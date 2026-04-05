package io.github.nanmazino.chatrebuild.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoom;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMember;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMemberStatus;
import io.github.nanmazino.chatrebuild.chat.repository.ChatMessageRepository;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomMemberRepository;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomRepository;
import io.github.nanmazino.chatrebuild.global.security.JwtTokenProvider;
import io.github.nanmazino.chatrebuild.post.entity.Post;
import io.github.nanmazino.chatrebuild.post.entity.PostStatus;
import io.github.nanmazino.chatrebuild.post.repository.PostRepository;
import io.github.nanmazino.chatrebuild.support.IntegrationTestSupport;
import io.github.nanmazino.chatrebuild.user.entity.User;
import io.github.nanmazino.chatrebuild.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketSecurityIntegrationTest extends IntegrationTestSupport {

    private static final long FRAME_TIMEOUT_SECONDS = 5;
    private static final long NO_FRAME_TIMEOUT_MILLIS = 700;

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<WebSocketSession> sessions = new ArrayList<>();

    private User author;
    private User activeMember;
    private User leftMember;
    private User outsider;

    private ChatRoom openRoom;
    private ChatRoom closedRoom;
    private ChatRoom deletedRoom;

    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAllInBatch();
        chatRoomMemberRepository.deleteAllInBatch();
        chatRoomRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        author = saveUser("author@example.com", "author");
        activeMember = saveUser("active@example.com", "active");
        leftMember = saveUser("left@example.com", "left");
        outsider = saveUser("outsider@example.com", "outsider");

        openRoom = createRoom("open-room", PostStatus.OPEN);
        addMember(openRoom, activeMember, ChatRoomMemberStatus.ACTIVE);
        addMember(openRoom, leftMember, ChatRoomMemberStatus.LEFT);

        closedRoom = createRoom("closed-room", PostStatus.CLOSED);
        addMember(closedRoom, activeMember, ChatRoomMemberStatus.ACTIVE);

        deletedRoom = createRoom("deleted-room", PostStatus.DELETED);
        addMember(deletedRoom, activeMember, ChatRoomMemberStatus.ACTIVE);
    }

    @AfterEach
    void tearDown() throws Exception {
        for (WebSocketSession session : sessions) {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    @Test
    @DisplayName("Authorization 헤더 없이 CONNECT 하면 AUTH_UNAUTHORIZED 에러를 반환한다")
    void connectFailsWithoutAuthorizationHeader() throws Exception {
        RawStompConnection connection = openConnection();
        connection.send(connectFrame(null));

        String frame = connection.awaitFrame();
        JsonNode error = parseErrorBody(frame);

        assertThat(frameCommand(frame)).isEqualTo("ERROR");
        assertThat(error.path("error").path("code").asText()).isEqualTo("AUTH_UNAUTHORIZED");
        assertThat(error.path("error").path("message").asText()).isEqualTo("인증이 필요합니다.");
    }

    @Test
    @DisplayName("유효하지 않은 토큰으로 CONNECT 하면 AUTH_UNAUTHORIZED 에러를 반환한다")
    void connectFailsWithInvalidToken() throws Exception {
        RawStompConnection connection = openConnection();
        connection.send(connectFrame("invalid-token"));

        String frame = connection.awaitFrame();
        JsonNode error = parseErrorBody(frame);

        assertThat(frameCommand(frame)).isEqualTo("ERROR");
        assertThat(error.path("error").path("code").asText()).isEqualTo("AUTH_UNAUTHORIZED");
    }

    @Test
    @DisplayName("유효한 토큰으로 CONNECT 하면 세션이 연결된다")
    void connectSucceedsWithValidToken() throws Exception {
        RawStompConnection connection = connect(accessToken(activeMember));

        assertThat(connection.connectedFrame()).startsWith("CONNECTED");
        connection.assertNoFrame();
    }

    @Test
    @DisplayName("ACTIVE 멤버는 채팅방 destination 구독에 성공한다")
    void activeMemberCanSubscribe() throws Exception {
        RawStompConnection connection = connect(accessToken(activeMember));
        connection.send(subscribeFrame(openRoom.getId()));

        connection.assertNoFrame();
    }

    @Test
    @DisplayName("비멤버는 채팅방 구독 시 AUTH_FORBIDDEN 에러를 반환한다")
    void nonMemberCannotSubscribe() throws Exception {
        RawStompConnection connection = connect(accessToken(outsider));
        connection.send(subscribeFrame(openRoom.getId()));

        String frame = connection.awaitFrame();
        JsonNode error = parseErrorBody(frame);

        assertThat(frameCommand(frame)).isEqualTo("ERROR");
        assertThat(error.path("error").path("code").asText()).isEqualTo("AUTH_FORBIDDEN");
        assertThat(error.path("error").path("message").asText()).isEqualTo("접근 권한이 없습니다.");
    }

    @Test
    @DisplayName("LEFT 멤버는 채팅방 구독 시 AUTH_FORBIDDEN 에러를 반환한다")
    void leftMemberCannotSubscribe() throws Exception {
        RawStompConnection connection = connect(accessToken(leftMember));
        connection.send(subscribeFrame(openRoom.getId()));

        String frame = connection.awaitFrame();
        JsonNode error = parseErrorBody(frame);

        assertThat(frameCommand(frame)).isEqualTo("ERROR");
        assertThat(error.path("error").path("code").asText()).isEqualTo("AUTH_FORBIDDEN");
    }

    @Test
    @DisplayName("ACTIVE 멤버는 채팅방 SEND destination 권한 검사를 통과한다")
    void activeMemberCanSend() throws Exception {
        RawStompConnection connection = connect(accessToken(activeMember));
        connection.send(sendFrame(openRoom.getId()));

        connection.assertNoFrame();
    }

    @Test
    @DisplayName("비멤버는 채팅방 SEND 시 AUTH_FORBIDDEN 에러를 반환한다")
    void nonMemberCannotSend() throws Exception {
        RawStompConnection connection = connect(accessToken(outsider));
        connection.send(sendFrame(openRoom.getId()));

        String frame = connection.awaitFrame();
        JsonNode error = parseErrorBody(frame);

        assertThat(frameCommand(frame)).isEqualTo("ERROR");
        assertThat(error.path("error").path("code").asText()).isEqualTo("AUTH_FORBIDDEN");
        assertThat(chatMessageRepository.countByRoomId(openRoom.getId())).isZero();
    }

    @Test
    @DisplayName("LEFT 멤버는 채팅방 SEND 시 AUTH_FORBIDDEN 에러를 반환한다")
    void leftMemberCannotSend() throws Exception {
        RawStompConnection connection = connect(accessToken(leftMember));
        connection.send(sendFrame(openRoom.getId()));

        String frame = connection.awaitFrame();
        JsonNode error = parseErrorBody(frame);

        assertThat(frameCommand(frame)).isEqualTo("ERROR");
        assertThat(error.path("error").path("code").asText()).isEqualTo("AUTH_FORBIDDEN");
        assertThat(chatMessageRepository.countByRoomId(openRoom.getId())).isZero();
    }

    @Test
    @DisplayName("CLOSED 게시글의 ACTIVE 멤버는 구독 권한을 유지한다")
    void closedPostActiveMemberCanStillSubscribe() throws Exception {
        RawStompConnection connection = connect(accessToken(activeMember));
        connection.send(subscribeFrame(closedRoom.getId()));

        connection.assertNoFrame();
    }

    @Test
    @DisplayName("DELETED 게시글의 ACTIVE 멤버는 구독 권한을 유지한다")
    void deletedPostActiveMemberCanStillSubscribe() throws Exception {
        RawStompConnection connection = connect(accessToken(activeMember));
        connection.send(subscribeFrame(deletedRoom.getId()));

        connection.assertNoFrame();
    }

    @Test
    @DisplayName("CLOSED 게시글의 ACTIVE 멤버는 SEND 권한을 유지한다")
    void closedPostActiveMemberCanStillSend() throws Exception {
        RawStompConnection connection = connect(accessToken(activeMember));
        connection.send(sendFrame(closedRoom.getId()));

        connection.assertNoFrame();
    }

    @Test
    @DisplayName("DELETED 게시글의 ACTIVE 멤버는 SEND 권한을 유지한다")
    void deletedPostActiveMemberCanStillSend() throws Exception {
        RawStompConnection connection = connect(accessToken(activeMember));
        connection.send(sendFrame(deletedRoom.getId()));

        connection.assertNoFrame();
    }

    @Test
    @DisplayName("CONNECT 없이 SUBSCRIBE 하면 AUTH_UNAUTHORIZED 에러를 반환한다")
    void subscribeFailsWithoutAuthenticatedConnect() throws Exception {
        RawStompConnection connection = openConnection();
        connection.send(subscribeFrame(openRoom.getId()));

        String frame = connection.awaitFrame();
        JsonNode error = parseErrorBody(frame);

        assertThat(frameCommand(frame)).isEqualTo("ERROR");
        assertThat(error.path("error").path("code").asText()).isEqualTo("AUTH_UNAUTHORIZED");
        assertThat(error.path("error").path("message").asText()).isEqualTo("인증이 필요합니다.");
    }

    @Test
    @DisplayName("CONNECT 없이 SEND 하면 AUTH_UNAUTHORIZED 에러를 반환한다")
    void sendFailsWithoutAuthenticatedConnect() throws Exception {
        RawStompConnection connection = openConnection();
        connection.send(sendFrame(openRoom.getId()));

        String frame = connection.awaitFrame();
        JsonNode error = parseErrorBody(frame);

        assertThat(frameCommand(frame)).isEqualTo("ERROR");
        assertThat(error.path("error").path("code").asText()).isEqualTo("AUTH_UNAUTHORIZED");
        assertThat(error.path("error").path("message").asText()).isEqualTo("인증이 필요합니다.");
    }

    private RawStompConnection connect(String token) throws Exception {
        RawStompConnection connection = openConnection();
        connection.send(connectFrame(token));
        return connection.withConnectedFrame(connection.awaitFrame());
    }

    private RawStompConnection openConnection() throws Exception {
        FrameCollectingWebSocketHandler handler = new FrameCollectingWebSocketHandler();
        WebSocketSession session = new StandardWebSocketClient()
            .execute(handler, "ws://127.0.0.1:" + port + "/ws-stomp")
            .get(FRAME_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        sessions.add(session);
        return new RawStompConnection(session, handler);
    }

    private String connectFrame(String token) {
        String authorizationHeader = token == null ? "" : "Authorization:Bearer " + token + "\n";

        return "CONNECT\n"
            + "accept-version:1.2\n"
            + "host:127.0.0.1\n"
            + authorizationHeader
            + "\n"
            + "\u0000";
    }

    private String subscribeFrame(Long roomId) {
        return "SUBSCRIBE\n"
            + "id:sub-" + roomId + "\n"
            + "destination:/sub/chat-rooms/" + roomId + "\n"
            + "\n"
            + "\u0000";
    }

    private String sendFrame(Long roomId) {
        String body = "{\"content\":\"hello\",\"type\":\"TEXT\"}";
        int contentLength = body.getBytes(StandardCharsets.UTF_8).length;

        return "SEND\n"
            + "destination:/pub/chat-rooms/" + roomId + "/messages\n"
            + "content-type:application/json\n"
            + "content-length:" + contentLength + "\n"
            + "\n"
            + body
            + "\u0000";
    }

    private String accessToken(User user) {
        return jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
    }

    private User saveUser(String email, String nickname) {
        return userRepository.save(new User(
            email,
            passwordEncoder.encode("Password123!"),
            nickname
        ));
    }

    private ChatRoom createRoom(String title, PostStatus status) {
        Post post = postRepository.save(new Post(author, title, title + "-content", 4, status));
        return chatRoomRepository.save(new ChatRoom(post));
    }

    private void addMember(ChatRoom room, User user, ChatRoomMemberStatus status) {
        chatRoomMemberRepository.save(new ChatRoomMember(
            room,
            user,
            status,
            LocalDateTime.now()
        ));

        if (status == ChatRoomMemberStatus.ACTIVE) {
            room.increaseMemberCount();
            chatRoomRepository.saveAndFlush(room);
        }
    }

    private String frameCommand(String frame) {
        return normalizeFrame(frame).split("\n", 2)[0];
    }

    private JsonNode parseErrorBody(String frame) throws Exception {
        return objectMapper.readTree(frameBody(frame));
    }

    private String frameBody(String frame) {
        String normalized = normalizeFrame(frame);
        int bodyStart = normalized.indexOf("\n\n");

        if (bodyStart < 0) {
            return "";
        }

        return normalized.substring(bodyStart + 2).replace("\u0000", "");
    }

    private String normalizeFrame(String frame) {
        return frame.replace("\r", "");
    }

    private static final class RawStompConnection {

        private final WebSocketSession session;
        private final FrameCollectingWebSocketHandler handler;
        private String connectedFrame;

        private RawStompConnection(WebSocketSession session, FrameCollectingWebSocketHandler handler) {
            this.session = session;
            this.handler = handler;
        }

        private RawStompConnection withConnectedFrame(String connectedFrame) {
            this.connectedFrame = connectedFrame;
            return this;
        }

        private String connectedFrame() {
            return connectedFrame;
        }

        private void send(String frame) throws Exception {
            session.sendMessage(new TextMessage(frame));
        }

        private String awaitFrame() throws Exception {
            String frame = handler.frames.poll(FRAME_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (frame == null) {
                throw new AssertionError("Expected a STOMP frame but none was received.");
            }

            return frame;
        }

        private void assertNoFrame() throws Exception {
            String frame = handler.frames.poll(NO_FRAME_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

            if (frame != null) {
                throw new AssertionError("Unexpected STOMP frame received: " + frame);
            }
        }
    }

    private static final class FrameCollectingWebSocketHandler extends TextWebSocketHandler {

        private final LinkedBlockingQueue<String> frames = new LinkedBlockingQueue<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            frames.offer(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            if (status.getCode() != CloseStatus.NORMAL.getCode()) {
                frames.offer("CLOSED\n\n" + status + "\u0000");
            }
        }
    }
}

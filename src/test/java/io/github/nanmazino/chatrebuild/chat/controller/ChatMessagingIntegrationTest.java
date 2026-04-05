package io.github.nanmazino.chatrebuild.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nanmazino.chatrebuild.chat.entity.ChatMessage;
import io.github.nanmazino.chatrebuild.chat.entity.ChatMessageType;
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
class ChatMessagingIntegrationTest extends IntegrationTestSupport {

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
    private User subscriber;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAllInBatch();
        chatRoomMemberRepository.deleteAllInBatch();
        chatRoomRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        author = saveUser("author@example.com", "author");
        subscriber = saveUser("subscriber@example.com", "subscriber");

        room = createRoom("chat-room", PostStatus.OPEN);
        addActiveMember(room, author);
        addActiveMember(room, subscriber);
    }

    @AfterEach
    void tearDown() throws Exception {
        for (WebSocketSession session : sessions) {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }

        chatMessageRepository.deleteAllInBatch();
        chatRoomMemberRepository.deleteAllInBatch();
        chatRoomRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("메시지를 보내면 저장되고 같은 인스턴스 구독자에게 principal sender 기준으로 브로드캐스트된다")
    void sendMessageBroadcastsAndPersistsUsingPrincipalSender() throws Exception {
        RawStompConnection subscriberConnection = connect(accessToken(subscriber));
        subscriberConnection.send(subscribeFrame(room.getId()));
        subscriberConnection.assertNoFrame();

        RawStompConnection senderConnection = connect(accessToken(author));
        senderConnection.send(sendFrame(room.getId(), """
            {
              "content": "오늘 7시로 확정할게요",
              "type": "TEXT",
              "senderId": 999,
              "nickname": "intruder",
              "sender": {
                "userId": 999,
                "nickname": "intruder"
              }
            }
            """));

        String frame = subscriberConnection.awaitFrame();
        JsonNode body = parseBody(frame);
        ChatMessage savedMessage = chatMessageRepository.findAll().stream()
            .findFirst()
            .orElseThrow(() -> new AssertionError("저장된 메시지가 있어야 합니다."));

        assertThat(frameCommand(frame)).isEqualTo("MESSAGE");
        assertThat(body.path("roomId").asLong()).isEqualTo(room.getId());
        assertThat(body.path("sender").path("userId").asLong()).isEqualTo(author.getId());
        assertThat(body.path("sender").path("nickname").asText()).isEqualTo(author.getNickname());
        assertThat(body.path("content").asText()).isEqualTo("오늘 7시로 확정할게요");
        assertThat(body.path("type").asText()).isEqualTo(ChatMessageType.TEXT.name());
        assertThat(body.path("messageId").asLong()).isPositive();
        assertThat(body.path("createdAt").asText()).isNotBlank();
        assertThat(chatMessageRepository.countByRoomId(room.getId())).isEqualTo(1);
        assertThat(savedMessage.getRoom().getId()).isEqualTo(room.getId());
        assertThat(savedMessage.getSender().getId()).isEqualTo(author.getId());
        assertThat(savedMessage.getContent()).isEqualTo("오늘 7시로 확정할게요");
        assertThat(savedMessage.getType()).isEqualTo(ChatMessageType.TEXT);
    }

    @Test
    @DisplayName("SYSTEM 타입 메시지 송신은 COMMON_VALIDATION_ERROR로 차단된다")
    void sendMessageRejectsSystemType() throws Exception {
        RawStompConnection connection = connect(accessToken(author));
        connection.send(sendFrame(room.getId(), """
            {
              "content": "시스템처럼 보내기",
              "type": "SYSTEM"
            }
            """));

        String frame = connection.awaitFrame();
        JsonNode error = parseBody(frame);

        assertThat(frameCommand(frame)).isEqualTo("ERROR");
        assertThat(error.path("error").path("code").asText()).isEqualTo("COMMON_VALIDATION_ERROR");
        assertThat(error.path("error").path("message").asText()).isEqualTo("메시지 타입은 TEXT만 보낼 수 있습니다.");
        assertThat(chatMessageRepository.countByRoomId(room.getId())).isZero();
    }

    @Test
    @DisplayName("빈 메시지 내용은 COMMON_VALIDATION_ERROR로 차단된다")
    void sendMessageRejectsBlankContent() throws Exception {
        RawStompConnection connection = connect(accessToken(author));
        connection.send(sendFrame(room.getId(), """
            {
              "content": "   ",
              "type": "TEXT"
            }
            """));

        String frame = connection.awaitFrame();
        JsonNode error = parseBody(frame);

        assertThat(frameCommand(frame)).isEqualTo("ERROR");
        assertThat(error.path("error").path("code").asText()).isEqualTo("COMMON_VALIDATION_ERROR");
        assertThat(error.path("error").path("message").asText()).isEqualTo("메시지 내용은 필수입니다.");
        assertThat(chatMessageRepository.countByRoomId(room.getId())).isZero();
    }

    @Test
    @DisplayName("메시지 타입이 없으면 COMMON_VALIDATION_ERROR로 차단된다")
    void sendMessageRejectsMissingType() throws Exception {
        RawStompConnection connection = connect(accessToken(author));
        connection.send(sendFrame(room.getId(), """
            {
              "content": "타입 없음"
            }
            """));

        String frame = connection.awaitFrame();
        JsonNode error = parseBody(frame);

        assertThat(frameCommand(frame)).isEqualTo("ERROR");
        assertThat(error.path("error").path("code").asText()).isEqualTo("COMMON_VALIDATION_ERROR");
        assertThat(error.path("error").path("message").asText()).isEqualTo("메시지 타입은 필수입니다.");
        assertThat(chatMessageRepository.countByRoomId(room.getId())).isZero();
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
        return "CONNECT\n"
            + "accept-version:1.2\n"
            + "host:127.0.0.1\n"
            + "Authorization:Bearer " + token + "\n"
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

    private String sendFrame(Long roomId, String body) {
        String normalizedBody = body.strip();
        int contentLength = normalizedBody.getBytes(StandardCharsets.UTF_8).length;

        return "SEND\n"
            + "destination:/pub/chat-rooms/" + roomId + "/messages\n"
            + "content-type:application/json\n"
            + "content-length:" + contentLength + "\n"
            + "\n"
            + normalizedBody
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

    private void addActiveMember(ChatRoom chatRoom, User user) {
        chatRoomMemberRepository.save(new ChatRoomMember(
            chatRoom,
            user,
            ChatRoomMemberStatus.ACTIVE,
            LocalDateTime.now()
        ));
        chatRoom.increaseMemberCount();
        chatRoomRepository.saveAndFlush(chatRoom);
    }

    private String frameCommand(String frame) {
        return normalizeFrame(frame).split("\n", 2)[0];
    }

    private JsonNode parseBody(String frame) throws Exception {
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

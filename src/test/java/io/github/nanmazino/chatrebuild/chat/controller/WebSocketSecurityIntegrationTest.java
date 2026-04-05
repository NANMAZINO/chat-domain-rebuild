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
import io.github.nanmazino.chatrebuild.support.RawStompTestClient;
import io.github.nanmazino.chatrebuild.user.entity.User;
import io.github.nanmazino.chatrebuild.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketSecurityIntegrationTest extends IntegrationTestSupport {

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

    private User author;
    private User activeMember;
    private User leftMember;
    private User outsider;

    private ChatRoom openRoom;
    private ChatRoom closedRoom;
    private ChatRoom deletedRoom;
    private RawStompTestClient stompClient;

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

        stompClient = new RawStompTestClient(port);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (stompClient != null) {
            stompClient.close();
        }
    }

    @Test
    @DisplayName("Authorization 헤더 없이 CONNECT 하면 AUTH_UNAUTHORIZED 에러를 반환한다")
    void connectFailsWithoutAuthorizationHeader() throws Exception {
        RawStompTestClient.Connection connection = stompClient.openConnection();
        connection.send(RawStompTestClient.connectFrame(null));

        String frame = connection.awaitFrame();
        JsonNode error = RawStompTestClient.parseBody(objectMapper, frame);

        assertThat(RawStompTestClient.frameCommand(frame)).isEqualTo("ERROR");
        assertThat(error.path("error").path("code").asText()).isEqualTo("AUTH_UNAUTHORIZED");
        assertThat(error.path("error").path("message").asText()).isEqualTo("인증이 필요합니다.");
    }

    @Test
    @DisplayName("유효하지 않은 토큰으로 CONNECT 하면 AUTH_UNAUTHORIZED 에러를 반환한다")
    void connectFailsWithInvalidToken() throws Exception {
        RawStompTestClient.Connection connection = stompClient.openConnection();
        connection.send(RawStompTestClient.connectFrame("invalid-token"));

        String frame = connection.awaitFrame();
        JsonNode error = RawStompTestClient.parseBody(objectMapper, frame);

        assertThat(RawStompTestClient.frameCommand(frame)).isEqualTo("ERROR");
        assertThat(error.path("error").path("code").asText()).isEqualTo("AUTH_UNAUTHORIZED");
    }

    @Test
    @DisplayName("유효한 토큰으로 CONNECT 하면 세션이 연결된다")
    void connectSucceedsWithValidToken() throws Exception {
        RawStompTestClient.Connection connection = stompClient.connect(accessToken(activeMember));

        assertThat(connection.connectedFrame()).startsWith("CONNECTED");
        connection.assertNoFrame();
    }

    @Test
    @DisplayName("ACTIVE 멤버는 채팅방 destination 구독에 성공한다")
    void activeMemberCanSubscribe() throws Exception {
        RawStompTestClient.Connection connection = stompClient.connect(accessToken(activeMember));
        connection.send(RawStompTestClient.subscribeFrame(openRoom.getId()));

        connection.assertNoFrame();
    }

    @Test
    @DisplayName("비멤버는 채팅방 구독 시 AUTH_FORBIDDEN 에러를 반환한다")
    void nonMemberCannotSubscribe() throws Exception {
        RawStompTestClient.Connection connection = stompClient.connect(accessToken(outsider));
        connection.send(RawStompTestClient.subscribeFrame(openRoom.getId()));

        String frame = connection.awaitFrame();
        JsonNode error = RawStompTestClient.parseBody(objectMapper, frame);

        assertThat(RawStompTestClient.frameCommand(frame)).isEqualTo("ERROR");
        assertThat(error.path("error").path("code").asText()).isEqualTo("AUTH_FORBIDDEN");
        assertThat(error.path("error").path("message").asText()).isEqualTo("접근 권한이 없습니다.");
    }

    @Test
    @DisplayName("LEFT 멤버는 채팅방 구독 시 AUTH_FORBIDDEN 에러를 반환한다")
    void leftMemberCannotSubscribe() throws Exception {
        RawStompTestClient.Connection connection = stompClient.connect(accessToken(leftMember));
        connection.send(RawStompTestClient.subscribeFrame(openRoom.getId()));

        String frame = connection.awaitFrame();
        JsonNode error = RawStompTestClient.parseBody(objectMapper, frame);

        assertThat(RawStompTestClient.frameCommand(frame)).isEqualTo("ERROR");
        assertThat(error.path("error").path("code").asText()).isEqualTo("AUTH_FORBIDDEN");
    }

    @Test
    @DisplayName("ACTIVE 멤버는 채팅방 SEND destination 권한 검사를 통과한다")
    void activeMemberCanSend() throws Exception {
        RawStompTestClient.Connection connection = stompClient.connect(accessToken(activeMember));
        connection.send(RawStompTestClient.sendFrame(openRoom.getId(), "{\"content\":\"hello\",\"type\":\"TEXT\"}"));

        connection.assertNoFrame();
    }

    @Test
    @DisplayName("비멤버는 채팅방 SEND 시 AUTH_FORBIDDEN 에러를 반환한다")
    void nonMemberCannotSend() throws Exception {
        RawStompTestClient.Connection connection = stompClient.connect(accessToken(outsider));
        connection.send(RawStompTestClient.sendFrame(openRoom.getId(), "{\"content\":\"hello\",\"type\":\"TEXT\"}"));

        String frame = connection.awaitFrame();
        JsonNode error = RawStompTestClient.parseBody(objectMapper, frame);

        assertThat(RawStompTestClient.frameCommand(frame)).isEqualTo("ERROR");
        assertThat(error.path("error").path("code").asText()).isEqualTo("AUTH_FORBIDDEN");
        assertThat(chatMessageRepository.countByRoomId(openRoom.getId())).isZero();
    }

    @Test
    @DisplayName("LEFT 멤버는 채팅방 SEND 시 AUTH_FORBIDDEN 에러를 반환한다")
    void leftMemberCannotSend() throws Exception {
        RawStompTestClient.Connection connection = stompClient.connect(accessToken(leftMember));
        connection.send(RawStompTestClient.sendFrame(openRoom.getId(), "{\"content\":\"hello\",\"type\":\"TEXT\"}"));

        String frame = connection.awaitFrame();
        JsonNode error = RawStompTestClient.parseBody(objectMapper, frame);

        assertThat(RawStompTestClient.frameCommand(frame)).isEqualTo("ERROR");
        assertThat(error.path("error").path("code").asText()).isEqualTo("AUTH_FORBIDDEN");
        assertThat(chatMessageRepository.countByRoomId(openRoom.getId())).isZero();
    }

    @Test
    @DisplayName("CLOSED 게시글의 ACTIVE 멤버는 구독 권한을 유지한다")
    void closedPostActiveMemberCanStillSubscribe() throws Exception {
        RawStompTestClient.Connection connection = stompClient.connect(accessToken(activeMember));
        connection.send(RawStompTestClient.subscribeFrame(closedRoom.getId()));

        connection.assertNoFrame();
    }

    @Test
    @DisplayName("DELETED 게시글의 ACTIVE 멤버는 구독 권한을 유지한다")
    void deletedPostActiveMemberCanStillSubscribe() throws Exception {
        RawStompTestClient.Connection connection = stompClient.connect(accessToken(activeMember));
        connection.send(RawStompTestClient.subscribeFrame(deletedRoom.getId()));

        connection.assertNoFrame();
    }

    @Test
    @DisplayName("CLOSED 게시글의 ACTIVE 멤버는 SEND 권한을 유지한다")
    void closedPostActiveMemberCanStillSend() throws Exception {
        RawStompTestClient.Connection connection = stompClient.connect(accessToken(activeMember));
        connection.send(RawStompTestClient.sendFrame(closedRoom.getId(), "{\"content\":\"hello\",\"type\":\"TEXT\"}"));

        connection.assertNoFrame();
    }

    @Test
    @DisplayName("DELETED 게시글의 ACTIVE 멤버는 SEND 권한을 유지한다")
    void deletedPostActiveMemberCanStillSend() throws Exception {
        RawStompTestClient.Connection connection = stompClient.connect(accessToken(activeMember));
        connection.send(RawStompTestClient.sendFrame(deletedRoom.getId(), "{\"content\":\"hello\",\"type\":\"TEXT\"}"));

        connection.assertNoFrame();
    }

    @Test
    @DisplayName("CONNECT 없이 SUBSCRIBE 하면 AUTH_UNAUTHORIZED 에러를 반환한다")
    void subscribeFailsWithoutAuthenticatedConnect() throws Exception {
        RawStompTestClient.Connection connection = stompClient.openConnection();
        connection.send(RawStompTestClient.subscribeFrame(openRoom.getId()));

        String frame = connection.awaitFrame();
        JsonNode error = RawStompTestClient.parseBody(objectMapper, frame);

        assertThat(RawStompTestClient.frameCommand(frame)).isEqualTo("ERROR");
        assertThat(error.path("error").path("code").asText()).isEqualTo("AUTH_UNAUTHORIZED");
        assertThat(error.path("error").path("message").asText()).isEqualTo("인증이 필요합니다.");
    }

    @Test
    @DisplayName("CONNECT 없이 SEND 하면 AUTH_UNAUTHORIZED 에러를 반환한다")
    void sendFailsWithoutAuthenticatedConnect() throws Exception {
        RawStompTestClient.Connection connection = stompClient.openConnection();
        connection.send(RawStompTestClient.sendFrame(openRoom.getId(), "{\"content\":\"hello\",\"type\":\"TEXT\"}"));

        String frame = connection.awaitFrame();
        JsonNode error = RawStompTestClient.parseBody(objectMapper, frame);

        assertThat(RawStompTestClient.frameCommand(frame)).isEqualTo("ERROR");
        assertThat(error.path("error").path("code").asText()).isEqualTo("AUTH_UNAUTHORIZED");
        assertThat(error.path("error").path("message").asText()).isEqualTo("인증이 필요합니다.");
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
}

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
class ChatMessagingIntegrationTest extends IntegrationTestSupport {

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
    private User subscriber;
    private ChatRoom room;
    private RawStompTestClient stompClient;

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
        stompClient = new RawStompTestClient(port);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (stompClient != null) {
            stompClient.close();
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
        RawStompTestClient.Connection subscriberConnection = stompClient.connect(accessToken(subscriber));
        subscriberConnection.send(RawStompTestClient.subscribeFrame(room.getId()));
        subscriberConnection.assertNoFrame();

        RawStompTestClient.Connection senderConnection = stompClient.connect(accessToken(author));
        senderConnection.send(RawStompTestClient.sendFrame(room.getId(), """
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
        JsonNode body = RawStompTestClient.parseBody(objectMapper, frame);
        ChatMessage savedMessage = chatMessageRepository.findAll().stream()
            .findFirst()
            .orElseThrow(() -> new AssertionError("저장된 메시지가 있어야 합니다."));

        assertThat(RawStompTestClient.frameCommand(frame)).isEqualTo("MESSAGE");
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
        RawStompTestClient.Connection connection = stompClient.connect(accessToken(author));
        connection.send(RawStompTestClient.sendFrame(room.getId(), """
            {
              "content": "시스템처럼 보내기",
              "type": "SYSTEM"
            }
            """));

        String frame = connection.awaitFrame();
        JsonNode error = RawStompTestClient.parseBody(objectMapper, frame);

        assertThat(RawStompTestClient.frameCommand(frame)).isEqualTo("ERROR");
        assertThat(error.path("error").path("code").asText()).isEqualTo("COMMON_VALIDATION_ERROR");
        assertThat(error.path("error").path("message").asText()).isEqualTo("메시지 타입은 TEXT만 보낼 수 있습니다.");
        assertThat(chatMessageRepository.countByRoomId(room.getId())).isZero();
    }

    @Test
    @DisplayName("빈 메시지 내용은 COMMON_VALIDATION_ERROR로 차단된다")
    void sendMessageRejectsBlankContent() throws Exception {
        RawStompTestClient.Connection connection = stompClient.connect(accessToken(author));
        connection.send(RawStompTestClient.sendFrame(room.getId(), """
            {
              "content": "   ",
              "type": "TEXT"
            }
            """));

        String frame = connection.awaitFrame();
        JsonNode error = RawStompTestClient.parseBody(objectMapper, frame);

        assertThat(RawStompTestClient.frameCommand(frame)).isEqualTo("ERROR");
        assertThat(error.path("error").path("code").asText()).isEqualTo("COMMON_VALIDATION_ERROR");
        assertThat(error.path("error").path("message").asText()).isEqualTo("메시지 내용은 필수입니다.");
        assertThat(chatMessageRepository.countByRoomId(room.getId())).isZero();
    }

    @Test
    @DisplayName("메시지 타입이 없으면 COMMON_VALIDATION_ERROR로 차단된다")
    void sendMessageRejectsMissingType() throws Exception {
        RawStompTestClient.Connection connection = stompClient.connect(accessToken(author));
        connection.send(RawStompTestClient.sendFrame(room.getId(), """
            {
              "content": "타입 없음"
            }
            """));

        String frame = connection.awaitFrame();
        JsonNode error = RawStompTestClient.parseBody(objectMapper, frame);

        assertThat(RawStompTestClient.frameCommand(frame)).isEqualTo("ERROR");
        assertThat(error.path("error").path("code").asText()).isEqualTo("COMMON_VALIDATION_ERROR");
        assertThat(error.path("error").path("message").asText()).isEqualTo("메시지 타입은 필수입니다.");
        assertThat(chatMessageRepository.countByRoomId(room.getId())).isZero();
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
}

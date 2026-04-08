package io.github.nanmazino.chatrebuild.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nanmazino.chatrebuild.ChatRebuildApplication;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrossInstancePubSubScenarioTest extends IntegrationTestSupport {

    @LocalServerPort
    private int primaryPort;

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

    private ConfigurableApplicationContext secondaryContext;
    private int secondaryPort;
    private RawStompTestClient primaryStompClient;
    private RawStompTestClient secondaryStompClient;
    private User author;
    private User member;
    private ChatRoom room;

    @BeforeAll
    void startSecondaryInstance() {
        secondaryContext = new SpringApplicationBuilder(ChatRebuildApplication.class)
            .profiles("test")
            .properties(
                "server.port=0",
                "spring.datasource.url=" + MYSQL_CONTAINER.getJdbcUrl(),
                "spring.datasource.username=" + MYSQL_CONTAINER.getUsername(),
                "spring.datasource.password=" + MYSQL_CONTAINER.getPassword(),
                "spring.datasource.driver-class-name=" + MYSQL_CONTAINER.getDriverClassName(),
                "spring.data.redis.host=" + REDIS_CONTAINER.getHost(),
                "spring.data.redis.port=" + REDIS_CONTAINER.getMappedPort(6379),
                "spring.jpa.hibernate.ddl-auto=none"
            )
            .run();
        secondaryPort = ((WebServerApplicationContext) secondaryContext).getWebServer().getPort();
    }

    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAllInBatch();
        chatRoomMemberRepository.deleteAllInBatch();
        chatRoomRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        author = saveUser("cross-author@test.com", "cross-author");
        member = saveUser("cross-member@test.com", "cross-member");
        room = createRoom("cross-room", PostStatus.OPEN);
        addActiveMember(room, author);
        addActiveMember(room, member);

        primaryStompClient = new RawStompTestClient(primaryPort);
        secondaryStompClient = new RawStompTestClient(secondaryPort);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (primaryStompClient != null) {
            primaryStompClient.close();
        }

        if (secondaryStompClient != null) {
            secondaryStompClient.close();
        }

        chatMessageRepository.deleteAllInBatch();
        chatRoomMemberRepository.deleteAllInBatch();
        chatRoomRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @AfterAll
    void closeSecondaryInstance() {
        if (secondaryContext != null) {
            secondaryContext.close();
        }
    }

    @Test
    @DisplayName("A 인스턴스에서 보낸 메시지는 B 인스턴스 구독자에게 전달된다")
    void messageSentFromPrimaryInstanceIsDeliveredToSecondarySubscriber() throws Exception {
        RawStompTestClient.Connection subscriberConnection = secondaryStompClient.connect(accessToken(member));
        subscriberConnection.send(RawStompTestClient.subscribeFrame(room.getId()));
        subscriberConnection.assertNoFrame();

        RawStompTestClient.Connection senderConnection = primaryStompClient.connect(accessToken(author));
        senderConnection.send(RawStompTestClient.sendFrame(room.getId(), """
            {
              "content": "A to B",
              "type": "TEXT"
            }
            """));

        String frame = subscriberConnection.awaitFrame();
        JsonNode body = RawStompTestClient.parseBody(objectMapper, frame);

        assertThat(RawStompTestClient.frameCommand(frame)).isEqualTo("MESSAGE");
        assertThat(body.path("roomId").asLong()).isEqualTo(room.getId());
        assertThat(body.path("sender").path("userId").asLong()).isEqualTo(author.getId());
        assertThat(body.path("sender").path("nickname").asText()).isEqualTo(author.getNickname());
        assertThat(body.path("content").asText()).isEqualTo("A to B");
        assertThat(body.path("type").asText()).isEqualTo("TEXT");
        assertThat(chatMessageRepository.countByRoomId(room.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("B 인스턴스에서 보낸 메시지는 A 인스턴스 구독자에게 전달된다")
    void messageSentFromSecondaryInstanceIsDeliveredToPrimarySubscriber() throws Exception {
        RawStompTestClient.Connection subscriberConnection = primaryStompClient.connect(accessToken(author));
        subscriberConnection.send(RawStompTestClient.subscribeFrame(room.getId()));
        subscriberConnection.assertNoFrame();

        RawStompTestClient.Connection senderConnection = secondaryStompClient.connect(accessToken(member));
        senderConnection.send(RawStompTestClient.sendFrame(room.getId(), """
            {
              "content": "B to A",
              "type": "TEXT"
            }
            """));

        String frame = subscriberConnection.awaitFrame();
        JsonNode body = RawStompTestClient.parseBody(objectMapper, frame);

        assertThat(RawStompTestClient.frameCommand(frame)).isEqualTo("MESSAGE");
        assertThat(body.path("roomId").asLong()).isEqualTo(room.getId());
        assertThat(body.path("sender").path("userId").asLong()).isEqualTo(member.getId());
        assertThat(body.path("sender").path("nickname").asText()).isEqualTo(member.getNickname());
        assertThat(body.path("content").asText()).isEqualTo("B to A");
        assertThat(body.path("type").asText()).isEqualTo("TEXT");
        assertThat(chatMessageRepository.countByRoomId(room.getId())).isEqualTo(1);
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

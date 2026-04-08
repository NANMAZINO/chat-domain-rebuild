package io.github.nanmazino.chatrebuild.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nanmazino.chatrebuild.chat.repository.ChatMessageRepository;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomMemberRepository;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomRepository;
import io.github.nanmazino.chatrebuild.post.repository.PostRepository;
import io.github.nanmazino.chatrebuild.support.IntegrationTestSupport;
import io.github.nanmazino.chatrebuild.support.RawStompTestClient;
import io.github.nanmazino.chatrebuild.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BaselineFlowScenarioTest extends IntegrationTestSupport {

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    private RawStompTestClient stompClient;

    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAllInBatch();
        chatRoomMemberRepository.deleteAllInBatch();
        chatRoomRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
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
    @DisplayName("회원가입부터 읽음 처리 후 unread 반영까지 baseline 주요 흐름이 동작한다")
    void baselineFlowWorksFromSignupToReadSync() throws Exception {
        signUp("author@example.com", "Password123!", "author");
        signUp("member@example.com", "Password123!", "member");

        String authorToken = login("author@example.com", "Password123!").path("data").path("accessToken").asText();
        String memberToken = login("member@example.com", "Password123!").path("data").path("accessToken").asText();

        JsonNode createPostResponse = createPost(authorToken, "강남역 저녁 같이 하실 분", "오늘 7시 가능하신 분 구합니다.");
        long postId = createPostResponse.path("data").path("postId").asLong();
        long roomId = createPostResponse.path("data").path("chatRoomId").asLong();

        JsonNode postDetailResponse = getAuthorized("/api/posts/" + postId, memberToken);
        assertThat(postDetailResponse.path("data").path("postId").asLong()).isEqualTo(postId);
        assertThat(postDetailResponse.path("data").path("chatRoomId").asLong()).isEqualTo(roomId);

        MvcResult joinResponse = perform(authorize(post("/api/posts/" + postId + "/join"), memberToken))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode joinBody = toJson(joinResponse);
        assertThat(joinBody.path("data").path("chatRoomId").asLong()).isEqualTo(roomId);
        assertThat(joinBody.path("data").path("memberStatus").asText()).isEqualTo("ACTIVE");

        RawStompTestClient.Connection subscriberConnection = stompClient.connect(memberToken);
        assertThat(subscriberConnection.connectedFrame()).startsWith("CONNECTED");
        subscriberConnection.send(RawStompTestClient.subscribeFrame(roomId));
        subscriberConnection.assertNoFrame();

        RawStompTestClient.Connection senderConnection = stompClient.connect(authorToken);
        assertThat(senderConnection.connectedFrame()).startsWith("CONNECTED");
        senderConnection.send(RawStompTestClient.sendFrame(roomId, """
            {
              "content": "오늘 7시로 확정할게요",
              "type": "TEXT"
            }
            """));

        String broadcastFrame = subscriberConnection.awaitFrame();
        JsonNode broadcastBody = RawStompTestClient.parseBody(objectMapper, broadcastFrame);
        long messageId = broadcastBody.path("messageId").asLong();
        String messageContent = broadcastBody.path("content").asText();

        assertThat(RawStompTestClient.frameCommand(broadcastFrame)).isEqualTo("MESSAGE");
        assertThat(broadcastBody.path("roomId").asLong()).isEqualTo(roomId);
        assertThat(broadcastBody.path("sender").path("nickname").asText()).isEqualTo("author");
        assertThat(messageContent).isEqualTo("오늘 7시로 확정할게요");

        JsonNode roomListResponse = getAuthorized("/api/chat-rooms", memberToken);
        JsonNode roomListItem = roomListResponse.path("data").path("items").get(0);

        assertThat(roomListResponse.path("data").path("items").size()).isEqualTo(1);
        assertThat(roomListItem.path("roomId").asLong()).isEqualTo(roomId);
        assertThat(roomListItem.path("postId").asLong()).isEqualTo(postId);
        assertThat(roomListItem.path("postTitle").asText()).isEqualTo("강남역 저녁 같이 하실 분");
        assertThat(roomListItem.path("lastMessageId").asLong()).isEqualTo(messageId);
        assertThat(roomListItem.path("lastMessagePreview").asText()).isEqualTo(messageContent);
        assertThat(roomListItem.path("lastReadMessageId").isNull()).isTrue();
        assertThat(roomListItem.path("unreadCount").asLong()).isEqualTo(1L);

        JsonNode roomSummaryResponse = getAuthorized("/api/chat-rooms/" + roomId, memberToken);
        assertThat(roomSummaryResponse.path("data").path("roomId").asLong()).isEqualTo(roomId);
        assertThat(roomSummaryResponse.path("data").path("postId").asLong()).isEqualTo(postId);
        assertThat(roomSummaryResponse.path("data").path("lastMessageId").asLong()).isEqualTo(messageId);
        assertThat(roomSummaryResponse.path("data").path("lastMessagePreview").asText()).isEqualTo(messageContent);

        JsonNode historyResponse = getAuthorized("/api/chat-rooms/" + roomId + "/messages", memberToken);
        JsonNode latestHistoryMessage = historyResponse.path("data").path("items").get(0);

        assertThat(historyResponse.path("data").path("items").size()).isEqualTo(1);
        assertThat(latestHistoryMessage.path("messageId").asLong()).isEqualTo(messageId);
        assertThat(latestHistoryMessage.path("content").asText()).isEqualTo(messageContent);
        assertThat(latestHistoryMessage.path("sender").path("nickname").asText()).isEqualTo("author");

        JsonNode readResponse = markAsRead(memberToken, roomId, messageId);
        assertThat(readResponse.path("data").path("roomId").asLong()).isEqualTo(roomId);
        assertThat(readResponse.path("data").path("lastReadMessageId").asLong()).isEqualTo(messageId);
        assertThat(readResponse.path("data").path("lastReadAt").asText()).isNotBlank();

        JsonNode refreshedRoomListResponse = getAuthorized("/api/chat-rooms", memberToken);
        JsonNode refreshedRoomListItem = refreshedRoomListResponse.path("data").path("items").get(0);

        assertThat(refreshedRoomListItem.path("roomId").asLong()).isEqualTo(roomId);
        assertThat(refreshedRoomListItem.path("lastReadMessageId").asLong()).isEqualTo(messageId);
        assertThat(refreshedRoomListItem.path("unreadCount").asLong()).isEqualTo(0L);
    }

    private void signUp(String email, String password, String nickname) throws Exception {
        perform(post("/api/users/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "email": "%s",
                  "password": "%s",
                  "nickname": "%s"
                }
                """.formatted(email, password, nickname)))
            .andExpect(status().isCreated());
    }

    private JsonNode login(String email, String password) throws Exception {
        MvcResult result = perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn();

        return toJson(result);
    }

    private JsonNode createPost(String accessToken, String title, String content) throws Exception {
        MvcResult result = perform(authorize(post("/api/posts"), accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "%s",
                      "content": "%s",
                      "maxParticipants": 4
                    }
                    """.formatted(title, content)))
            .andExpect(status().isCreated())
            .andReturn();

        return toJson(result);
    }

    private JsonNode markAsRead(String accessToken, long roomId, long lastReadMessageId) throws Exception {
        MvcResult result = perform(authorize(patch("/api/chat-rooms/" + roomId + "/read"), accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lastReadMessageId": %d
                    }
                    """.formatted(lastReadMessageId)))
            .andExpect(status().isOk())
            .andReturn();

        return toJson(result);
    }

    private JsonNode getAuthorized(String path, String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(authorize(get(path), accessToken))
            .andExpect(status().isOk())
            .andReturn();

        return toJson(result);
    }

    private MockHttpServletRequestBuilder authorize(MockHttpServletRequestBuilder builder, String accessToken) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    private JsonNode toJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(builder);
    }
}

package io.github.nanmazino.chatrebuild.chat.pubsub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatMessageResponse;
import io.github.nanmazino.chatrebuild.chat.entity.ChatMessageType;
import io.github.nanmazino.chatrebuild.chat.websocket.ChatWebSocketBroadcaster;
import io.github.nanmazino.chatrebuild.support.IntegrationTestSupport;
import io.github.nanmazino.chatrebuild.user.entity.User;
import io.github.nanmazino.chatrebuild.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class ChatPubSubServiceTest extends IntegrationTestSupport {

    @Autowired
    private ChatPubSubService chatPubSubService;

    @Autowired
    private ChatPubSubRollbackTestHelper rollbackTestHelper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @MockitoSpyBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoSpyBean
    private ChatWebSocketBroadcaster chatWebSocketBroadcaster;

    @BeforeEach
    void setUp() {
        clearInvocations(stringRedisTemplate, chatWebSocketBroadcaster);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("메시지 생성 이벤트 publish는 chat:events 채널에 문서 기준 payload로 발행한다")
    void publishMessageCreatedAfterCommitUsesFixedChannelAndPayload() throws Exception {
        ChatMessageCreatedEvent event = new ChatMessageCreatedEvent(
            ChatPubSubEventType.CHAT_MESSAGE_CREATED,
            3L,
            120L,
            7L,
            "오늘 7시로 확정할게요",
            ChatMessageType.TEXT,
            LocalDateTime.of(2026, 4, 8, 17, 0)
        );
        doReturn(1L).when(stringRedisTemplate).convertAndSend(anyString(), anyString());

        chatPubSubService.publishMessageCreatedAfterCommit(event);

        ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(stringRedisTemplate).convertAndSend(channelCaptor.capture(), payloadCaptor.capture());

        JsonNode payload = objectMapper.readTree(payloadCaptor.getValue());

        assertThat(channelCaptor.getValue()).isEqualTo(ChatPubSubService.CHANNEL_NAME);
        assertThat(payload.path("eventType").asText()).isEqualTo("CHAT_MESSAGE_CREATED");
        assertThat(payload.path("roomId").asLong()).isEqualTo(3L);
        assertThat(payload.path("messageId").asLong()).isEqualTo(120L);
        assertThat(payload.path("senderId").asLong()).isEqualTo(7L);
        assertThat(payload.path("content").asText()).isEqualTo("오늘 7시로 확정할게요");
        assertThat(payload.path("type").asText()).isEqualTo("TEXT");
        assertThat(payload.path("createdAt").asText()).isEqualTo("2026-04-08T17:00:00");
    }

    @Test
    @DisplayName("Redis Pub/Sub 수신 payload는 현재 사용자 닉네임 기준 ChatMessageResponse로 fan-out 한다")
    void handlePublishedMessageBroadcastsUsingCurrentUserNickname() throws Exception {
        User sender = userRepository.save(new User("pubsub-sender@test.com", "pw", "current-nickname"));
        ChatMessageCreatedEvent event = new ChatMessageCreatedEvent(
            ChatPubSubEventType.CHAT_MESSAGE_CREATED,
            5L,
            88L,
            sender.getId(),
            "redis fan-out",
            ChatMessageType.TEXT,
            LocalDateTime.of(2026, 4, 8, 17, 5)
        );
        String payload = objectMapper.writeValueAsString(event);
        doNothing().when(chatWebSocketBroadcaster).broadcast(any(ChatMessageResponse.class));

        chatPubSubService.handlePublishedMessage(payload);

        ArgumentCaptor<ChatMessageResponse> responseCaptor = ArgumentCaptor.forClass(ChatMessageResponse.class);
        verify(chatWebSocketBroadcaster).broadcast(responseCaptor.capture());

        ChatMessageResponse response = responseCaptor.getValue();

        assertThat(response.roomId()).isEqualTo(5L);
        assertThat(response.messageId()).isEqualTo(88L);
        assertThat(response.sender().userId()).isEqualTo(sender.getId());
        assertThat(response.sender().nickname()).isEqualTo("current-nickname");
        assertThat(response.content()).isEqualTo("redis fan-out");
        assertThat(response.type()).isEqualTo(ChatMessageType.TEXT);
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 4, 8, 17, 5));
    }

    @Test
    @DisplayName("트랜잭션이 rollback 되면 Redis publish는 발생하지 않는다")
    void publishMessageCreatedAfterCommitSkipsPublishOnRollback() {
        ChatMessageCreatedEvent event = new ChatMessageCreatedEvent(
            ChatPubSubEventType.CHAT_MESSAGE_CREATED,
            9L,
            321L,
            12L,
            "rollback",
            ChatMessageType.TEXT,
            LocalDateTime.of(2026, 4, 8, 17, 10)
        );
        doReturn(1L).when(stringRedisTemplate).convertAndSend(anyString(), anyString());

        assertThatThrownBy(() -> rollbackTestHelper.publishThenRollback(event))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("force rollback");

        verify(stringRedisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @TestConfiguration
    static class ChatPubSubTestConfig {

        @Bean
        ChatPubSubRollbackTestHelper chatPubSubRollbackTestHelper(ChatPubSubService chatPubSubService) {
            return new ChatPubSubRollbackTestHelper(chatPubSubService);
        }
    }

    static class ChatPubSubRollbackTestHelper {

        private final ChatPubSubService chatPubSubService;

        ChatPubSubRollbackTestHelper(ChatPubSubService chatPubSubService) {
            this.chatPubSubService = chatPubSubService;
        }

        @Transactional
        public void publishThenRollback(ChatMessageCreatedEvent event) {
            chatPubSubService.publishMessageCreatedAfterCommit(event);
            throw new IllegalStateException("force rollback");
        }
    }
}

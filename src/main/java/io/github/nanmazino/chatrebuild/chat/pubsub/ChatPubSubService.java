package io.github.nanmazino.chatrebuild.chat.pubsub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatMessageResponse;
import io.github.nanmazino.chatrebuild.chat.websocket.ChatWebSocketBroadcaster;
import io.github.nanmazino.chatrebuild.global.util.AfterCommitExecutor;
import io.github.nanmazino.chatrebuild.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatPubSubService {

    public static final String CHANNEL_NAME = "chat:events";

    private static final Logger log = LoggerFactory.getLogger(ChatPubSubService.class);

    private final AfterCommitExecutor afterCommitExecutor;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final ChatWebSocketBroadcaster chatWebSocketBroadcaster;

    public void publishMessageCreatedAfterCommit(ChatMessageCreatedEvent event) {
        afterCommitExecutor.run(() -> publishMessageCreated(event));
    }

    public void handlePublishedMessage(String payload) {
        try {
            ChatMessageCreatedEvent event = objectMapper.readValue(payload, ChatMessageCreatedEvent.class);
            handleMessageCreatedEvent(event);
        } catch (JsonProcessingException | RuntimeException exception) {
            log.warn("Redis Pub/Sub message handling failed. payload={}", payload, exception);
        }
    }

    private void publishMessageCreated(ChatMessageCreatedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            stringRedisTemplate.convertAndSend(CHANNEL_NAME, payload);
        } catch (JsonProcessingException | RuntimeException exception) {
            log.warn("Redis Pub/Sub publish failed. eventType={}, roomId={}, messageId={}",
                event.eventType(),
                event.roomId(),
                event.messageId(),
                exception
            );
        }
    }

    private void handleMessageCreatedEvent(ChatMessageCreatedEvent event) {
        if (event.eventType() != ChatPubSubEventType.CHAT_MESSAGE_CREATED) {
            log.warn("Unsupported Redis Pub/Sub event type. eventType={}", event.eventType());
            return;
        }

        String senderNickname = userRepository.findNicknameById(event.senderId())
            .orElseThrow(() -> new IllegalStateException("메시지 발신자 닉네임을 찾을 수 없습니다."));
        ChatMessageResponse response = event.toChatMessageResponse(senderNickname);

        chatWebSocketBroadcaster.broadcast(response);
    }
}

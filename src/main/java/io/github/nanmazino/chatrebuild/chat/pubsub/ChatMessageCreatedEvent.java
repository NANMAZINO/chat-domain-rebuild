package io.github.nanmazino.chatrebuild.chat.pubsub;

import io.github.nanmazino.chatrebuild.chat.dto.response.ChatMessageResponse;
import io.github.nanmazino.chatrebuild.chat.entity.ChatMessage;
import io.github.nanmazino.chatrebuild.chat.entity.ChatMessageType;
import java.time.LocalDateTime;

public record ChatMessageCreatedEvent(
    ChatPubSubEventType eventType,
    Long roomId,
    Long messageId,
    Long senderId,
    String content,
    ChatMessageType type,
    LocalDateTime createdAt
) {

    public static ChatMessageCreatedEvent from(ChatMessage message) {
        return new ChatMessageCreatedEvent(
            ChatPubSubEventType.CHAT_MESSAGE_CREATED,
            message.getRoom().getId(),
            message.getId(),
            message.getSender().getId(),
            message.getContent(),
            message.getType(),
            message.getCreatedAt()
        );
    }

    public ChatMessageResponse toChatMessageResponse(String senderNickname) {
        return new ChatMessageResponse(
            messageId,
            roomId,
            new ChatMessageResponse.Sender(senderId, senderNickname),
            content,
            type,
            createdAt
        );
    }
}

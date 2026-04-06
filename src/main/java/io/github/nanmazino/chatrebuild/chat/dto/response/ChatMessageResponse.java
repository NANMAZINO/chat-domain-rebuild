package io.github.nanmazino.chatrebuild.chat.dto.response;

import io.github.nanmazino.chatrebuild.chat.entity.ChatMessage;
import io.github.nanmazino.chatrebuild.chat.entity.ChatMessageType;
import java.time.LocalDateTime;

public record ChatMessageResponse(
    Long messageId,
    Long roomId,
    Sender sender,
    String content,
    ChatMessageType type,
    LocalDateTime createdAt
) {

    public static ChatMessageResponse from(ChatMessage message) {
        return from(message.getRoom().getId(), message);
    }

    public static ChatMessageResponse from(Long roomId, ChatMessage message) {
        return new ChatMessageResponse(
            message.getId(),
            roomId,
            new Sender(
                message.getSender().getId(),
                message.getSender().getNickname()
            ),
            message.getContent(),
            message.getType(),
            message.getCreatedAt()
        );
    }

    public record Sender(
        Long userId,
        String nickname
    ) {
    }
}

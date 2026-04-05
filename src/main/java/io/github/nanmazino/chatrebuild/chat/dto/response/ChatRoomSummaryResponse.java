package io.github.nanmazino.chatrebuild.chat.dto.response;

import java.time.LocalDateTime;

public record ChatRoomSummaryResponse(
    Long roomId,
    Long postId,
    String postTitle,
    int memberCount,
    Long lastMessageId,
    String lastMessagePreview,
    LocalDateTime lastMessageAt,
    Long lastReadMessageId,
    long unreadCount
) {
}

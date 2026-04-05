package io.github.nanmazino.chatrebuild.chat.dto.response;

import java.time.LocalDateTime;

public record ChatRoomDetailResponse(
    Long roomId,
    Long postId,
    String postTitle,
    int memberCount,
    Long lastMessageId,
    String lastMessagePreview,
    LocalDateTime lastMessageAt
) {
}

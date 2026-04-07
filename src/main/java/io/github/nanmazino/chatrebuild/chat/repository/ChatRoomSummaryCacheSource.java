package io.github.nanmazino.chatrebuild.chat.repository;

import io.github.nanmazino.chatrebuild.chat.dto.response.ChatRoomDetailResponse;
import java.time.LocalDateTime;

public record ChatRoomSummaryCacheSource(
    Long roomId,
    Long postId,
    String postTitle,
    int memberCount,
    Long lastMessageId,
    String lastMessagePreview,
    LocalDateTime lastMessageAt,
    LocalDateTime createdAt
) {

    public ChatRoomDetailResponse toResponse() {
        return new ChatRoomDetailResponse(
            roomId,
            postId,
            postTitle,
            memberCount,
            lastMessageId,
            lastMessagePreview,
            lastMessageAt
        );
    }
}

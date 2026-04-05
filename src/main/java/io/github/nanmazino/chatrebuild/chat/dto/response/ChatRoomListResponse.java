package io.github.nanmazino.chatrebuild.chat.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ChatRoomListResponse(
    List<ChatRoomSummaryResponse> items,
    LocalDateTime nextCursorLastMessageAt,
    Long nextCursorRoomId,
    boolean hasNext
) {
}

package io.github.nanmazino.chatrebuild.chat.dto.response;

import java.util.List;

public record ChatMessageHistoryResponse(
    List<ChatMessageResponse> items,
    Long nextCursor,
    boolean hasNext
) {
}

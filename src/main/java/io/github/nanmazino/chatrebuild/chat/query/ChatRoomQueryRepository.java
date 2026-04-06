package io.github.nanmazino.chatrebuild.chat.query;

import io.github.nanmazino.chatrebuild.chat.dto.response.ChatRoomSummaryResponse;
import java.time.LocalDateTime;
import java.util.List;

public interface ChatRoomQueryRepository {

    List<ChatRoomSummaryResponse> findMyChatRooms(
        Long userId,
        LocalDateTime cursorLastMessageAt,
        Long cursorRoomId,
        int size,
        String keyword
    );
}

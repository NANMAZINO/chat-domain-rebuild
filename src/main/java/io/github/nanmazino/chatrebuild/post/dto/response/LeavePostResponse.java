package io.github.nanmazino.chatrebuild.post.dto.response;

import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMemberStatus;
import java.time.LocalDateTime;

public record LeavePostResponse(
    Long postId,
    Long chatRoomId,
    ChatRoomMemberStatus memberStatus,
    int memberCount,
    LocalDateTime leftAt
) {

}

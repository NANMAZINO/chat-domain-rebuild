package io.github.nanmazino.chatrebuild.chat.dto.response;

import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMember;
import java.time.LocalDateTime;

public record ChatReadResponse(
    Long roomId,
    Long lastReadMessageId,
    LocalDateTime lastReadAt
) {

    public static ChatReadResponse from(ChatRoomMember member) {
        return new ChatReadResponse(
            member.getRoom().getId(),
            member.getLastReadMessageId(),
            member.getLastReadAt()
        );
    }
}

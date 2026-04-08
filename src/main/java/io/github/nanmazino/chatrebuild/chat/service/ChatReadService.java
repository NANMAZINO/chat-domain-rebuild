package io.github.nanmazino.chatrebuild.chat.service;

import io.github.nanmazino.chatrebuild.chat.dto.request.ChatReadRequest;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatReadResponse;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMember;
import io.github.nanmazino.chatrebuild.chat.exception.InvalidLastReadMessageIdException;
import io.github.nanmazino.chatrebuild.chat.repository.ChatMessageRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatReadService {

    private final ChatMembershipService chatMembershipService;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public ChatReadResponse markAsRead(Long roomId, Long userId, ChatReadRequest request) {
        ChatRoomMember activeMember = chatMembershipService.getActiveMemberForUpdate(roomId, userId);

        if (activeMember.hasReadAtLeast(request.lastReadMessageId())) {
            return ChatReadResponse.from(activeMember);
        }

        validateLastReadMessageId(roomId, request.lastReadMessageId());
        activeMember.updateLastRead(request.lastReadMessageId(), LocalDateTime.now());

        return ChatReadResponse.from(activeMember);
    }

    private void validateLastReadMessageId(Long roomId, Long lastReadMessageId) {
        if (!chatMessageRepository.existsByRoomIdAndId(roomId, lastReadMessageId)) {
            throw new InvalidLastReadMessageIdException();
        }
    }
}

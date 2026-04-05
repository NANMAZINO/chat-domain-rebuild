package io.github.nanmazino.chatrebuild.chat.service;

import io.github.nanmazino.chatrebuild.chat.dto.request.ChatSendRequest;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatMessageResponse;
import io.github.nanmazino.chatrebuild.chat.entity.ChatMessage;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMember;
import io.github.nanmazino.chatrebuild.chat.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {

    private final ChatMembershipService chatMembershipService;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public ChatMessageResponse sendMessage(Long roomId, Long userId, ChatSendRequest request) {
        ChatRoomMember activeMember = chatMembershipService.getActiveMember(roomId, userId);
        ChatMessage savedMessage = chatMessageRepository.save(new ChatMessage(
            activeMember.getRoom(),
            activeMember.getUser(),
            request.content(),
            request.type()
        ));

        return ChatMessageResponse.from(savedMessage);
    }
}

package io.github.nanmazino.chatrebuild.chat.service;

import io.github.nanmazino.chatrebuild.chat.dto.request.ChatSendRequest;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatMessageHistoryResponse;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatMessageResponse;
import io.github.nanmazino.chatrebuild.chat.entity.ChatMessage;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMember;
import io.github.nanmazino.chatrebuild.chat.repository.ChatMessageRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {

    private final ChatMembershipService chatMembershipService;
    private final ChatMessageRepository chatMessageRepository;

    public ChatMessageHistoryResponse getMessages(Long roomId, Long userId, Long cursorMessageId, int size) {
        chatMembershipService.getActiveMember(roomId, userId);

        List<ChatMessage> fetchedMessages = getFetchedMessages(roomId, cursorMessageId, size + 1);
        boolean hasNext = fetchedMessages.size() > size;
        List<ChatMessageResponse> items = fetchedMessages.stream()
            .limit(size)
            .map(ChatMessageResponse::from)
            .toList();
        Long nextCursor = hasNext && !items.isEmpty()
            ? items.get(items.size() - 1).messageId()
            : null;

        return new ChatMessageHistoryResponse(items, nextCursor, hasNext);
    }

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

    private List<ChatMessage> getFetchedMessages(Long roomId, Long cursorMessageId, int fetchSize) {
        PageRequest pageRequest = PageRequest.of(0, fetchSize);

        if (cursorMessageId == null) {
            return chatMessageRepository.findRecentMessages(roomId, pageRequest);
        }

        return chatMessageRepository.findRecentMessagesBeforeCursor(roomId, cursorMessageId, pageRequest);
    }
}

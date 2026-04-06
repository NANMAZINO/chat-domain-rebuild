package io.github.nanmazino.chatrebuild.chat.service;

import io.github.nanmazino.chatrebuild.chat.dto.request.ChatSendRequest;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatMessageHistoryResponse;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatMessageResponse;
import io.github.nanmazino.chatrebuild.chat.entity.ChatMessage;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMember;
import io.github.nanmazino.chatrebuild.chat.exception.InvalidChatMessageCursorException;
import io.github.nanmazino.chatrebuild.chat.repository.ChatMessageRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {

    private final ChatMembershipService chatMembershipService;
    private final ChatMessageRepository chatMessageRepository;

    public ChatMessageHistoryResponse getMessages(Long roomId, Long userId, Long cursorMessageId, int size) {
        chatMembershipService.getActiveMember(roomId, userId);
        MessagePage messagePage = getMessagePage(roomId, cursorMessageId, size);
        List<ChatMessageResponse> items = messagePage.messages().stream()
            .limit(size)
            .map(message -> ChatMessageResponse.from(roomId, message))
            .toList();
        Long nextCursor = messagePage.hasNext() && !items.isEmpty()
            ? items.get(items.size() - 1).messageId()
            : null;

        return new ChatMessageHistoryResponse(items, nextCursor, messagePage.hasNext());
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
        activeMember.getRoom().updateLastMessageSummary(
            savedMessage.getId(),
            savedMessage.getContent(),
            savedMessage.getCreatedAt()
        );

        return ChatMessageResponse.from(savedMessage);
    }

    private MessagePage getMessagePage(Long roomId, Long cursorMessageId, int size) {
        if (cursorMessageId == null) {
            List<ChatMessage> fetchedMessages = chatMessageRepository.findRecentMessages(
                roomId,
                PageRequest.of(0, size + 1)
            );

            return buildMessagePage(fetchedMessages, size);
        }

        if (cursorMessageId <= 0) {
            throw new InvalidChatMessageCursorException();
        }

        List<ChatMessage> fetchedMessages = chatMessageRepository.findRecentMessagesAtOrBeforeCursor(
            roomId,
            cursorMessageId,
            PageRequest.of(0, size + 2)
        );

        if (fetchedMessages.isEmpty() || !fetchedMessages.get(0).getId().equals(cursorMessageId)) {
            throw new InvalidChatMessageCursorException();
        }

        return buildMessagePage(fetchedMessages.subList(1, fetchedMessages.size()), size);
    }

    private MessagePage buildMessagePage(List<ChatMessage> fetchedMessages, int size) {
        boolean hasNext = fetchedMessages.size() > size;
        List<ChatMessage> messages = fetchedMessages.stream()
            .limit(size)
            .toList();

        return new MessagePage(messages, hasNext);
    }

    private record MessagePage(
        List<ChatMessage> messages,
        boolean hasNext
    ) {
    }
}

package io.github.nanmazino.chatrebuild.chat.service;

import io.github.nanmazino.chatrebuild.chat.dto.request.ChatSendRequest;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatMessageHistoryResponse;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatMessageResponse;
import io.github.nanmazino.chatrebuild.chat.entity.ChatMessage;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMember;
import io.github.nanmazino.chatrebuild.chat.exception.InvalidChatMessageCursorException;
import io.github.nanmazino.chatrebuild.chat.repository.ChatMessageHistoryProjection;
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
        chatMembershipService.validateActiveMember(roomId, userId);
        MessagePage<ChatMessageHistoryProjection> messagePage = getMessagePage(roomId, cursorMessageId, size);
        List<ChatMessageResponse> items = messagePage.items().stream()
            .map(message -> new ChatMessageResponse(
                message.getMessageId(),
                roomId,
                new ChatMessageResponse.Sender(
                    message.getSenderId(),
                    message.getSenderNickname()
                ),
                message.getContent(),
                message.getType(),
                message.getCreatedAt()
            ))
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

    private MessagePage<ChatMessageHistoryProjection> getMessagePage(Long roomId, Long cursorMessageId, int size) {
        if (cursorMessageId == null) {
            List<ChatMessageHistoryProjection> fetchedMessages = chatMessageRepository.findRecentMessageHistory(
                roomId,
                PageRequest.of(0, size + 1)
            );

            return buildMessagePage(fetchedMessages, size);
        }

        if (cursorMessageId <= 0) {
            throw new InvalidChatMessageCursorException();
        }

        if (!chatMessageRepository.existsCursorInRoom(roomId, cursorMessageId)) {
            throw new InvalidChatMessageCursorException();
        }

        List<ChatMessageHistoryProjection> fetchedMessages = chatMessageRepository.findRecentMessageHistoryBeforeCursor(
            roomId,
            cursorMessageId,
            PageRequest.of(0, size + 1)
        );

        return buildMessagePage(fetchedMessages, size);
    }

    private <T> MessagePage<T> buildMessagePage(List<T> fetchedItems, int size) {
        boolean hasNext = fetchedItems.size() > size;
        List<T> items = fetchedItems.stream()
            .limit(size)
            .toList();

        return new MessagePage<>(items, hasNext);
    }

    private record MessagePage<T>(
        List<T> items,
        boolean hasNext
    ) {
    }
}

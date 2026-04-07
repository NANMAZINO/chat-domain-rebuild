package io.github.nanmazino.chatrebuild.chat.service;

import io.github.nanmazino.chatrebuild.chat.cache.ChatCacheService;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatRoomDetailResponse;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatRoomListResponse;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatRoomSummaryResponse;
import io.github.nanmazino.chatrebuild.chat.query.ChatRoomQueryRepository;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomRepository;
import io.github.nanmazino.chatrebuild.chat.exception.InvalidChatRoomCursorException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {

    private final ChatCacheService chatCacheService;
    private final ChatMembershipService chatMembershipService;
    private final ChatRoomQueryRepository chatRoomQueryRepository;
    private final ChatRoomRepository chatRoomRepository;

    public ChatRoomListResponse getChatRooms(
        Long userId,
        LocalDateTime cursorLastMessageAt,
        Long cursorRoomId,
        int size,
        String keyword
    ) {
        validateCursor(cursorLastMessageAt, cursorRoomId);

        List<ChatRoomSummaryResponse> fetchedItems = chatRoomQueryRepository.findMyChatRooms(
            userId,
            cursorLastMessageAt,
            cursorRoomId,
            size,
            normalizeKeyword(keyword)
        );
        boolean hasNext = fetchedItems.size() > size;
        List<ChatRoomSummaryResponse> items = fetchedItems.stream()
            .limit(size)
            .toList();

        if (!hasNext || items.isEmpty()) {
            return new ChatRoomListResponse(items, null, null, false);
        }

        ChatRoomSummaryResponse lastItem = items.get(items.size() - 1);

        return new ChatRoomListResponse(
            items,
            lastItem.lastMessageAt(),
            lastItem.roomId(),
            true
        );
    }

    public ChatRoomDetailResponse getChatRoom(Long roomId, Long userId) {
        chatMembershipService.validateActiveMember(roomId, userId);
        return chatCacheService.getOrLoadRoomSummary(roomId, () -> loadRoomSummary(roomId));
    }

    private void validateCursor(LocalDateTime cursorLastMessageAt, Long cursorRoomId) {
        if (cursorLastMessageAt != null && cursorRoomId == null) {
            throw new InvalidChatRoomCursorException();
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }

    private ChatRoomDetailResponse loadRoomSummary(Long roomId) {
        return chatRoomRepository.findRoomSummaryById(roomId)
            .orElseThrow(() -> new IllegalStateException("채팅방 summary를 찾을 수 없습니다."));
    }
}

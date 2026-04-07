package io.github.nanmazino.chatrebuild.chat.service;

import io.github.nanmazino.chatrebuild.chat.cache.ChatCacheService;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatRoomDetailResponse;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatRoomListResponse;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatRoomSummaryResponse;
import io.github.nanmazino.chatrebuild.chat.query.ChatRoomQueryRepository;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomSummaryCacheSource;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomRepository;
import io.github.nanmazino.chatrebuild.chat.exception.InvalidChatRoomCursorException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {

    public static final int DEFAULT_ROOM_LIST_SIZE = 20;
    private static final Duration ROOM_SUMMARY_DORMANT_THRESHOLD = Duration.ofMinutes(30);

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
        String normalizedKeyword = normalizeKeyword(keyword);
        return loadChatRooms(userId, cursorLastMessageAt, cursorRoomId, size, normalizedKeyword);
    }

    public ChatRoomDetailResponse getChatRoom(Long roomId, Long userId) {
        chatMembershipService.validateActiveMember(roomId, userId);
        return chatCacheService.findRoomSummary(roomId)
            .orElseGet(() -> loadAndMaybeCacheRoomSummary(roomId));
    }

    private ChatRoomListResponse loadChatRooms(
        Long userId,
        LocalDateTime cursorLastMessageAt,
        Long cursorRoomId,
        int size,
        String keyword
    ) {
        List<ChatRoomSummaryResponse> fetchedItems = chatRoomQueryRepository.findMyChatRooms(
            userId,
            cursorLastMessageAt,
            cursorRoomId,
            size,
            keyword
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

    private ChatRoomDetailResponse loadAndMaybeCacheRoomSummary(Long roomId) {
        ChatRoomSummaryCacheSource summarySource = loadRoomSummarySource(roomId);

        if (!isDormant(summarySource)) {
            return summarySource.toResponse();
        }

        // 휴면 방 miss 경로는 저장 직전에 한 번 더 최신 summary를 확인해 stale 재캐시 가능성을 줄인다.
        ChatRoomSummaryCacheSource latestSummarySource = loadRoomSummarySource(roomId);
        ChatRoomDetailResponse response = latestSummarySource.toResponse();

        if (isDormant(latestSummarySource)) {
            chatCacheService.saveRoomSummary(roomId, response);
        }

        return response;
    }

    private ChatRoomSummaryCacheSource loadRoomSummarySource(Long roomId) {
        return chatRoomRepository.findRoomSummaryCacheSourceById(roomId)
            .orElseThrow(() -> new IllegalStateException("채팅방 summary를 찾을 수 없습니다."));
    }

    private boolean isDormant(ChatRoomSummaryCacheSource summarySource) {
        LocalDateTime summaryUpdatedAt = summarySource.lastMessageAt() != null
            ? summarySource.lastMessageAt()
            : summarySource.createdAt();

        return summaryUpdatedAt != null
            && !summaryUpdatedAt.isAfter(LocalDateTime.now().minus(ROOM_SUMMARY_DORMANT_THRESHOLD));
    }
}

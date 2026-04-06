package io.github.nanmazino.chatrebuild.chat.service;

import io.github.nanmazino.chatrebuild.chat.dto.response.ChatRoomDetailResponse;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatRoomListResponse;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatRoomSummaryResponse;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMember;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMemberStatus;
import io.github.nanmazino.chatrebuild.chat.exception.InvalidChatRoomCursorException;
import io.github.nanmazino.chatrebuild.chat.repository.ChatMessageRepository;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomMemberRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {

    private final ChatMembershipService chatMembershipService;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ChatRoomListResponse getChatRooms(
        Long userId,
        LocalDateTime cursorLastMessageAt,
        Long cursorRoomId,
        int size,
        String keyword
    ) {
        validateCursor(cursorLastMessageAt, cursorRoomId);

        List<RoomSnapshot> roomSnapshots = chatRoomMemberRepository.findAllByUserIdAndStatusWithRoomAndPost(
                userId,
                ChatRoomMemberStatus.ACTIVE,
                normalizeKeyword(keyword)
            )
            .stream()
            .map(this::resolveRoomSnapshot)
            .filter(snapshot -> isAfterCursor(snapshot, cursorLastMessageAt, cursorRoomId))
            .toList();

        boolean hasNext = roomSnapshots.size() > size;
        List<ChatRoomSummaryResponse> items = roomSnapshots.stream()
            .limit(size)
            .map(RoomSnapshot::toSummaryResponse)
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
        ChatRoomMember activeMember = chatMembershipService.getActiveMember(roomId, userId);

        return resolveRoomSnapshot(activeMember).toDetailResponse();
    }

    private RoomSnapshot resolveRoomSnapshot(ChatRoomMember activeMember) {
        Long roomId = activeMember.getRoom().getId();
        return new RoomSnapshot(
            roomId,
            activeMember.getRoom().getPost().getId(),
            activeMember.getRoom().getPost().getTitle(),
            activeMember.getRoom().getMemberCount(),
            activeMember.getRoom().getLastMessageId(),
            activeMember.getRoom().getLastMessagePreview(),
            activeMember.getRoom().getLastMessageAt(),
            activeMember.getLastReadMessageId(),
            calculateUnreadCount(roomId, activeMember.getLastReadMessageId())
        );
    }

    private long calculateUnreadCount(Long roomId, Long lastReadMessageId) {
        if (lastReadMessageId == null) {
            return chatMessageRepository.countByRoomId(roomId);
        }

        return chatMessageRepository.countByRoomIdAndIdGreaterThan(roomId, lastReadMessageId);
    }

    private boolean isAfterCursor(RoomSnapshot snapshot, LocalDateTime cursorLastMessageAt, Long cursorRoomId) {
        if (cursorLastMessageAt == null && cursorRoomId == null) {
            return true;
        }

        if (cursorLastMessageAt == null) {
            return snapshot.lastMessageAt() == null && snapshot.roomId() < cursorRoomId;
        }

        if (snapshot.lastMessageAt() == null) {
            return true;
        }

        if (snapshot.lastMessageAt().isBefore(cursorLastMessageAt)) {
            return true;
        }

        return snapshot.lastMessageAt().isEqual(cursorLastMessageAt) && snapshot.roomId() < cursorRoomId;
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

    private record RoomSnapshot(
        Long roomId,
        Long postId,
        String postTitle,
        int memberCount,
        Long lastMessageId,
        String lastMessagePreview,
        LocalDateTime lastMessageAt,
        Long lastReadMessageId,
        long unreadCount
    ) {

        private ChatRoomSummaryResponse toSummaryResponse() {
            return new ChatRoomSummaryResponse(
                roomId,
                postId,
                postTitle,
                memberCount,
                lastMessageId,
                lastMessagePreview,
                lastMessageAt,
                lastReadMessageId,
                unreadCount
            );
        }

        private ChatRoomDetailResponse toDetailResponse() {
            return new ChatRoomDetailResponse(
                roomId,
                postId,
                postTitle,
                memberCount,
                lastMessageId,
                lastMessagePreview,
                lastMessageAt
            );
        }
    }
}

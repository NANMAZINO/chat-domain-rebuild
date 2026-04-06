package io.github.nanmazino.chatrebuild.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nanmazino.chatrebuild.chat.dto.response.ChatRoomDetailResponse;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatRoomListResponse;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatRoomSummaryResponse;
import io.github.nanmazino.chatrebuild.chat.entity.ChatMessage;
import io.github.nanmazino.chatrebuild.chat.entity.ChatMessageType;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoom;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMember;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMemberStatus;
import io.github.nanmazino.chatrebuild.chat.repository.ChatMessageRepository;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomMemberRepository;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomRepository;
import io.github.nanmazino.chatrebuild.post.entity.Post;
import io.github.nanmazino.chatrebuild.post.entity.PostStatus;
import io.github.nanmazino.chatrebuild.post.repository.PostRepository;
import io.github.nanmazino.chatrebuild.support.IntegrationTestSupport;
import io.github.nanmazino.chatrebuild.user.entity.User;
import io.github.nanmazino.chatrebuild.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@ActiveProfiles("test")
class ChatRoomServiceTest extends IntegrationTestSupport {

    @Autowired
    private ChatRoomService chatRoomService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User author;
    private User member;

    @BeforeEach
    void setUp() {
        author = userRepository.save(new User("author-room-service@test.com", "pw", "author-room-service"));
        member = userRepository.save(new User("member-room-service@test.com", "pw", "member-room-service"));
    }

    @AfterEach
    void tearDown() {
        chatMessageRepository.deleteAllInBatch();
        chatRoomMemberRepository.deleteAllInBatch();
        chatRoomRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("room list는 lastMessageAt desc, roomId desc 순으로 정렬하고 메시지 없는 방을 null 버킷으로 포함한다")
    void getChatRoomsSortsByLastMessageAtAndRoomIdAndIncludesNullBucket() {
        RoomFixture oldRoom = createRoom("old-room", PostStatus.OPEN);
        RoomFixture sameTimeLowerRoom = createRoom("same-time-lower-room", PostStatus.OPEN);
        RoomFixture sameTimeHigherRoom = createRoom("same-time-higher-room", PostStatus.OPEN);
        RoomFixture nullBucketLowerRoom = createRoom("null-bucket-lower-room", PostStatus.OPEN);
        RoomFixture nullBucketHigherRoom = createRoom("null-bucket-higher-room", PostStatus.OPEN);

        storeRoomSummary(oldRoom.room(),
            saveMessage(oldRoom.room(), author, "old-message", LocalDateTime.of(2026, 4, 2, 19, 0)));
        storeRoomSummary(sameTimeLowerRoom.room(), saveMessage(
            sameTimeLowerRoom.room(),
            author,
            "same-time-lower-message",
            LocalDateTime.of(2026, 4, 2, 20, 30)
        ));
        storeRoomSummary(sameTimeHigherRoom.room(), saveMessage(
            sameTimeHigherRoom.room(),
            author,
            "same-time-higher-message",
            LocalDateTime.of(2026, 4, 2, 20, 30)
        ));

        ChatRoomListResponse response = chatRoomService.getChatRooms(member.getId(), null, null, 10, null);

        assertThat(response.items())
            .extracting(ChatRoomSummaryResponse::roomId)
            .containsExactly(
                sameTimeHigherRoom.room().getId(),
                sameTimeLowerRoom.room().getId(),
                oldRoom.room().getId(),
                nullBucketHigherRoom.room().getId(),
                nullBucketLowerRoom.room().getId()
            );
        assertThat(response.items().get(3).lastMessageAt()).isNull();
        assertThat(response.items().get(4).lastMessageAt()).isNull();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursorLastMessageAt()).isNull();
        assertThat(response.nextCursorRoomId()).isNull();
    }

    @Test
    @DisplayName("room list는 복합 cursor로 다음 페이지를 계산한다")
    void getChatRoomsCalculatesNextPageByCompositeCursor() {
        RoomFixture latestRoom = createRoom("latest-room", PostStatus.OPEN);
        RoomFixture sameTimeLowerRoom = createRoom("same-time-lower-room", PostStatus.OPEN);
        RoomFixture sameTimeHigherRoom = createRoom("same-time-higher-room", PostStatus.OPEN);
        RoomFixture nullBucketRoom = createRoom("null-bucket-room", PostStatus.OPEN);

        storeRoomSummary(latestRoom.room(),
            saveMessage(latestRoom.room(), author, "latest-message", LocalDateTime.of(2026, 4, 2, 21, 0)));
        storeRoomSummary(sameTimeLowerRoom.room(), saveMessage(
            sameTimeLowerRoom.room(),
            author,
            "same-time-lower-message",
            LocalDateTime.of(2026, 4, 2, 20, 0)
        ));
        storeRoomSummary(sameTimeHigherRoom.room(), saveMessage(
            sameTimeHigherRoom.room(),
            author,
            "same-time-higher-message",
            LocalDateTime.of(2026, 4, 2, 20, 0)
        ));

        ChatRoomListResponse firstPage = chatRoomService.getChatRooms(member.getId(), null, null, 2, null);
        ChatRoomListResponse nextPage = chatRoomService.getChatRooms(
            member.getId(),
            firstPage.nextCursorLastMessageAt(),
            firstPage.nextCursorRoomId(),
            2,
            null
        );

        assertThat(firstPage.items())
            .extracting(ChatRoomSummaryResponse::roomId)
            .containsExactly(latestRoom.room().getId(), sameTimeHigherRoom.room().getId());
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.nextCursorLastMessageAt()).isEqualTo(LocalDateTime.of(2026, 4, 2, 20, 0));
        assertThat(firstPage.nextCursorRoomId()).isEqualTo(sameTimeHigherRoom.room().getId());

        assertThat(nextPage.items())
            .extracting(ChatRoomSummaryResponse::roomId)
            .containsExactly(sameTimeLowerRoom.room().getId(), nullBucketRoom.room().getId());
        assertThat(nextPage.hasNext()).isFalse();
        assertThat(nextPage.nextCursorLastMessageAt()).isNull();
        assertThat(nextPage.nextCursorRoomId()).isNull();
    }

    @Test
    @DisplayName("null 버킷 cursor는 cursorRoomId만으로 다음 페이지를 이어간다")
    void getChatRoomsContinuesNullBucketWithCursorRoomIdOnly() {
        RoomFixture roomWithMessage = createRoom("room-with-message", PostStatus.OPEN);
        RoomFixture nullBucketLowerRoom = createRoom("null-bucket-lower-room", PostStatus.OPEN);
        RoomFixture nullBucketHigherRoom = createRoom("null-bucket-higher-room", PostStatus.OPEN);

        storeRoomSummary(roomWithMessage.room(), saveMessage(
            roomWithMessage.room(),
            author,
            "visible-before-null-bucket",
            LocalDateTime.of(2026, 4, 2, 21, 0)
        ));

        ChatRoomListResponse response = chatRoomService.getChatRooms(
            member.getId(),
            null,
            nullBucketHigherRoom.room().getId(),
            10,
            null
        );

        assertThat(response.items())
            .extracting(ChatRoomSummaryResponse::roomId)
            .containsExactly(nullBucketLowerRoom.room().getId());
    }

    @Test
    @DisplayName("읽음 이력이 없으면 unreadCount는 전체 메시지 수를 사용한다")
    void getChatRoomsUsesTotalMessageCountWhenLastReadMessageIdIsNull() {
        RoomFixture room = createRoom("unread-all-room", PostStatus.OPEN);

        saveMessage(room.room(), author, "message-1", LocalDateTime.of(2026, 4, 2, 19, 0));
        saveMessage(room.room(), member, "message-2", LocalDateTime.of(2026, 4, 2, 19, 5));
        ChatMessage latest = saveMessage(room.room(), author, "message-3", LocalDateTime.of(2026, 4, 2, 19, 10));
        storeRoomSummary(room.room(), latest);

        ChatRoomSummaryResponse response = chatRoomService.getChatRooms(member.getId(), null, null, 10, null)
            .items()
            .get(0);

        assertThat(response.lastReadMessageId()).isNull();
        assertThat(response.unreadCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("room list summary는 chat_rooms에 저장된 값을 그대로 사용한다")
    void getChatRoomsUsesStoredSummaryValues() {
        RoomFixture room = createRoom("summary-clock-skew-room", PostStatus.OPEN);

        ChatMessage laterByTime = saveMessage(room.room(), author, "later-by-time", LocalDateTime.of(2026, 4, 2, 21, 0));
        ChatMessage laterById = saveMessage(room.room(), member, "later-by-id", LocalDateTime.of(2026, 4, 2, 20, 0));
        storeRoomSummary(room.room(), laterByTime);

        ChatRoomSummaryResponse response = chatRoomService.getChatRooms(member.getId(), null, null, 10, null)
            .items()
            .stream()
            .filter(item -> item.roomId().equals(room.room().getId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("조회한 채팅방이 목록에 있어야 합니다."));

        assertThat(laterById.getId()).isGreaterThan(laterByTime.getId());
        assertThat(response.lastMessageId()).isEqualTo(laterByTime.getId());
        assertThat(response.lastMessagePreview()).isEqualTo("later-by-time");
        assertThat(response.lastMessageAt()).isEqualTo(LocalDateTime.of(2026, 4, 2, 21, 0));
    }

    @Test
    @DisplayName("lastReadMessageId가 있으면 그 이후 메시지 수만 unreadCount로 계산한다")
    void getChatRoomsUsesMessagesAfterLastReadMessageIdForUnreadCount() {
        RoomFixture room = createRoom("partial-read-room", PostStatus.OPEN);

        ChatMessage first = saveMessage(room.room(), author, "message-1", LocalDateTime.of(2026, 4, 2, 19, 0));
        ChatMessage second = saveMessage(room.room(), member, "message-2", LocalDateTime.of(2026, 4, 2, 19, 5));
        ChatMessage latest = saveMessage(room.room(), author, "message-3", LocalDateTime.of(2026, 4, 2, 19, 10));
        storeRoomSummary(room.room(), latest);
        markLastReadMessage(room.memberMembership(), second.getId());

        ChatRoomSummaryResponse response = chatRoomService.getChatRooms(member.getId(), null, null, 10, null)
            .items()
            .get(0);

        assertThat(first.getId()).isLessThan(second.getId());
        assertThat(response.lastReadMessageId()).isEqualTo(second.getId());
        assertThat(response.unreadCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("CLOSED 또는 DELETED 게시글이어도 ACTIVE 멤버는 room list와 summary를 조회할 수 있다")
    void getChatRoomsAndSummaryAllowActiveMemberForClosedOrDeletedPost() {
        RoomFixture closedRoom = createRoom("closed-room", PostStatus.CLOSED);
        RoomFixture deletedRoom = createRoom("deleted-room", PostStatus.DELETED);

        storeRoomSummary(closedRoom.room(), saveMessage(
            closedRoom.room(),
            author,
            "closed-room-message",
            LocalDateTime.of(2026, 4, 2, 19, 0)
        ));
        storeRoomSummary(deletedRoom.room(), saveMessage(
            deletedRoom.room(),
            author,
            "deleted-room-message",
            LocalDateTime.of(2026, 4, 2, 19, 10)
        ));

        ChatRoomListResponse listResponse = chatRoomService.getChatRooms(member.getId(), null, null, 10, null);
        ChatRoomDetailResponse closedSummary = chatRoomService.getChatRoom(closedRoom.room().getId(), member.getId());
        ChatRoomDetailResponse deletedSummary = chatRoomService.getChatRoom(deletedRoom.room().getId(), member.getId());

        assertThat(listResponse.items())
            .extracting(ChatRoomSummaryResponse::postTitle)
            .contains("closed-room", "deleted-room");
        assertThat(closedSummary.postTitle()).isEqualTo("closed-room");
        assertThat(deletedSummary.postTitle()).isEqualTo("deleted-room");
    }

    private RoomFixture createRoom(String title, PostStatus status) {
        Post post = postRepository.save(new Post(author, title, "content", 4, status));
        ChatRoom room = chatRoomRepository.save(new ChatRoom(post));

        chatRoomMemberRepository.save(new ChatRoomMember(
            room,
            author,
            ChatRoomMemberStatus.ACTIVE,
            post.getCreatedAt()
        ));
        room.increaseMemberCount();

        ChatRoomMember memberMembership = chatRoomMemberRepository.save(new ChatRoomMember(
            room,
            member,
            ChatRoomMemberStatus.ACTIVE,
            post.getCreatedAt()
        ));
        room.increaseMemberCount();

        chatRoomRepository.saveAndFlush(room);

        return new RoomFixture(post, room, memberMembership);
    }

    private ChatMessage saveMessage(ChatRoom room, User sender, String content, LocalDateTime createdAt) {
        ChatMessage message = chatMessageRepository.save(new ChatMessage(room, sender, content, ChatMessageType.TEXT));

        jdbcTemplate.update(
            "update chat_messages set created_at = ? where id = ?",
            Timestamp.valueOf(createdAt),
            message.getId()
        );
        ReflectionTestUtils.setField(message, "createdAt", createdAt);

        return message;
    }

    private void storeRoomSummary(ChatRoom room, ChatMessage message) {
        room.updateLastMessageSummary(message.getId(), message.getContent(), message.getCreatedAt());
        chatRoomRepository.saveAndFlush(room);
    }

    private void markLastReadMessage(ChatRoomMember memberMembership, Long lastReadMessageId) {
        ReflectionTestUtils.setField(memberMembership, "lastReadMessageId", lastReadMessageId);
        ReflectionTestUtils.setField(memberMembership, "lastReadAt", LocalDateTime.now());
        chatRoomMemberRepository.saveAndFlush(memberMembership);
    }

    private record RoomFixture(
        Post post,
        ChatRoom room,
        ChatRoomMember memberMembership
    ) {
    }
}

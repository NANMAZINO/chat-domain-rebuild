package io.github.nanmazino.chatrebuild.chat.query;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.List;
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
class ChatRoomQueryRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private ChatRoomQueryRepository chatRoomQueryRepository;

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
        author = userRepository.save(new User("author-room-query@test.com", "pw", "author-room-query"));
        member = userRepository.save(new User("member-room-query@test.com", "pw", "member-room-query"));
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
    @DisplayName("QueryDSL room list는 lastMessageAt desc, roomId desc 순으로 정렬하고 null 버킷을 뒤로 보낸다")
    void findMyChatRoomsSortsByLastMessageAtAndRoomIdAndIncludesNullBucket() {
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

        List<ChatRoomSummaryResponse> response = chatRoomQueryRepository.findMyChatRooms(
            member.getId(),
            null,
            null,
            10,
            null
        );

        assertThat(response)
            .extracting(ChatRoomSummaryResponse::roomId)
            .containsExactly(
                sameTimeHigherRoom.room().getId(),
                sameTimeLowerRoom.room().getId(),
                oldRoom.room().getId(),
                nullBucketHigherRoom.room().getId(),
                nullBucketLowerRoom.room().getId()
            );
        assertThat(response.get(3).lastMessageAt()).isNull();
        assertThat(response.get(4).lastMessageAt()).isNull();
    }

    @Test
    @DisplayName("QueryDSL room list는 size+1 조회와 복합 cursor 조건을 함께 처리한다")
    void findMyChatRoomsFetchesSizePlusOneAndAppliesCompositeCursor() {
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

        List<ChatRoomSummaryResponse> firstPageCandidates = chatRoomQueryRepository.findMyChatRooms(
            member.getId(),
            null,
            null,
            2,
            null
        );
        List<ChatRoomSummaryResponse> nextPageCandidates = chatRoomQueryRepository.findMyChatRooms(
            member.getId(),
            LocalDateTime.of(2026, 4, 2, 20, 0),
            sameTimeHigherRoom.room().getId(),
            2,
            null
        );

        assertThat(firstPageCandidates)
            .extracting(ChatRoomSummaryResponse::roomId)
            .containsExactly(
                latestRoom.room().getId(),
                sameTimeHigherRoom.room().getId(),
                sameTimeLowerRoom.room().getId()
            );
        assertThat(nextPageCandidates)
            .extracting(ChatRoomSummaryResponse::roomId)
            .containsExactly(
                sameTimeLowerRoom.room().getId(),
                nullBucketRoom.room().getId()
            );
    }

    @Test
    @DisplayName("QueryDSL room list는 null 버킷 cursorRoomId만으로 다음 페이지를 잇는다")
    void findMyChatRoomsContinuesNullBucketWithCursorRoomIdOnly() {
        RoomFixture roomWithMessage = createRoom("room-with-message", PostStatus.OPEN);
        RoomFixture nullBucketLowerRoom = createRoom("null-bucket-lower-room", PostStatus.OPEN);
        RoomFixture nullBucketHigherRoom = createRoom("null-bucket-higher-room", PostStatus.OPEN);

        storeRoomSummary(roomWithMessage.room(), saveMessage(
            roomWithMessage.room(),
            author,
            "visible-before-null-bucket",
            LocalDateTime.of(2026, 4, 2, 21, 0)
        ));

        List<ChatRoomSummaryResponse> response = chatRoomQueryRepository.findMyChatRooms(
            member.getId(),
            null,
            nullBucketHigherRoom.room().getId(),
            10,
            null
        );

        assertThat(response)
            .extracting(ChatRoomSummaryResponse::roomId)
            .containsExactly(nullBucketLowerRoom.room().getId());
    }

    @Test
    @DisplayName("QueryDSL room list는 post title keyword 검색을 적용한다")
    void findMyChatRoomsFiltersByKeyword() {
        createRoom("강남역 저녁", PostStatus.OPEN);
        createRoom("홍대 점심", PostStatus.OPEN);
        createRoom("강남 스터디", PostStatus.OPEN);

        List<ChatRoomSummaryResponse> response = chatRoomQueryRepository.findMyChatRooms(
            member.getId(),
            null,
            null,
            10,
            "강남"
        );

        assertThat(response)
            .extracting(ChatRoomSummaryResponse::postTitle)
            .containsExactly("강남 스터디", "강남역 저녁");
    }

    @Test
    @DisplayName("QueryDSL projection은 stored summary, lastReadMessageId, unreadCount 규칙을 그대로 반영한다")
    void findMyChatRoomsProjectsStoredSummaryAndUnreadCount() {
        RoomFixture summaryRoom = createRoom("summary-room", PostStatus.OPEN);
        RoomFixture unreadAllRoom = createRoom("unread-all-room", PostStatus.OPEN);

        ChatMessage laterByTime = saveMessage(
            summaryRoom.room(),
            author,
            "later-by-time",
            LocalDateTime.of(2026, 4, 2, 21, 0)
        );
        ChatMessage laterById = saveMessage(
            summaryRoom.room(),
            member,
            "later-by-id",
            LocalDateTime.of(2026, 4, 2, 20, 0)
        );
        storeRoomSummary(summaryRoom.room(), laterByTime);
        markLastReadMessage(summaryRoom.memberMembership(), laterByTime.getId());

        saveMessage(unreadAllRoom.room(), author, "message-1", LocalDateTime.of(2026, 4, 2, 19, 0));
        saveMessage(unreadAllRoom.room(), member, "message-2", LocalDateTime.of(2026, 4, 2, 19, 5));
        ChatMessage unreadAllLatest = saveMessage(
            unreadAllRoom.room(),
            author,
            "message-3",
            LocalDateTime.of(2026, 4, 2, 19, 10)
        );
        storeRoomSummary(unreadAllRoom.room(), unreadAllLatest);

        List<ChatRoomSummaryResponse> response = chatRoomQueryRepository.findMyChatRooms(
            member.getId(),
            null,
            null,
            10,
            null
        );

        ChatRoomSummaryResponse summaryRoomResponse = findByRoomId(response, summaryRoom.room().getId());
        ChatRoomSummaryResponse unreadAllRoomResponse = findByRoomId(response, unreadAllRoom.room().getId());

        assertThat(laterById.getId()).isGreaterThan(laterByTime.getId());
        assertThat(summaryRoomResponse.lastMessageId()).isEqualTo(laterByTime.getId());
        assertThat(summaryRoomResponse.lastMessagePreview()).isEqualTo("later-by-time");
        assertThat(summaryRoomResponse.lastMessageAt()).isEqualTo(LocalDateTime.of(2026, 4, 2, 21, 0));
        assertThat(summaryRoomResponse.lastReadMessageId()).isEqualTo(laterByTime.getId());
        assertThat(summaryRoomResponse.unreadCount()).isEqualTo(1L);

        assertThat(unreadAllRoomResponse.lastReadMessageId()).isNull();
        assertThat(unreadAllRoomResponse.lastMessageId()).isEqualTo(unreadAllLatest.getId());
        assertThat(unreadAllRoomResponse.unreadCount()).isEqualTo(3L);
    }

    private ChatRoomSummaryResponse findByRoomId(List<ChatRoomSummaryResponse> response, Long roomId) {
        return response.stream()
            .filter(item -> item.roomId().equals(roomId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("조회한 채팅방이 목록에 있어야 합니다."));
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

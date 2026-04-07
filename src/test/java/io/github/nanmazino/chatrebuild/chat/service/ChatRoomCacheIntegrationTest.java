package io.github.nanmazino.chatrebuild.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.github.nanmazino.chatrebuild.chat.cache.ChatCacheRepository;
import io.github.nanmazino.chatrebuild.chat.dto.request.ChatSendRequest;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatMessageResponse;
import io.github.nanmazino.chatrebuild.chat.dto.response.ChatRoomDetailResponse;
import io.github.nanmazino.chatrebuild.chat.entity.ChatMessage;
import io.github.nanmazino.chatrebuild.chat.entity.ChatMessageType;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoom;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMember;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMemberStatus;
import io.github.nanmazino.chatrebuild.chat.exception.ChatMemberNotFoundException;
import io.github.nanmazino.chatrebuild.chat.repository.ChatMessageRepository;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomMemberRepository;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomRepository;
import io.github.nanmazino.chatrebuild.post.dto.request.UpdatePostRequest;
import io.github.nanmazino.chatrebuild.post.entity.Post;
import io.github.nanmazino.chatrebuild.post.entity.PostStatus;
import io.github.nanmazino.chatrebuild.post.repository.PostRepository;
import io.github.nanmazino.chatrebuild.post.service.PostService;
import io.github.nanmazino.chatrebuild.support.IntegrationTestSupport;
import io.github.nanmazino.chatrebuild.user.entity.User;
import io.github.nanmazino.chatrebuild.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@ActiveProfiles("test")
class ChatRoomCacheIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ChatRoomService chatRoomService;

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private ChatMembershipService chatMembershipService;

    @Autowired
    private PostService postService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @MockitoSpyBean
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @MockitoSpyBean
    private ChatCacheRepository chatCacheRepository;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    private User author;
    private User member;
    private User outsider;

    @BeforeEach
    void setUp() {
        author = userRepository.save(new User("author-cache@test.com", "pw", "author-cache"));
        member = userRepository.save(new User("member-cache@test.com", "pw", "member-cache"));
        outsider = userRepository.save(new User("outsider-cache@test.com", "pw", "outsider-cache"));
    }

    @AfterEach
    void tearDown() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }

        chatMessageRepository.deleteAllInBatch();
        chatRoomMemberRepository.deleteAllInBatch();
        chatRoomRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("room summary 첫 조회는 DB에서 읽고 Redis에 캐시한다")
    void getChatRoomCachesSummaryAfterCacheMiss() {
        RoomFixture fixture = createRoom("cache-miss-room", List.of(author, member));
        ChatMessage latest = saveMessageAndStoreSummary(fixture.room(), author, "첫 캐시 대상 메시지");

        clearInvocations(chatRoomRepository);

        ChatRoomDetailResponse response = chatRoomService.getChatRoom(fixture.room().getId(), member.getId());

        assertThat(response.roomId()).isEqualTo(fixture.room().getId());
        assertThat(response.postId()).isEqualTo(fixture.post().getId());
        assertThat(response.postTitle()).isEqualTo("cache-miss-room");
        assertThat(response.memberCount()).isEqualTo(2);
        assertThat(response.lastMessageId()).isEqualTo(latest.getId());
        assertThat(response.lastMessagePreview()).isEqualTo("첫 캐시 대상 메시지");
        assertThat(response.lastMessageAt()).isEqualToIgnoringNanos(latest.getCreatedAt());
        assertThat(chatCacheRepository.findRoomSummary(fixture.room().getId())).contains(response);
        verify(chatRoomRepository, times(1)).findRoomSummaryById(fixture.room().getId());
    }

    @Test
    @DisplayName("같은 room summary 재조회는 Redis cache hit로 DB 재조회 없이 응답한다")
    void getChatRoomUsesRedisCacheOnSecondRead() {
        RoomFixture fixture = createRoom("cache-hit-room", List.of(author, member));
        saveMessageAndStoreSummary(fixture.room(), author, "cache-hit-message");

        clearInvocations(chatRoomRepository);

        ChatRoomDetailResponse firstResponse = chatRoomService.getChatRoom(fixture.room().getId(), member.getId());
        ChatRoomDetailResponse secondResponse = chatRoomService.getChatRoom(fixture.room().getId(), member.getId());

        assertThat(secondResponse).isEqualTo(firstResponse);
        verify(chatRoomRepository, times(1)).findRoomSummaryById(fixture.room().getId());
    }

    @Test
    @DisplayName("메시지 저장 후 room summary cache는 after-commit eviction 된다")
    void sendMessageEvictsRoomSummaryCacheAfterCommit() {
        RoomFixture fixture = createRoom("message-evict-room", List.of(author, member));
        ChatMessage previousMessage = saveMessageAndStoreSummary(fixture.room(), author, "이전 메시지");
        ChatRoomDetailResponse cachedResponse = chatRoomService.getChatRoom(fixture.room().getId(), member.getId());

        assertThat(cachedResponse.lastMessageId()).isEqualTo(previousMessage.getId());
        assertThat(chatCacheRepository.findRoomSummary(fixture.room().getId())).isPresent();

        ChatMessageResponse sendResponse = chatMessageService.sendMessage(
            fixture.room().getId(),
            member.getId(),
            new ChatSendRequest("새 메시지", ChatMessageType.TEXT)
        );

        assertThat(chatCacheRepository.findRoomSummary(fixture.room().getId())).isEmpty();

        ChatRoomDetailResponse refreshedResponse = chatRoomService.getChatRoom(fixture.room().getId(), member.getId());

        assertThat(sendResponse.messageId()).isEqualTo(refreshedResponse.lastMessageId());
        assertThat(refreshedResponse.lastMessagePreview()).isEqualTo("새 메시지");
    }

    @Test
    @DisplayName("참여와 나가기 후 room summary cache는 after-commit eviction 된다")
    void joinAndLeaveEvictRoomSummaryCacheAfterCommit() {
        RoomFixture fixture = createRoom("membership-evict-room", List.of(author));

        ChatRoomDetailResponse initialSummary = chatRoomService.getChatRoom(fixture.room().getId(), author.getId());

        assertThat(initialSummary.memberCount()).isEqualTo(1);
        assertThat(chatCacheRepository.findRoomSummary(fixture.room().getId())).isPresent();

        chatMembershipService.joinPost(fixture.post().getId(), member.getId());

        assertThat(chatCacheRepository.findRoomSummary(fixture.room().getId())).isEmpty();

        ChatRoomDetailResponse joinedSummary = chatRoomService.getChatRoom(fixture.room().getId(), author.getId());

        assertThat(joinedSummary.memberCount()).isEqualTo(2);
        assertThat(chatCacheRepository.findRoomSummary(fixture.room().getId())).isPresent();

        chatMembershipService.leavePost(fixture.post().getId(), member.getId());

        assertThat(chatCacheRepository.findRoomSummary(fixture.room().getId())).isEmpty();

        ChatRoomDetailResponse leftSummary = chatRoomService.getChatRoom(fixture.room().getId(), author.getId());

        assertThat(leftSummary.memberCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("게시글 제목 수정 후 room summary cache는 after-commit eviction 된다")
    void updatePostEvictsRoomSummaryCacheAfterCommit() {
        RoomFixture fixture = createRoom("before-title", List.of(author, member));
        chatRoomService.getChatRoom(fixture.room().getId(), member.getId());

        assertThat(chatCacheRepository.findRoomSummary(fixture.room().getId())).isPresent();

        postService.updatePost(
            fixture.post().getId(),
            author.getId(),
            new UpdatePostRequest("after-title", "updated-content", 5)
        );

        assertThat(chatCacheRepository.findRoomSummary(fixture.room().getId())).isEmpty();

        ChatRoomDetailResponse refreshedResponse = chatRoomService.getChatRoom(fixture.room().getId(), member.getId());

        assertThat(refreshedResponse.postTitle()).isEqualTo("after-title");
    }

    @Test
    @DisplayName("Redis read 실패 시 room summary 조회는 DB fallback으로 응답한다")
    void getChatRoomFallsBackToDbWhenRedisReadFails() {
        RoomFixture fixture = createRoom("redis-read-fallback-room", List.of(author, member));
        ChatMessage latest = saveMessageAndStoreSummary(fixture.room(), author, "fallback-message");
        doThrow(new RuntimeException("redis read failed"))
            .when(chatCacheRepository)
            .findRoomSummary(fixture.room().getId());

        clearInvocations(chatRoomRepository);

        ChatRoomDetailResponse response = chatRoomService.getChatRoom(fixture.room().getId(), member.getId());

        assertThat(response.lastMessageId()).isEqualTo(latest.getId());
        assertThat(response.lastMessagePreview()).isEqualTo("fallback-message");
        verify(chatRoomRepository, times(1)).findRoomSummaryById(fixture.room().getId());
    }

    @Test
    @DisplayName("Redis save 실패 시 room summary 조회는 DB 결과를 그대로 반환한다")
    void getChatRoomReturnsDbResultWhenRedisSaveFails() {
        RoomFixture fixture = createRoom("redis-save-fallback-room", List.of(author, member));
        ChatMessage latest = saveMessageAndStoreSummary(fixture.room(), author, "save-fallback-message");
        doThrow(new RuntimeException("redis save failed"))
            .when(chatCacheRepository)
            .saveRoomSummary(eq(fixture.room().getId()), any(ChatRoomDetailResponse.class));

        ChatRoomDetailResponse response = chatRoomService.getChatRoom(fixture.room().getId(), member.getId());

        assertThat(response.lastMessageId()).isEqualTo(latest.getId());
        assertThat(chatCacheRepository.findRoomSummary(fixture.room().getId())).isEmpty();
    }

    @Test
    @DisplayName("Redis eviction 실패가 나도 메시지 저장은 DB commit을 우선한다")
    void sendMessageKeepsDbCommitWhenRedisEvictionFails() {
        RoomFixture fixture = createRoom("redis-evict-fallback-room", List.of(author, member));
        ChatMessage previousMessage = saveMessageAndStoreSummary(fixture.room(), author, "stale-cache-message");
        ChatRoomDetailResponse cachedResponse = chatRoomService.getChatRoom(fixture.room().getId(), member.getId());
        doThrow(new RuntimeException("redis evict failed"))
            .when(chatCacheRepository)
            .evictRoomSummary(fixture.room().getId());

        ChatMessageResponse sendResponse = chatMessageService.sendMessage(
            fixture.room().getId(),
            member.getId(),
            new ChatSendRequest("db-commit-wins", ChatMessageType.TEXT)
        );

        ChatRoomDetailResponse dbSummary = chatRoomRepository.findRoomSummaryById(fixture.room().getId())
            .orElseThrow(() -> new AssertionError("DB summary가 있어야 합니다."));

        assertThat(cachedResponse.lastMessageId()).isEqualTo(previousMessage.getId());
        assertThat(chatCacheRepository.findRoomSummary(fixture.room().getId())).contains(cachedResponse);
        assertThat(dbSummary.lastMessageId()).isEqualTo(sendResponse.messageId());
        assertThat(dbSummary.lastMessagePreview()).isEqualTo("db-commit-wins");
    }

    @Test
    @DisplayName("cache hit 상태여도 ACTIVE 멤버가 아니면 room summary 조회에 실패한다")
    void getChatRoomStillRejectsNonMemberAfterCacheWarm() {
        RoomFixture fixture = createRoom("cache-auth-room", List.of(author, member));
        saveMessageAndStoreSummary(fixture.room(), author, "auth-message");
        chatRoomService.getChatRoom(fixture.room().getId(), member.getId());

        assertThat(chatCacheRepository.findRoomSummary(fixture.room().getId())).isPresent();
        assertThatThrownBy(() -> chatRoomService.getChatRoom(fixture.room().getId(), outsider.getId()))
            .isInstanceOf(ChatMemberNotFoundException.class);
    }

    private RoomFixture createRoom(String title, List<User> activeMembers) {
        Post post = postRepository.save(new Post(author, title, "content", 5, PostStatus.OPEN));
        ChatRoom room = chatRoomRepository.save(new ChatRoom(post));

        for (User activeMember : activeMembers) {
            chatRoomMemberRepository.save(new ChatRoomMember(
                room,
                activeMember,
                ChatRoomMemberStatus.ACTIVE,
                post.getCreatedAt()
            ));
            room.increaseMemberCount();
        }

        chatRoomRepository.saveAndFlush(room);

        return new RoomFixture(post, room);
    }

    private ChatMessage saveMessageAndStoreSummary(ChatRoom room, User sender, String content) {
        ChatMessage message = chatMessageRepository.save(new ChatMessage(room, sender, content, ChatMessageType.TEXT));
        room.updateLastMessageSummary(message.getId(), message.getContent(), message.getCreatedAt());
        chatRoomRepository.saveAndFlush(room);
        return message;
    }

    private record RoomFixture(
        Post post,
        ChatRoom room
    ) {
    }
}

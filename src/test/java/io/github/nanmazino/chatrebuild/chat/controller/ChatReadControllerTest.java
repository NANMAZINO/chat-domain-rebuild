package io.github.nanmazino.chatrebuild.chat.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.nanmazino.chatrebuild.chat.entity.ChatMessage;
import io.github.nanmazino.chatrebuild.chat.entity.ChatMessageType;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoom;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMember;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMemberStatus;
import io.github.nanmazino.chatrebuild.chat.repository.ChatMessageRepository;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomMemberRepository;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomRepository;
import io.github.nanmazino.chatrebuild.global.security.JwtTokenProvider;
import io.github.nanmazino.chatrebuild.post.entity.Post;
import io.github.nanmazino.chatrebuild.post.entity.PostStatus;
import io.github.nanmazino.chatrebuild.post.repository.PostRepository;
import io.github.nanmazino.chatrebuild.support.IntegrationTestSupport;
import io.github.nanmazino.chatrebuild.user.entity.User;
import io.github.nanmazino.chatrebuild.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatReadControllerTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User author;
    private User activeMember;
    private User outsider;

    @BeforeEach
    void setUp() {
        author = userRepository.save(new User(
            "author-read@example.com",
            passwordEncoder.encode("Password123!"),
            "author-read"
        ));
        activeMember = userRepository.save(new User(
            "member-read@example.com",
            passwordEncoder.encode("Password123!"),
            "member-read"
        ));
        outsider = userRepository.save(new User(
            "outsider-read@example.com",
            passwordEncoder.encode("Password123!"),
            "outsider-read"
        ));
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
    @DisplayName("ACTIVE 멤버는 마지막 읽은 메시지를 저장할 수 있다")
    void markAsReadSuccess() throws Exception {
        RoomFixture fixture = createRoomWithActiveMember("읽음 처리 성공", PostStatus.OPEN);
        ChatMessage first = saveMessageAndStoreSummary(fixture.room(), author, "첫 메시지");
        ChatMessage latest = saveMessageAndStoreSummary(fixture.room(), activeMember, "두 번째 메시지");
        String accessToken = accessToken(activeMember);

        mockMvc.perform(patch("/api/chat-rooms/" + fixture.room().getId() + "/read")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lastReadMessageId": %d
                    }
                    """.formatted(latest.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.roomId").value(fixture.room().getId()))
            .andExpect(jsonPath("$.data.lastReadMessageId").value(latest.getId()))
            .andExpect(jsonPath("$.data.lastReadAt").isString())
            .andExpect(jsonPath("$.error").value(nullValue()));

        ChatRoomMember savedMember = findMember(fixture.activeMemberMember().getId());
        org.assertj.core.api.Assertions.assertThat(savedMember.getLastReadMessageId()).isEqualTo(latest.getId());
        org.assertj.core.api.Assertions.assertThat(savedMember.getLastReadAt()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(first.getId()).isLessThan(latest.getId());
    }

    @Test
    @DisplayName("읽음 처리 이후 room list는 최신 unreadCount를 반영한다")
    void markAsReadUpdatesUnreadCountInRoomList() throws Exception {
        RoomFixture fixture = createRoomWithActiveMember("읽음 처리 후 목록 반영", PostStatus.OPEN);
        ChatMessage first = saveMessageAndStoreSummary(fixture.room(), author, "message-1");
        ChatMessage second = saveMessageAndStoreSummary(fixture.room(), activeMember, "message-2");
        ChatMessage latest = saveMessageAndStoreSummary(fixture.room(), author, "message-3");
        markLastReadMessage(fixture.activeMemberMember(), first.getId(), LocalDateTime.of(2026, 4, 8, 20, 20));
        String accessToken = accessToken(activeMember);

        mockMvc.perform(patch("/api/chat-rooms/" + fixture.room().getId() + "/read")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lastReadMessageId": %d
                    }
                    """.formatted(latest.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.lastReadMessageId").value(latest.getId()));

        mockMvc.perform(get("/api/chat-rooms")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].roomId").value(fixture.room().getId()))
            .andExpect(jsonPath("$.data.items[0].lastReadMessageId").value(latest.getId()))
            .andExpect(jsonPath("$.data.items[0].unreadCount").value(0));

        org.assertj.core.api.Assertions.assertThat(second.getId()).isGreaterThan(first.getId());
    }

    @Test
    @DisplayName("더 작은 lastReadMessageId 요청은 no-op으로 기존 읽음 상태를 반환한다")
    void markAsReadNoOpWhenRequestIsSmallerThanCurrentState() throws Exception {
        RoomFixture fixture = createRoomWithActiveMember("읽음 처리 no-op", PostStatus.OPEN);
        ChatMessage first = saveMessageAndStoreSummary(fixture.room(), author, "message-1");
        ChatMessage second = saveMessageAndStoreSummary(fixture.room(), activeMember, "message-2");
        LocalDateTime existingReadAt = LocalDateTime.of(2026, 4, 8, 20, 30, 0);
        markLastReadMessage(fixture.activeMemberMember(), second.getId(), existingReadAt);
        String accessToken = accessToken(activeMember);

        mockMvc.perform(patch("/api/chat-rooms/" + fixture.room().getId() + "/read")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lastReadMessageId": %d
                    }
                    """.formatted(first.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roomId").value(fixture.room().getId()))
            .andExpect(jsonPath("$.data.lastReadMessageId").value(second.getId()))
            .andExpect(jsonPath("$.data.lastReadAt").value(existingReadAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));

        ChatRoomMember savedMember = findMember(fixture.activeMemberMember().getId());
        org.assertj.core.api.Assertions.assertThat(savedMember.getLastReadMessageId()).isEqualTo(second.getId());
        org.assertj.core.api.Assertions.assertThat(savedMember.getLastReadAt()).isEqualTo(existingReadAt);
    }

    @Test
    @DisplayName("같은 lastReadMessageId를 다시 보내면 no-op으로 기존 읽음 시각을 유지한다")
    void markAsReadNoOpWhenRequestEqualsCurrentState() throws Exception {
        RoomFixture fixture = createRoomWithActiveMember("읽음 처리 동등 no-op", PostStatus.OPEN);
        ChatMessage latest = saveMessageAndStoreSummary(fixture.room(), author, "message-1");
        LocalDateTime existingReadAt = LocalDateTime.of(2026, 4, 8, 20, 31, 0);
        markLastReadMessage(fixture.activeMemberMember(), latest.getId(), existingReadAt);
        String accessToken = accessToken(activeMember);

        mockMvc.perform(patch("/api/chat-rooms/" + fixture.room().getId() + "/read")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lastReadMessageId": %d
                    }
                    """.formatted(latest.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.lastReadMessageId").value(latest.getId()))
            .andExpect(jsonPath("$.data.lastReadAt").value(existingReadAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));

        ChatRoomMember savedMember = findMember(fixture.activeMemberMember().getId());
        org.assertj.core.api.Assertions.assertThat(savedMember.getLastReadMessageId()).isEqualTo(latest.getId());
        org.assertj.core.api.Assertions.assertThat(savedMember.getLastReadAt()).isEqualTo(existingReadAt);
    }

    @Test
    @DisplayName("비멤버는 읽음 처리할 수 없다")
    void markAsReadFailsWhenUserIsNotMember() throws Exception {
        RoomFixture fixture = createRoomWithActiveMember("비멤버 차단", PostStatus.OPEN);
        ChatMessage latest = saveMessageAndStoreSummary(fixture.room(), author, "message-1");
        String accessToken = accessToken(outsider);

        mockMvc.perform(patch("/api/chat-rooms/" + fixture.room().getId() + "/read")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lastReadMessageId": %d
                    }
                    """.formatted(latest.getId())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("CHAT_MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("LEFT 멤버는 읽음 처리할 수 없다")
    void markAsReadFailsWhenUserLeftRoom() throws Exception {
        RoomFixture fixture = createRoomWithActiveMember("나간 멤버 차단", PostStatus.OPEN);
        ChatMessage latest = saveMessageAndStoreSummary(fixture.room(), author, "message-1");
        fixture.activeMemberMember().leave(LocalDateTime.of(2026, 4, 8, 20, 40));
        chatRoomMemberRepository.saveAndFlush(fixture.activeMemberMember());
        String accessToken = accessToken(activeMember);

        mockMvc.perform(patch("/api/chat-rooms/" + fixture.room().getId() + "/read")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lastReadMessageId": %d
                    }
                    """.formatted(latest.getId())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("CHAT_MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("존재하지 않는 lastReadMessageId는 400과 공통 검증 에러를 반환한다")
    void markAsReadRejectsNonExistentMessageId() throws Exception {
        RoomFixture fixture = createRoomWithActiveMember("존재하지 않는 메시지", PostStatus.OPEN);
        String accessToken = accessToken(activeMember);

        mockMvc.perform(patch("/api/chat-rooms/" + fixture.room().getId() + "/read")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lastReadMessageId": 999999
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("lastReadMessageId가 올바르지 않습니다."));
    }

    @Test
    @DisplayName("다른 방 메시지 ID로 읽음 처리하면 400과 공통 검증 에러를 반환한다")
    void markAsReadRejectsMessageIdFromAnotherRoom() throws Exception {
        RoomFixture fixture = createRoomWithActiveMember("현재 방", PostStatus.OPEN);
        RoomFixture otherFixture = createRoomWithActiveMember("다른 방", PostStatus.OPEN);
        ChatMessage otherRoomMessage = saveMessageAndStoreSummary(otherFixture.room(), author, "other-room-message");
        String accessToken = accessToken(activeMember);

        mockMvc.perform(patch("/api/chat-rooms/" + fixture.room().getId() + "/read")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lastReadMessageId": %d
                    }
                    """.formatted(otherRoomMessage.getId())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("lastReadMessageId가 올바르지 않습니다."));
    }

    @Test
    @DisplayName("인증 없이 읽음 처리하면 401을 반환한다")
    void markAsReadUnauthorizedWithoutToken() throws Exception {
        RoomFixture fixture = createRoomWithActiveMember("인증 차단", PostStatus.OPEN);
        ChatMessage latest = saveMessageAndStoreSummary(fixture.room(), author, "message-1");

        mockMvc.perform(patch("/api/chat-rooms/" + fixture.room().getId() + "/read")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lastReadMessageId": %d
                    }
                    """.formatted(latest.getId())))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
    }

    private RoomFixture createRoomWithActiveMember(String title, PostStatus status) {
        Post post = postRepository.save(new Post(author, title, "content", 4, status));
        ChatRoom room = chatRoomRepository.save(new ChatRoom(post));

        ChatRoomMember authorMember = chatRoomMemberRepository.save(new ChatRoomMember(
            room,
            author,
            ChatRoomMemberStatus.ACTIVE,
            post.getCreatedAt()
        ));
        room.increaseMemberCount();

        ChatRoomMember member = chatRoomMemberRepository.save(new ChatRoomMember(
            room,
            activeMember,
            ChatRoomMemberStatus.ACTIVE,
            post.getCreatedAt()
        ));
        room.increaseMemberCount();

        chatRoomRepository.saveAndFlush(room);

        return new RoomFixture(post, room, authorMember, member);
    }

    private ChatMessage saveMessageAndStoreSummary(ChatRoom room, User sender, String content) {
        ChatMessage message = chatMessageRepository.save(new ChatMessage(room, sender, content, ChatMessageType.TEXT));
        room.updateLastMessageSummary(message.getId(), message.getContent(), message.getCreatedAt());
        chatRoomRepository.saveAndFlush(room);
        return message;
    }

    private void markLastReadMessage(ChatRoomMember member, Long lastReadMessageId, LocalDateTime lastReadAt) {
        ReflectionTestUtils.setField(member, "lastReadMessageId", lastReadMessageId);
        ReflectionTestUtils.setField(member, "lastReadAt", lastReadAt);
        chatRoomMemberRepository.saveAndFlush(member);
    }

    private ChatRoomMember findMember(Long memberId) {
        Optional<ChatRoomMember> member = chatRoomMemberRepository.findById(memberId);

        return member.orElseThrow(() -> new AssertionError("채팅방 멤버를 찾을 수 있어야 합니다."));
    }

    private String accessToken(User user) {
        return jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record RoomFixture(
        Post post,
        ChatRoom room,
        ChatRoomMember authorMember,
        ChatRoomMember activeMemberMember
    ) {
    }
}

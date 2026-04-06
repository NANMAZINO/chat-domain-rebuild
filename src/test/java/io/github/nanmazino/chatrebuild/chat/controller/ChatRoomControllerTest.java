package io.github.nanmazino.chatrebuild.chat.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatRoomControllerTest extends IntegrationTestSupport {

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
            "author@example.com",
            passwordEncoder.encode("Password123!"),
            "author"
        ));
        activeMember = userRepository.save(new User(
            "member@example.com",
            passwordEncoder.encode("Password123!"),
            "member"
        ));
        outsider = userRepository.save(new User(
            "outsider@example.com",
            passwordEncoder.encode("Password123!"),
            "outsider"
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
    @DisplayName("ACTIVE 멤버는 내 채팅방 목록을 최종 응답 계약으로 조회할 수 있다")
    void getChatRoomsSuccess() throws Exception {
        RoomFixture fixture = createRoomWithMembers("강남역 저녁", PostStatus.OPEN, true);
        ChatMessage first = saveMessageAndStoreSummary(fixture.room(), author, "첫 메시지");
        ChatMessage latest = saveMessageAndStoreSummary(fixture.room(), activeMember, "오늘 7시로 확정할게요");
        markLastReadMessage(fixture.activeMemberMember(), first.getId());
        String accessToken = jwtTokenProvider.generateAccessToken(activeMember.getId(), activeMember.getEmail());

        mockMvc.perform(get("/api/chat-rooms")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].roomId").value(fixture.room().getId()))
            .andExpect(jsonPath("$.data.items[0].postId").value(fixture.post().getId()))
            .andExpect(jsonPath("$.data.items[0].postTitle").value("강남역 저녁"))
            .andExpect(jsonPath("$.data.items[0].memberCount").value(2))
            .andExpect(jsonPath("$.data.items[0].lastMessageId").value(latest.getId()))
            .andExpect(jsonPath("$.data.items[0].lastMessagePreview").value("오늘 7시로 확정할게요"))
            .andExpect(jsonPath("$.data.items[0].lastMessageAt").isString())
            .andExpect(jsonPath("$.data.items[0].lastReadMessageId").value(first.getId()))
            .andExpect(jsonPath("$.data.items[0].unreadCount").value(1))
            .andExpect(jsonPath("$.data.nextCursorLastMessageAt").value(nullValue()))
            .andExpect(jsonPath("$.data.nextCursorRoomId").value(nullValue()))
            .andExpect(jsonPath("$.data.hasNext").value(false))
            .andExpect(jsonPath("$.error").value(nullValue()));
    }

    @Test
    @DisplayName("ACTIVE 멤버는 CLOSED 또는 DELETED 게시글의 채팅방 summary도 조회할 수 있다")
    void getChatRoomSummarySuccessForClosedOrDeletedPost() throws Exception {
        RoomFixture fixture = createRoomWithMembers("삭제된 게시글 방", PostStatus.DELETED, true);
        ChatMessage latest = saveMessageAndStoreSummary(fixture.room(), author, "삭제된 게시글이어도 조회됩니다");
        String accessToken = jwtTokenProvider.generateAccessToken(activeMember.getId(), activeMember.getEmail());

        mockMvc.perform(get("/api/chat-rooms/" + fixture.room().getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.roomId").value(fixture.room().getId()))
            .andExpect(jsonPath("$.data.postId").value(fixture.post().getId()))
            .andExpect(jsonPath("$.data.postTitle").value("삭제된 게시글 방"))
            .andExpect(jsonPath("$.data.memberCount").value(2))
            .andExpect(jsonPath("$.data.lastMessageId").value(latest.getId()))
            .andExpect(jsonPath("$.data.lastMessagePreview").value("삭제된 게시글이어도 조회됩니다"))
            .andExpect(jsonPath("$.data.lastMessageAt").isString())
            .andExpect(jsonPath("$.error").value(nullValue()));
    }

    @Test
    @DisplayName("인증 없이 내 채팅방 목록을 조회하면 401을 반환한다")
    void getChatRoomsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/chat-rooms"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("ACTIVE 멤버가 아니면 채팅방 summary 조회에 실패한다")
    void getChatRoomSummaryFailsWhenUserIsNotActiveMember() throws Exception {
        RoomFixture fixture = createRoomWithMembers("비멤버 차단", PostStatus.OPEN, false);
        String accessToken = jwtTokenProvider.generateAccessToken(outsider.getId(), outsider.getEmail());

        mockMvc.perform(get("/api/chat-rooms/" + fixture.room().getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("CHAT_MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("내 채팅방 목록 조회에서 size가 0이면 400을 반환한다")
    void getChatRoomsRejectsNonPositiveSize() throws Exception {
        String accessToken = jwtTokenProvider.generateAccessToken(activeMember.getId(), activeMember.getEmail());

        mockMvc.perform(get("/api/chat-rooms")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .param("size", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("size는 1 이상이어야 합니다."));
    }

    @Test
    @DisplayName("cursorLastMessageAt만 전달하면 400과 공통 검증 에러를 반환한다")
    void getChatRoomsRejectsCursorLastMessageAtWithoutCursorRoomId() throws Exception {
        String accessToken = jwtTokenProvider.generateAccessToken(activeMember.getId(), activeMember.getEmail());

        mockMvc.perform(get("/api/chat-rooms")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .param("cursorLastMessageAt", LocalDateTime.of(2026, 4, 2, 20, 30)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("cursorRoomId는 cursorLastMessageAt와 함께 전달해야 합니다."));
    }

    @Test
    @DisplayName("ACTIVE 멤버는 메시지 히스토리를 최신순 cursor 응답으로 조회할 수 있다")
    void getMessagesSuccess() throws Exception {
        RoomFixture fixture = createRoomWithMembers("title", PostStatus.OPEN, true);
        ChatRoom room = fixture.room();
        ChatMessage oldest = chatMessageRepository.save(new ChatMessage(room, author, "첫 메시지", ChatMessageType.TEXT));
        ChatMessage middle = chatMessageRepository.save(new ChatMessage(room, activeMember, "두 번째 메시지", ChatMessageType.TEXT));
        ChatMessage latest = chatMessageRepository.save(new ChatMessage(room, author, "세 번째 메시지", ChatMessageType.TEXT));
        String accessToken = jwtTokenProvider.generateAccessToken(activeMember.getId(), activeMember.getEmail());

        mockMvc.perform(get("/api/chat-rooms/" + room.getId() + "/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].messageId").value(latest.getId()))
            .andExpect(jsonPath("$.data.items[1].messageId").value(middle.getId()))
            .andExpect(jsonPath("$.data.items[0].roomId").value(room.getId()))
            .andExpect(jsonPath("$.data.items[0].sender.userId").value(author.getId()))
            .andExpect(jsonPath("$.data.items[0].sender.nickname").value(author.getNickname()))
            .andExpect(jsonPath("$.data.items[0].content").value("세 번째 메시지"))
            .andExpect(jsonPath("$.data.items[0].type").value("TEXT"))
            .andExpect(jsonPath("$.data.nextCursor").value(middle.getId()))
            .andExpect(jsonPath("$.data.hasNext").value(true))
            .andExpect(jsonPath("$.error").value(nullValue()));
    }

    @Test
    @DisplayName("size를 생략하면 기본값 30으로 조회한다")
    void getMessagesUsesDefaultSize() throws Exception {
        ChatRoom room = createRoomWithMembers("title", PostStatus.OPEN, true).room();
        for (int index = 1; index <= 31; index++) {
            User sender = index % 2 == 0 ? author : activeMember;
            chatMessageRepository.save(new ChatMessage(room, sender, "message-" + index, ChatMessageType.TEXT));
        }
        String accessToken = jwtTokenProvider.generateAccessToken(activeMember.getId(), activeMember.getEmail());

        mockMvc.perform(get("/api/chat-rooms/" + room.getId() + "/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(30))
            .andExpect(jsonPath("$.data.hasNext").value(true))
            .andExpect(jsonPath("$.data.nextCursor").isNumber());
    }

    @Test
    @DisplayName("size가 0이면 400과 공통 검증 에러를 반환한다")
    void getMessagesRejectsNonPositiveSize() throws Exception {
        ChatRoom room = createRoomWithMembers("title", PostStatus.OPEN, true).room();
        String accessToken = jwtTokenProvider.generateAccessToken(activeMember.getId(), activeMember.getEmail());

        mockMvc.perform(get("/api/chat-rooms/" + room.getId() + "/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .param("size", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("size는 1 이상이어야 합니다."));
    }

    @Test
    @DisplayName("인증 없이 메시지 히스토리를 조회하면 401을 반환한다")
    void getMessagesUnauthorizedWithoutToken() throws Exception {
        ChatRoom room = createRoomWithMembers("title", PostStatus.OPEN, true).room();

        mockMvc.perform(get("/api/chat-rooms/" + room.getId() + "/messages"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("ACTIVE 멤버가 아니면 404와 CHAT_MEMBER_NOT_FOUND를 반환한다")
    void getMessagesFailsWhenUserIsNotActiveMember() throws Exception {
        ChatRoom room = createRoomWithMembers("title", PostStatus.OPEN, false).room();
        String accessToken = jwtTokenProvider.generateAccessToken(outsider.getId(), outsider.getEmail());

        mockMvc.perform(get("/api/chat-rooms/" + room.getId() + "/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("CHAT_MEMBER_NOT_FOUND"));
    }

    private RoomFixture createRoomWithMembers(String title, PostStatus status, boolean includeActiveMember) {
        Post post = postRepository.save(new Post(author, title, "content", 4, status));
        ChatRoom room = chatRoomRepository.save(new ChatRoom(post));

        ChatRoomMember authorMember = chatRoomMemberRepository.save(new ChatRoomMember(
            room,
            author,
            ChatRoomMemberStatus.ACTIVE,
            post.getCreatedAt()
        ));
        room.increaseMemberCount();

        ChatRoomMember savedActiveMember = null;
        if (includeActiveMember) {
            savedActiveMember = chatRoomMemberRepository.save(new ChatRoomMember(
                room,
                activeMember,
                ChatRoomMemberStatus.ACTIVE,
                post.getCreatedAt()
            ));
            room.increaseMemberCount();
        }

        chatRoomRepository.saveAndFlush(room);
        return new RoomFixture(post, room, authorMember, savedActiveMember);
    }

    private void markLastReadMessage(ChatRoomMember member, Long lastReadMessageId) {
        ReflectionTestUtils.setField(member, "lastReadMessageId", lastReadMessageId);
        ReflectionTestUtils.setField(member, "lastReadAt", LocalDateTime.now());
        chatRoomMemberRepository.saveAndFlush(member);
    }

    private ChatMessage saveMessageAndStoreSummary(ChatRoom room, User sender, String content) {
        ChatMessage message = chatMessageRepository.save(new ChatMessage(room, sender, content, ChatMessageType.TEXT));
        room.updateLastMessageSummary(message.getId(), message.getContent(), message.getCreatedAt());
        chatRoomRepository.saveAndFlush(room);
        return message;
    }

    private record RoomFixture(
        Post post,
        ChatRoom room,
        ChatRoomMember authorMember,
        ChatRoomMember activeMemberMember
    ) {
    }
}

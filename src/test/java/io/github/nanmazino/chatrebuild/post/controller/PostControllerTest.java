package io.github.nanmazino.chatrebuild.post.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.nanmazino.chatrebuild.chat.entity.ChatRoom;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMember;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMemberStatus;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomMemberRepository;
import io.github.nanmazino.chatrebuild.chat.repository.ChatRoomRepository;
import io.github.nanmazino.chatrebuild.global.security.JwtTokenProvider;
import io.github.nanmazino.chatrebuild.post.entity.Post;
import io.github.nanmazino.chatrebuild.post.entity.PostStatus;
import io.github.nanmazino.chatrebuild.post.repository.PostRepository;
import io.github.nanmazino.chatrebuild.support.IntegrationTestSupport;
import io.github.nanmazino.chatrebuild.user.entity.User;
import io.github.nanmazino.chatrebuild.user.repository.UserRepository;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostControllerTest extends IntegrationTestSupport {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User author;
    private User otherUser;

    @AfterEach
    void tearDown() {
        chatRoomMemberRepository.deleteAllInBatch();
        chatRoomRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @BeforeEach
    void setUp() {
        author = userRepository.save(new User(
            "author@example.com",
            passwordEncoder.encode("Password123!"),
            "author"
        ));

        otherUser = userRepository.save(new User(
            "other@example.com",
            passwordEncoder.encode("Password123!"),
            "other"
        ));
    }

    @Test
    @DisplayName("게시글 생성 성공 시 201과 공통 성공 응답을 반환한다")
    void createPostSuccess() throws Exception {
        String accessToken = jwtTokenProvider.generateAccessToken(author.getId(), author.getEmail());

        mockMvc.perform(post("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "강남역 저녁 같이 하실 분",
                      "content": "오늘 7시 가능하신 분 구합니다.",
                      "maxParticipants": 4
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.postId").isNumber())
            .andExpect(jsonPath("$.data.chatRoomId").isNumber())
            .andExpect(jsonPath("$.data.title").value("강남역 저녁 같이 하실 분"))
            .andExpect(jsonPath("$.data.status").value("OPEN"))
            .andExpect(jsonPath("$.data.author.userId").value(author.getId()))
            .andExpect(jsonPath("$.error").value(nullValue()));

        Post savedPost = postRepository.findAll().get(0);
        ChatRoom savedChatRoom = chatRoomRepository.findByPostId(savedPost.getId())
            .orElseThrow(() -> new AssertionError("게시글 생성 후 채팅방이 함께 생성되어야 합니다."));
        ChatRoomMember savedMember = chatRoomMemberRepository
            .findByRoomIdAndUserId(savedChatRoom.getId(), author.getId())
            .orElseThrow(() -> new AssertionError("게시글 작성자가 최초 채팅방 멤버로 저장되어야 합니다."));

        assertNotNull(savedChatRoom.getId());
        assertEquals(savedPost.getId(), savedChatRoom.getPost().getId());
        assertEquals(1, savedChatRoom.getMemberCount());
        assertNull(savedChatRoom.getLastMessageId());
        assertNull(savedChatRoom.getLastMessagePreview());
        assertNull(savedChatRoom.getLastMessageAt());
        assertEquals(ChatRoomMemberStatus.ACTIVE, savedMember.getStatus());
        assertNotNull(savedMember.getJoinedAt());
        assertNull(savedMember.getLeftAt());
        assertNull(savedMember.getLastReadMessageId());
        assertNull(savedMember.getLastReadAt());
    }

    @Test
    @DisplayName("게시글 목록 조회는 기본적으로 OPEN과 CLOSED만 반환한다")
    void getPostsReturnsOpenAndClosedByDefault() throws Exception {
        postRepository.save(new Post(author, "open-post", "content", 4, PostStatus.OPEN));
        postRepository.save(new Post(author, "closed-post", "content", 4, PostStatus.CLOSED));
        postRepository.save(new Post(author, "deleted-post", "content", 4, PostStatus.DELETED));

        mockMvc.perform(get("/api/posts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    @DisplayName("게시글 목록 조회는 비회원도 가능하고 DELETED 상태는 status 필터로도 노출되지 않는다")
    void getPostsAsGuestDoesNotExposeDeletedStatus() throws Exception {
        postRepository.save(new Post(author, "deleted-post", "content", 4, PostStatus.DELETED));

        mockMvc.perform(get("/api/posts")
                .param("status", "DELETED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items.length()").value(0))
            .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("게시글 목록 조회에서 page가 음수면 400과 공통 에러 응답을 반환한다")
    void getPostsRejectsNegativePage() throws Exception {
        mockMvc.perform(get("/api/posts")
                .param("page", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("page는 0 이상이어야 합니다."));
    }

    @Test
    @DisplayName("게시글 목록 조회에서 size가 0이면 400과 공통 에러 응답을 반환한다")
    void getPostsRejectsNonPositiveSize() throws Exception {
        mockMvc.perform(get("/api/posts")
                .param("size", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("size는 1 이상이어야 합니다."));
    }

    @Test
    @DisplayName("게시글 목록 조회에서 잘못된 status 값은 400과 공통 에러 응답을 반환한다")
    void getPostsRejectsInvalidStatus() throws Exception {
        mockMvc.perform(get("/api/posts")
                .param("status", "INVALID"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("status 값이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("게시글 상세 조회는 비회원도 가능하다")
    void getPostAsGuestSuccess() throws Exception {
        Post savedPost = createPostWithChatRoom("detail-title", "detail-content", PostStatus.OPEN);

        mockMvc.perform(get("/api/posts/" + savedPost.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.postId").value(savedPost.getId()))
            .andExpect(jsonPath("$.data.chatRoomId").isNumber())
            .andExpect(jsonPath("$.data.author.userId").value(author.getId()))
            .andExpect(jsonPath("$.data.author.nickname").value("author"));
    }

    @Test
    @DisplayName("삭제된 게시글 상세 조회는 404와 POST_NOT_FOUND를 반환한다")
    void getDeletedPostFails() throws Exception {
        Post deletedPost = createPostWithChatRoom("deleted-post", "content", PostStatus.DELETED);

        mockMvc.perform(get("/api/posts/" + deletedPost.getId()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("게시글 참여 성공 시 채팅방 ACTIVE 멤버로 등록된다")
    void joinPostSuccess() throws Exception {
        Post savedPost = createPostWithChatRoomAndAuthorMember("join-title", "content", PostStatus.OPEN);
        String accessToken = jwtTokenProvider.generateAccessToken(otherUser.getId(), otherUser.getEmail());

        mockMvc.perform(post("/api/posts/" + savedPost.getId() + "/join")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.postId").value(savedPost.getId()))
            .andExpect(jsonPath("$.data.chatRoomId").isNumber())
            .andExpect(jsonPath("$.data.memberStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.data.memberCount").value(2))
            .andExpect(jsonPath("$.data.joinedAt").isString())
            .andExpect(jsonPath("$.error").value(nullValue()));

        ChatRoom chatRoom = chatRoomRepository.findByPostId(savedPost.getId())
            .orElseThrow(() -> new AssertionError("게시글 참여 시 연결된 채팅방이 있어야 합니다."));
        ChatRoomMember joinedMember = chatRoomMemberRepository.findByRoomIdAndUserId(chatRoom.getId(), otherUser.getId())
            .orElseThrow(() -> new AssertionError("참여한 사용자가 채팅방 멤버로 저장되어야 합니다."));

        assertEquals(2, chatRoom.getMemberCount());
        assertEquals(ChatRoomMemberStatus.ACTIVE, joinedMember.getStatus());
        assertNotNull(joinedMember.getJoinedAt());
        assertNull(joinedMember.getLeftAt());
    }

    @Test
    @DisplayName("이미 ACTIVE 멤버인 사용자가 다시 참여하면 409를 반환한다")
    void joinPostFailsWhenAlreadyActive() throws Exception {
        Post savedPost = createPostWithChatRoomAndAuthorMember("join-title", "content", PostStatus.OPEN);
        String accessToken = jwtTokenProvider.generateAccessToken(author.getId(), author.getEmail());

        mockMvc.perform(post("/api/posts/" + savedPost.getId() + "/join")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("CHAT_MEMBER_ALREADY_ACTIVE"));
    }

    @Test
    @DisplayName("모집 종료된 게시글은 참여 시 409와 POST_ALREADY_CLOSED를 반환한다")
    void joinPostFailsWhenClosed() throws Exception {
        Post savedPost = createPostWithChatRoomAndAuthorMember("join-title", "content", PostStatus.CLOSED);
        String accessToken = jwtTokenProvider.generateAccessToken(otherUser.getId(), otherUser.getEmail());

        mockMvc.perform(post("/api/posts/" + savedPost.getId() + "/join")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("POST_ALREADY_CLOSED"));
    }

    @Test
    @DisplayName("삭제된 게시글은 참여 시 404와 POST_NOT_FOUND를 반환한다")
    void joinPostFailsWhenDeleted() throws Exception {
        Post savedPost = createPostWithChatRoomAndAuthorMember("join-title", "content", PostStatus.DELETED);
        String accessToken = jwtTokenProvider.generateAccessToken(otherUser.getId(), otherUser.getEmail());

        mockMvc.perform(post("/api/posts/" + savedPost.getId() + "/join")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("게시글 참여는 인증이 없으면 401을 반환한다")
    void joinPostUnauthorizedWithoutToken() throws Exception {
        Post savedPost = createPostWithChatRoomAndAuthorMember("join-title", "content", PostStatus.OPEN);

        mockMvc.perform(post("/api/posts/" + savedPost.getId() + "/join"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("LEFT 멤버가 다시 참여하면 기존 row를 ACTIVE로 복구한다")
    void joinPostRestoresLeftMember() throws Exception {
        Post savedPost = createPostWithChatRoomAndAuthorMember("join-title", "content", PostStatus.OPEN);
        ChatRoom chatRoom = chatRoomRepository.findByPostId(savedPost.getId())
            .orElseThrow(() -> new AssertionError("게시글에 연결된 채팅방이 있어야 합니다."));
        ChatRoomMember leftMember = chatRoomMemberRepository.save(new ChatRoomMember(
            chatRoom,
            otherUser,
            ChatRoomMemberStatus.LEFT,
            savedPost.getCreatedAt().minusDays(1)
        ));
        String accessToken = jwtTokenProvider.generateAccessToken(otherUser.getId(), otherUser.getEmail());

        mockMvc.perform(post("/api/posts/" + savedPost.getId() + "/join")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.memberStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.data.memberCount").value(2))
            .andExpect(jsonPath("$.data.joinedAt").isString());

        ChatRoomMember restoredMember = chatRoomMemberRepository.findById(leftMember.getId())
            .orElseThrow(() -> new AssertionError("기존 LEFT 멤버 row가 유지되어야 합니다."));

        assertEquals(leftMember.getId(), restoredMember.getId());
        assertEquals(ChatRoomMemberStatus.ACTIVE, restoredMember.getStatus());
        assertNotNull(restoredMember.getJoinedAt());
        assertNull(restoredMember.getLeftAt());
        assertEquals(2, chatRoomRepository.findById(chatRoom.getId())
            .orElseThrow(() -> new AssertionError("채팅방이 유지되어야 합니다."))
            .getMemberCount());
    }

    @Test
    @DisplayName("게시글 나가기 성공 시 멤버 상태를 LEFT로 변경하고 memberCount를 감소시킨다")
    void leavePostSuccess() throws Exception {
        Post savedPost = createPostWithChatRoomAndAuthorMember("leave-title", "content", PostStatus.OPEN);
        ChatRoom chatRoom = chatRoomRepository.findByPostId(savedPost.getId())
            .orElseThrow(() -> new AssertionError("게시글에 연결된 채팅방이 있어야 합니다."));
        chatRoomMemberRepository.save(new ChatRoomMember(
            chatRoom,
            otherUser,
            ChatRoomMemberStatus.ACTIVE,
            savedPost.getCreatedAt()
        ));
        chatRoom.increaseMemberCount();
        chatRoomRepository.saveAndFlush(chatRoom);
        String accessToken = jwtTokenProvider.generateAccessToken(otherUser.getId(), otherUser.getEmail());

        mockMvc.perform(post("/api/posts/" + savedPost.getId() + "/leave")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.postId").value(savedPost.getId()))
            .andExpect(jsonPath("$.data.chatRoomId").value(chatRoom.getId()))
            .andExpect(jsonPath("$.data.memberStatus").value("LEFT"))
            .andExpect(jsonPath("$.data.memberCount").value(1))
            .andExpect(jsonPath("$.data.leftAt").isString());

        ChatRoomMember leftMember = chatRoomMemberRepository.findByRoomIdAndUserId(chatRoom.getId(), otherUser.getId())
            .orElseThrow(() -> new AssertionError("나간 사용자의 멤버 row가 유지되어야 합니다."));

        assertEquals(ChatRoomMemberStatus.LEFT, leftMember.getStatus());
        assertNotNull(leftMember.getLeftAt());
        assertEquals(1, chatRoomRepository.findById(chatRoom.getId())
            .orElseThrow(() -> new AssertionError("채팅방이 유지되어야 합니다."))
            .getMemberCount());
    }

    @Test
    @DisplayName("비참여자가 게시글 나가기를 요청하면 404와 CHAT_MEMBER_NOT_FOUND를 반환한다")
    void leavePostFailsWhenMemberDoesNotExist() throws Exception {
        Post savedPost = createPostWithChatRoomAndAuthorMember("leave-title", "content", PostStatus.OPEN);
        String accessToken = jwtTokenProvider.generateAccessToken(otherUser.getId(), otherUser.getEmail());

        mockMvc.perform(post("/api/posts/" + savedPost.getId() + "/leave")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("CHAT_MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("정원이 가득 찬 게시글은 신규 참여 시 409와 CHAT_ROOM_FULL을 반환한다")
    void joinPostFailsWhenRoomIsFull() throws Exception {
        Post savedPost = createPostWithChatRoomAndAuthorMember("join-title", "content", PostStatus.OPEN);
        ChatRoom chatRoom = chatRoomRepository.findByPostId(savedPost.getId())
            .orElseThrow(() -> new AssertionError("게시글에 연결된 채팅방이 있어야 합니다."));
        Post fullPost = postRepository.findById(savedPost.getId())
            .orElseThrow(() -> new AssertionError("게시글이 있어야 합니다."));
        fullPost.update(fullPost.getTitle(), fullPost.getContent(), 1);
        postRepository.saveAndFlush(fullPost);
        String accessToken = jwtTokenProvider.generateAccessToken(otherUser.getId(), otherUser.getEmail());

        mockMvc.perform(post("/api/posts/" + savedPost.getId() + "/join")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("CHAT_ROOM_FULL"));
    }

    @Test
    @DisplayName("정원이 가득 찬 게시글은 LEFT 멤버 재참여도 409와 CHAT_ROOM_FULL을 반환한다")
    void joinPostFailsWhenRoomIsFullOnRejoin() throws Exception {
        Post savedPost = createPostWithChatRoomAndAuthorMember("join-title", "content", PostStatus.OPEN);
        ChatRoom chatRoom = chatRoomRepository.findByPostId(savedPost.getId())
            .orElseThrow(() -> new AssertionError("게시글에 연결된 채팅방이 있어야 합니다."));
        chatRoomMemberRepository.save(new ChatRoomMember(
            chatRoom,
            otherUser,
            ChatRoomMemberStatus.LEFT,
            savedPost.getCreatedAt().minusDays(1)
        ));
        Post fullPost = postRepository.findById(savedPost.getId())
            .orElseThrow(() -> new AssertionError("게시글이 있어야 합니다."));
        fullPost.update(fullPost.getTitle(), fullPost.getContent(), 1);
        postRepository.saveAndFlush(fullPost);
        String accessToken = jwtTokenProvider.generateAccessToken(otherUser.getId(), otherUser.getEmail());

        mockMvc.perform(post("/api/posts/" + savedPost.getId() + "/join")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("CHAT_ROOM_FULL"));
    }

    @Test
    @DisplayName("게시글 수정은 작성자만 가능하다")
    void updatePostForbiddenForNonAuthor() throws Exception {
        Post savedPost = postRepository.save(new Post(author, "before-title", "before-content", 4, PostStatus.OPEN));
        String accessToken = jwtTokenProvider.generateAccessToken(otherUser.getId(), otherUser.getEmail());

        mockMvc.perform(patch("/api/posts/" + savedPost.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "after-title",
                      "content": "after-content",
                      "maxParticipants": 5
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @DisplayName("게시글 모집 종료는 작성자만 가능하다")
    void closePostForbiddenForNonAuthor() throws Exception {
        Post savedPost = postRepository.save(new Post(author, "close-title", "close-content", 4, PostStatus.OPEN));
        String accessToken = jwtTokenProvider.generateAccessToken(otherUser.getId(), otherUser.getEmail());

        mockMvc.perform(patch("/api/posts/" + savedPost.getId() + "/close")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @DisplayName("게시글 모집 종료 성공 시 CLOSED 상태를 반환한다")
    void closePostSuccess() throws Exception {
        Post savedPost = postRepository.save(new Post(author, "close-title", "close-content", 4, PostStatus.OPEN));
        String accessToken = jwtTokenProvider.generateAccessToken(author.getId(), author.getEmail());

        mockMvc.perform(patch("/api/posts/" + savedPost.getId() + "/close")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("CLOSED"))
            .andExpect(jsonPath("$.data.closedAt").isString());
    }

    @Test
    @DisplayName("게시글 삭제는 작성자만 가능하다")
    void deletePostForbiddenForNonAuthor() throws Exception {
        Post savedPost = postRepository.save(new Post(author, "delete-title", "delete-content", 4, PostStatus.OPEN));
        String accessToken = jwtTokenProvider.generateAccessToken(otherUser.getId(), otherUser.getEmail());

        mockMvc.perform(delete("/api/posts/" + savedPost.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @DisplayName("게시글 삭제 후 목록과 상세에서 노출되지 않는다")
    void deletePostHidesPost() throws Exception {
        Post savedPost = postRepository.save(new Post(author, "delete-title", "delete-content", 4, PostStatus.OPEN));
        String accessToken = jwtTokenProvider.generateAccessToken(author.getId(), author.getEmail());

        mockMvc.perform(delete("/api/posts/" + savedPost.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("DELETED"));

        mockMvc.perform(get("/api/posts"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("delete-title"))));

        mockMvc.perform(get("/api/posts/" + savedPost.getId()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
    }

    private Post createPostWithChatRoom(String title, String content, PostStatus status) {
        Post post = postRepository.save(new Post(author, title, content, 4, status));
        chatRoomRepository.save(new ChatRoom(post));
        return post;
    }

    private Post createPostWithChatRoomAndAuthorMember(String title, String content, PostStatus status) {
        Post post = postRepository.save(new Post(author, title, content, 4, status));
        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(post));
        chatRoomMemberRepository.save(new ChatRoomMember(
            chatRoom,
            author,
            ChatRoomMemberStatus.ACTIVE,
            post.getCreatedAt()
        ));
        chatRoom.increaseMemberCount();
        chatRoomRepository.saveAndFlush(chatRoom);
        return post;
    }
}

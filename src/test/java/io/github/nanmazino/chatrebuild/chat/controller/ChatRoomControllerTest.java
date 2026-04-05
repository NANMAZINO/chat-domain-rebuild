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
    @DisplayName("ACTIVE 멤버는 메시지 히스토리를 최신순 cursor 응답으로 조회할 수 있다")
    void getMessagesSuccess() throws Exception {
        ChatRoom room = createRoomWithMembers(PostStatus.OPEN, true);
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
        ChatRoom room = createRoomWithMembers(PostStatus.OPEN, true);
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
        ChatRoom room = createRoomWithMembers(PostStatus.OPEN, true);
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
        ChatRoom room = createRoomWithMembers(PostStatus.OPEN, true);

        mockMvc.perform(get("/api/chat-rooms/" + room.getId() + "/messages"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("ACTIVE 멤버가 아니면 404와 CHAT_MEMBER_NOT_FOUND를 반환한다")
    void getMessagesFailsWhenUserIsNotActiveMember() throws Exception {
        ChatRoom room = createRoomWithMembers(PostStatus.OPEN, false);
        String accessToken = jwtTokenProvider.generateAccessToken(outsider.getId(), outsider.getEmail());

        mockMvc.perform(get("/api/chat-rooms/" + room.getId() + "/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("CHAT_MEMBER_NOT_FOUND"));
    }

    private ChatRoom createRoomWithMembers(PostStatus status, boolean includeActiveMember) {
        Post post = postRepository.save(new Post(author, "title", "content", 4, status));
        ChatRoom room = chatRoomRepository.save(new ChatRoom(post));

        chatRoomMemberRepository.save(new ChatRoomMember(
            room,
            author,
            ChatRoomMemberStatus.ACTIVE,
            post.getCreatedAt()
        ));
        room.increaseMemberCount();

        if (includeActiveMember) {
            chatRoomMemberRepository.save(new ChatRoomMember(
                room,
                activeMember,
                ChatRoomMemberStatus.ACTIVE,
                post.getCreatedAt()
            ));
            room.increaseMemberCount();
        }

        chatRoomRepository.saveAndFlush(room);
        return room;
    }
}

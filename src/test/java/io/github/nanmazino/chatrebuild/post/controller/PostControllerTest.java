package io.github.nanmazino.chatrebuild.post.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.nanmazino.chatrebuild.global.security.JwtTokenProvider;
import io.github.nanmazino.chatrebuild.post.entity.Post;
import io.github.nanmazino.chatrebuild.post.entity.PostStatus;
import io.github.nanmazino.chatrebuild.post.repository.PostRepository;
import io.github.nanmazino.chatrebuild.support.IntegrationTestSupport;
import io.github.nanmazino.chatrebuild.user.entity.User;
import io.github.nanmazino.chatrebuild.user.repository.UserRepository;
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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User author;
    private User otherUser;

    @BeforeEach
    void setUp() {
        postRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

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
            .andExpect(jsonPath("$.data.title").value("강남역 저녁 같이 하실 분"))
            .andExpect(jsonPath("$.data.status").value("OPEN"))
            .andExpect(jsonPath("$.data.author.userId").value(author.getId()))
            .andExpect(jsonPath("$.error").value(nullValue()));
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
    @DisplayName("삭제된 게시글 상세 조회는 404와 POST_NOT_FOUND를 반환한다")
    void getDeletedPostFails() throws Exception {
        Post deletedPost = postRepository.save(new Post(author, "deleted-post", "content", 4, PostStatus.DELETED));

        mockMvc.perform(get("/api/posts/" + deletedPost.getId()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
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
}

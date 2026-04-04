package io.github.nanmazino.chatrebuild.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.nanmazino.chatrebuild.support.IntegrationTestSupport;
import io.github.nanmazino.chatrebuild.post.repository.PostRepository;
import io.github.nanmazino.chatrebuild.user.entity.User;
import io.github.nanmazino.chatrebuild.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        postRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("로그인 성공 시 200과 토큰 응답을 반환한다")
    void loginSuccess() throws Exception {
        userRepository.save(new User(
            "user@example.com",
            passwordEncoder.encode("Password123!"),
            "nanmazino"
        ));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "user@example.com",
                      "password": "Password123!"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").isString())
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.user.userId").isNumber())
            .andExpect(jsonPath("$.data.user.email").value("user@example.com"))
            .andExpect(jsonPath("$.data.user.nickname").value("nanmazino"))
            .andExpect(jsonPath("$.error").value(nullValue()))
            .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("로그인 요청 검증에 실패하면 400과 공통 에러 응답을 반환한다")
    void loginFailsWhenRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "invalid-email",
                      "password": ""
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").value(nullValue()))
            .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").isString())
            .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("존재하지 않는 이메일이면 401과 공통 에러 응답을 반환한다")
    void loginFailsWhenEmailDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "missing@example.com",
                      "password": "Password123!"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").value(nullValue()))
            .andExpect(jsonPath("$.error.code").value("LOGIN_INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.error.message").value("이메일 또는 비밀번호가 올바르지 않습니다."))
            .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 401과 공통 에러 응답을 반환한다")
    void loginFailsWhenPasswordIsInvalid() throws Exception {
        userRepository.save(new User(
            "user@example.com",
            passwordEncoder.encode("Password123!"),
            "nanmazino"
        ));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "user@example.com",
                      "password": "WrongPassword123!"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").value(nullValue()))
            .andExpect(jsonPath("$.error.code").value("LOGIN_INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.error.message").value("이메일 또는 비밀번호가 올바르지 않습니다."))
            .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("로그인 API가 OpenAPI 문서에 노출된다")
    void loginApiIsExposedInOpenApiDocs() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"/api/auth/login\"")))
            .andExpect(content().string(containsString("\"summary\":\"로그인\"")));
    }
}

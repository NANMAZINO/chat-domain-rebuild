package io.github.nanmazino.chatrebuild.user.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.nanmazino.chatrebuild.support.IntegrationTestSupport;
import io.github.nanmazino.chatrebuild.user.entity.User;
import io.github.nanmazino.chatrebuild.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("회원가입 성공 시 201과 공통 성공 응답을 반환한다")
    void signupSuccess() throws Exception {
        mockMvc.perform(post("/api/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "user@example.com",
                      "password": "Password123!",
                      "nickname": "nanmazino"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.userId").isNumber())
            .andExpect(jsonPath("$.data.email").value("user@example.com"))
            .andExpect(jsonPath("$.data.nickname").value("nanmazino"))
            .andExpect(jsonPath("$.error").value(nullValue()))
            .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("회원가입 요청 검증에 실패하면 400과 공통 에러 응답을 반환한다")
    void signupFailsWhenRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/api/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "invalid-email",
                      "password": "1234",
                      "nickname": ""
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
    @DisplayName("비밀번호 정책을 만족하지 않으면 400과 공통 에러 응답을 반환한다")
    void signupFailsWhenPasswordPolicyIsInvalid() throws Exception {
        mockMvc.perform(post("/api/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "user@example.com",
                      "password": "Password123",
                      "nickname": "nanmazino"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").value(nullValue()))
            .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("비밀번호는 영문, 숫자, 특수문자를 포함해야 합니다."))
            .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("중복 이메일이면 409와 공통 에러 응답을 반환한다")
    void signupFailsWhenEmailDuplicated() throws Exception {
        userRepository.save(new User("user@example.com", "encoded-password", "existing-user"));

        mockMvc.perform(post("/api/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "user@example.com",
                      "password": "Password123!",
                      "nickname": "new-user"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").value(nullValue()))
            .andExpect(jsonPath("$.error.code").value("USER_EMAIL_DUPLICATED"))
            .andExpect(jsonPath("$.error.message").value("이미 사용 중인 이메일입니다."))
            .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("중복 닉네임이면 409와 공통 에러 응답을 반환한다")
    void signupFailsWhenNicknameDuplicated() throws Exception {
        userRepository.save(new User("existing@example.com", "encoded-password", "nanmazino"));

        mockMvc.perform(post("/api/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "new@example.com",
                      "password": "Password123!",
                      "nickname": "nanmazino"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").value(nullValue()))
            .andExpect(jsonPath("$.error.code").value("USER_NICKNAME_DUPLICATED"))
            .andExpect(jsonPath("$.error.message").value("이미 사용 중인 닉네임입니다."))
            .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("회원가입 API가 OpenAPI 문서에 노출된다")
    void signupApiIsExposedInOpenApiDocs() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"/api/users/signup\"")))
            .andExpect(content().string(containsString("\"summary\":\"회원가입\"")));
    }
}

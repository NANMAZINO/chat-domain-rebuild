package io.github.nanmazino.chatrebuild.global.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import io.github.nanmazino.chatrebuild.support.IntegrationTestSupport;
import io.github.nanmazino.chatrebuild.user.entity.User;
import io.github.nanmazino.chatrebuild.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SecurityTestApi.class)
class SecurityIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JwtProperties jwtProperties;

    private User savedUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAllInBatch();
        savedUser = userRepository.save(new User(
            "secure-user@example.com",
            passwordEncoder.encode("Password123!"),
            "secure-user"
        ));
    }

    @Test
    @DisplayName("인증 헤더 없이 보호 API에 접근하면 401과 공통 에러 응답을 반환한다")
    void protectedEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/test/protected"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").value(nullValue()))
            .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"))
            .andExpect(jsonPath("$.error.message").value("인증이 필요합니다."))
            .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("유효하지 않은 JWT로 보호 API에 접근하면 401과 공통 에러 응답을 반환한다")
    void protectedEndpointRejectsInvalidToken() throws Exception {
        assertUnauthorized(mockMvc.perform(get("/api/test/protected")
            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")));
    }

    @Test
    @DisplayName("유효한 JWT로 보호 API에 접근하면 인증된 사용자 정보를 사용할 수 있다")
    void protectedEndpointAllowsValidToken() throws Exception {
        String accessToken = jwtTokenProvider.generateAccessToken(savedUser.getId(), savedUser.getEmail());

        mockMvc.perform(get("/api/test/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(savedUser.getId()))
            .andExpect(jsonPath("$.email").value(savedUser.getEmail()))
            .andExpect(jsonPath("$.nickname").value(savedUser.getNickname()));
    }

    @Test
    @DisplayName("유효한 형식의 JWT라도 사용자가 없으면 401과 공통 에러 응답을 반환한다")
    void protectedEndpointRejectsTokenWhenUserNoLongerExists() throws Exception {
        String accessToken = jwtTokenProvider.generateAccessToken(savedUser.getId(), savedUser.getEmail());
        userRepository.deleteById(savedUser.getId());

        assertUnauthorized(mockMvc.perform(get("/api/test/protected")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)));
    }

    @Test
    @DisplayName("서명은 유효하지만 subject가 숫자가 아닌 JWT는 401과 공통 에러 응답을 반환한다")
    void protectedEndpointRejectsTokenWithInvalidSubject() throws Exception {
        String accessToken = generateSignedToken("not-a-number");

        assertUnauthorized(mockMvc.perform(get("/api/test/protected")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)));
    }

    @Test
    @DisplayName("권한이 부족한 사용자가 관리자 전용 API에 접근하면 403과 공통 에러 응답을 반환한다")
    void adminEndpointReturnsForbiddenForUserRole() throws Exception {
        String accessToken = jwtTokenProvider.generateAccessToken(savedUser.getId(), savedUser.getEmail());

        assertForbidden(mockMvc.perform(get("/api/test/admin")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)));
    }

    @Test
    @DisplayName("게시글 목록 조회는 인증 없이 접근할 수 있다")
    void postListEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/posts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.postCount").value(0));
    }

    @Test
    @DisplayName("게시글 상세 조회는 인증 없이 접근할 수 있다")
    void postDetailEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/posts/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.postId").value(1L));
    }

    @Test
    @DisplayName("게시글 모집 종료는 인증 없이 접근하면 401과 공통 에러 응답을 반환한다")
    void postCloseEndpointRequiresAuthentication() throws Exception {
        assertUnauthorized(mockMvc.perform(patch("/api/posts/1/close")));
    }

    @Test
    @DisplayName("게시글 삭제는 인증 없이 접근하면 401과 공통 에러 응답을 반환한다")
    void postDeleteEndpointRequiresAuthentication() throws Exception {
        assertUnauthorized(mockMvc.perform(delete("/api/posts/1")));
    }

    @Test
    @DisplayName("유효한 JWT가 있으면 게시글 모집 종료에 접근할 수 있다")
    void postCloseEndpointAllowsAuthenticatedUser() throws Exception {
        String accessToken = jwtTokenProvider.generateAccessToken(savedUser.getId(), savedUser.getEmail());

        mockMvc.perform(patch("/api/posts/1/close")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.postId").value(1L))
            .andExpect(jsonPath("$.closedBy").value(savedUser.getId()));
    }

    @Test
    @DisplayName("유효한 JWT가 있으면 게시글 삭제에 접근할 수 있다")
    void postDeleteEndpointAllowsAuthenticatedUser() throws Exception {
        String accessToken = jwtTokenProvider.generateAccessToken(savedUser.getId(), savedUser.getEmail());

        mockMvc.perform(delete("/api/posts/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.postId").value(1L))
            .andExpect(jsonPath("$.deletedBy").value(savedUser.getId()));
    }

    @Test
    @DisplayName("회원가입과 로그인 경로는 인증 없이 계속 접근할 수 있다")
    void publicAuthEndpointsRemainAccessible() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "",
                      "password": ""
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"));

        mockMvc.perform(post("/api/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "",
                      "password": "",
                      "nickname": ""
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Swagger 문서 경로는 인증 없이 접근할 수 있다")
    void swaggerEndpointsRemainAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"openapi\"")));
    }

    private String generateSignedToken(String subject) {
        SecretKey signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret()));
        Instant issuedAt = Instant.now();

        return Jwts.builder()
            .subject(subject)
            .claim("email", savedUser.getEmail())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(issuedAt.plusSeconds(jwtProperties.accessTokenExpirationSeconds())))
            .signWith(signingKey)
            .compact();
    }

    private void assertUnauthorized(ResultActions resultActions) throws Exception {
        resultActions
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").value(nullValue()))
            .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"))
            .andExpect(jsonPath("$.error.message").value("인증이 필요합니다."))
            .andExpect(jsonPath("$.timestamp").isString());
    }

    private void assertForbidden(ResultActions resultActions) throws Exception {
        resultActions
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").value(nullValue()))
            .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"))
            .andExpect(jsonPath("$.error.message").value("접근 권한이 없습니다."))
            .andExpect(jsonPath("$.timestamp").isString());
    }
}

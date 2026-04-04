package io.github.nanmazino.chatrebuild.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nanmazino.chatrebuild.auth.dto.request.LoginRequest;
import io.github.nanmazino.chatrebuild.auth.dto.response.LoginResponse;
import io.github.nanmazino.chatrebuild.auth.exception.InvalidLoginCredentialsException;
import io.github.nanmazino.chatrebuild.global.security.JwtProperties;
import io.github.nanmazino.chatrebuild.post.repository.PostRepository;
import io.github.nanmazino.chatrebuild.support.IntegrationTestSupport;
import io.github.nanmazino.chatrebuild.user.entity.User;
import io.github.nanmazino.chatrebuild.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class AuthServiceTest extends IntegrationTestSupport {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        postRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("로그인에 성공하면 JWT와 사용자 정보를 반환한다")
    void loginSuccess() {
        User user = userRepository.save(new User(
            "user@example.com",
            passwordEncoder.encode("Password123!"),
            "nanmazino"
        ));

        LoginResponse response = authService.login(new LoginRequest("user@example.com", "Password123!"));

        Claims claims = Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret())))
            .build()
            .parseSignedClaims(response.accessToken())
            .getPayload();

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().userId()).isEqualTo(user.getId());
        assertThat(response.user().email()).isEqualTo("user@example.com");
        assertThat(response.user().nickname()).isEqualTo("nanmazino");
        assertThat(claims.getSubject()).isEqualTo(String.valueOf(user.getId()));
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    @DisplayName("존재하지 않는 이메일이면 로그인에 실패한다")
    void loginFailsWhenEmailDoesNotExist() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("missing@example.com", "Password123!")))
            .isInstanceOf(InvalidLoginCredentialsException.class)
            .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 로그인에 실패한다")
    void loginFailsWhenPasswordIsInvalid() {
        userRepository.save(new User(
            "user@example.com",
            passwordEncoder.encode("Password123!"),
            "nanmazino"
        ));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "WrongPassword123!")))
            .isInstanceOf(InvalidLoginCredentialsException.class)
            .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
    }
}

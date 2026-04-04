package io.github.nanmazino.chatrebuild.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nanmazino.chatrebuild.support.IntegrationTestSupport;
import io.github.nanmazino.chatrebuild.user.dto.request.SignUpRequest;
import io.github.nanmazino.chatrebuild.user.dto.response.SignUpResponse;
import io.github.nanmazino.chatrebuild.user.entity.User;
import io.github.nanmazino.chatrebuild.user.exception.DuplicateEmailException;
import io.github.nanmazino.chatrebuild.user.exception.DuplicateNicknameException;
import io.github.nanmazino.chatrebuild.user.repository.UserRepository;
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
class UserServiceTest extends IntegrationTestSupport {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("회원가입에 성공하면 사용자 정보를 저장하고 응답을 반환한다")
    void signUpSuccess() {
        SignUpRequest request = new SignUpRequest(
            "user@example.com",
            "Password123!",
            "nanmazino"
        );

        SignUpResponse response = userService.signUp(request);

        User savedUser = userRepository.findById(response.userId())
            .orElseThrow();

        assertThat(response.userId()).isNotNull();
        assertThat(response.email()).isEqualTo(request.email());
        assertThat(response.nickname()).isEqualTo(request.nickname());
        assertThat(savedUser.getEmail()).isEqualTo(request.email());
        assertThat(savedUser.getNickname()).isEqualTo(request.nickname());
        assertThat(savedUser.getPassword()).isNotEqualTo(request.password());
        assertThat(passwordEncoder.matches(request.password(), savedUser.getPassword())).isTrue();
    }

    @Test
    @DisplayName("이미 사용 중인 이메일이면 회원가입에 실패한다")
    void signUpFailsWhenEmailDuplicated() {
        userRepository.save(new User("user@example.com", "encoded-password", "first-user"));

        SignUpRequest request = new SignUpRequest(
            "user@example.com",
            "Password123!",
            "second-user"
        );

        assertThatThrownBy(() -> userService.signUp(request))
            .isInstanceOf(DuplicateEmailException.class)
            .hasMessage("이미 사용 중인 이메일입니다.");
    }

    @Test
    @DisplayName("이미 사용 중인 닉네임이면 회원가입에 실패한다")
    void signUpFailsWhenNicknameDuplicated() {
        userRepository.save(new User("first@example.com", "encoded-password", "nanmazino"));

        SignUpRequest request = new SignUpRequest(
            "second@example.com",
            "Password123!",
            "nanmazino"
        );

        assertThatThrownBy(() -> userService.signUp(request))
            .isInstanceOf(DuplicateNicknameException.class)
            .hasMessage("이미 사용 중인 닉네임입니다.");
    }
}

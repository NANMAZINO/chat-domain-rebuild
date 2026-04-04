package io.github.nanmazino.chatrebuild.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nanmazino.chatrebuild.global.config.JpaAuditingConfig;
import io.github.nanmazino.chatrebuild.support.IntegrationTestSupport;
import io.github.nanmazino.chatrebuild.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class UserRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("사용자를 저장한 뒤 이메일로 조회할 수 있다")
    void saveAndFindByEmail() {
        User user = new User("user@example.com", "encoded-password", "nanmazino");

        User savedUser = userRepository.save(user);

        entityManager.flush();
        entityManager.clear();

        User foundUser = userRepository.findByEmail("user@example.com")
                .orElseThrow();

        assertThat(foundUser.getId()).isEqualTo(savedUser.getId());
        assertThat(foundUser.getNickname()).isEqualTo("nanmazino");
        assertThat(foundUser.getCreatedAt()).isNotNull();
        assertThat(foundUser.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("중복 이메일은 저장할 수 없다")
    void duplicateEmailFails() {
        userRepository.save(new User("user@example.com", "encoded-password", "user1"));

        assertThatThrownBy(() -> userRepository.save(new User("user@example.com", "encoded-password", "user2")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("중복 닉네임은 저장할 수 없다")
    void duplicateNicknameFails() {
        userRepository.save(new User("first@example.com", "encoded-password", "nanmazino"));

        assertThatThrownBy(() -> userRepository.save(new User("second@example.com", "encoded-password", "nanmazino")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}

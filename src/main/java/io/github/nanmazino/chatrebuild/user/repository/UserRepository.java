package io.github.nanmazino.chatrebuild.user.repository;

import io.github.nanmazino.chatrebuild.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    @Query("""
        select user.nickname
        from User user
        where user.id = :userId
        """)
    Optional<String> findNicknameById(@Param("userId") Long userId);
}

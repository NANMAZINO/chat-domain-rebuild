package io.github.nanmazino.chatrebuild.chat.repository;

import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMember;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMemberStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    Optional<ChatRoomMember> findByRoomIdAndUserId(Long roomId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select member
        from ChatRoomMember member
        where member.room.id = :roomId
          and member.user.id = :userId
          and member.status = :status
        """)
    Optional<ChatRoomMember> findByRoomIdAndUserIdAndStatusForUpdate(
        @Param("roomId") Long roomId,
        @Param("userId") Long userId,
        @Param("status") ChatRoomMemberStatus status
    );

    boolean existsByRoomIdAndUserIdAndStatus(Long roomId, Long userId, ChatRoomMemberStatus status);

    long countByRoomIdAndStatus(Long roomId, ChatRoomMemberStatus status);
}

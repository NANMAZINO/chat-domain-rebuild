package io.github.nanmazino.chatrebuild.chat.repository;

import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMember;
import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    Optional<ChatRoomMember> findByRoomIdAndUserId(Long roomId, Long userId);

    long countByRoomIdAndStatus(Long roomId, ChatRoomMemberStatus status);

    @Query("""
        select member
        from ChatRoomMember member
        join fetch member.room room
        join fetch room.post post
        where member.user.id = :userId
          and member.status = :status
          and (
            :keyword is null
            or lower(post.title) like lower(concat('%', :keyword, '%'))
          )
        """)
    List<ChatRoomMember> findAllByUserIdAndStatusWithRoomAndPost(
        @Param("userId") Long userId,
        @Param("status") ChatRoomMemberStatus status,
        @Param("keyword") String keyword
    );
}

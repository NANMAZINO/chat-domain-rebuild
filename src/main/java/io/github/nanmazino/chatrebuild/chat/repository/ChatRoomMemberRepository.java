package io.github.nanmazino.chatrebuild.chat.repository;

import io.github.nanmazino.chatrebuild.chat.entity.ChatRoomMember;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    Optional<ChatRoomMember> findByRoomIdAndUserId(Long roomId, Long userId);
}

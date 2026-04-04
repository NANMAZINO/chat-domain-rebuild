package io.github.nanmazino.chatrebuild.chat.repository;

import io.github.nanmazino.chatrebuild.chat.entity.ChatRoom;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByPostId(Long postId);
}

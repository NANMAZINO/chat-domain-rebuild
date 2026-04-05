package io.github.nanmazino.chatrebuild.chat.repository;

import io.github.nanmazino.chatrebuild.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    long countByRoomId(Long roomId);
}

package io.github.nanmazino.chatrebuild.chat.repository;

import io.github.nanmazino.chatrebuild.chat.entity.ChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    long countByRoomId(Long roomId);

    long countByRoomIdAndIdGreaterThan(Long roomId, Long messageId);

    @Query("""
        select message
        from ChatMessage message
        join fetch message.sender
        where message.room.id = :roomId
        order by message.id desc
        """)
    List<ChatMessage> findRecentMessages(@Param("roomId") Long roomId, org.springframework.data.domain.Pageable pageable);

    @Query("""
        select message
        from ChatMessage message
        join fetch message.sender
        where message.room.id = :roomId
          and message.id <= (
            select cursor.id
            from ChatMessage cursor
            where cursor.id = :cursorMessageId
              and cursor.room.id = :roomId
          )
        order by message.id desc
        """)
    List<ChatMessage> findRecentMessagesAtOrBeforeCursor(
        @Param("roomId") Long roomId,
        @Param("cursorMessageId") Long cursorMessageId,
        org.springframework.data.domain.Pageable pageable
    );
}

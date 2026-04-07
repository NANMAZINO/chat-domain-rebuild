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
        select
            message.id as messageId,
            sender.id as senderId,
            sender.nickname as senderNickname,
            message.content as content,
            message.type as type,
            message.createdAt as createdAt
        from ChatMessage message
        join message.sender sender
        where message.room.id = :roomId
        order by message.id desc
        """)
    List<ChatMessageHistoryProjection> findRecentMessageHistory(
        @Param("roomId") Long roomId,
        org.springframework.data.domain.Pageable pageable
    );

    @Query("""
        select case when count(cursor) > 0 then true else false end
        from ChatMessage cursor
        where cursor.id = :cursorMessageId
          and cursor.room.id = :roomId
        """)
    boolean existsCursorInRoom(
        @Param("roomId") Long roomId,
        @Param("cursorMessageId") Long cursorMessageId
    );

    @Query("""
        select message
        from ChatMessage message
        join fetch message.sender
        where message.room.id = :roomId
          and message.id < :cursorMessageId
        order by message.id desc
        """)
    List<ChatMessage> findRecentMessagesBeforeCursor(
        @Param("roomId") Long roomId,
        @Param("cursorMessageId") Long cursorMessageId,
        org.springframework.data.domain.Pageable pageable
    );

    @Query("""
        select
            message.id as messageId,
            sender.id as senderId,
            sender.nickname as senderNickname,
            message.content as content,
            message.type as type,
            message.createdAt as createdAt
        from ChatMessage message
        join message.sender sender
        where message.room.id = :roomId
          and message.id < :cursorMessageId
        order by message.id desc
        """)
    List<ChatMessageHistoryProjection> findRecentMessageHistoryBeforeCursor(
        @Param("roomId") Long roomId,
        @Param("cursorMessageId") Long cursorMessageId,
        org.springframework.data.domain.Pageable pageable
    );
}

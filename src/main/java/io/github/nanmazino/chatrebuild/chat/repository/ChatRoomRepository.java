package io.github.nanmazino.chatrebuild.chat.repository;

import io.github.nanmazino.chatrebuild.chat.entity.ChatRoom;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByPostId(Long postId);

    @Query(value = """
        select *
        from chat_rooms
        where post_id = :postId
        for update
        """, nativeQuery = true)
    Optional<ChatRoom> findByPostIdForUpdate(Long postId);
}

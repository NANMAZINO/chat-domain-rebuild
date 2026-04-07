package io.github.nanmazino.chatrebuild.chat.cache;

import io.github.nanmazino.chatrebuild.chat.dto.response.ChatRoomDetailResponse;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatCacheRepository {

    private static final Duration ROOM_SUMMARY_TTL = Duration.ofMinutes(10);
    private static final String ROOM_SUMMARY_KEY_PREFIX = "chat:room-summary:";

    private final RedisTemplate<String, ChatRoomDetailResponse> chatRoomSummaryRedisTemplate;

    public Optional<ChatRoomDetailResponse> findRoomSummary(Long roomId) {
        return Optional.ofNullable(chatRoomSummaryRedisTemplate.opsForValue().get(roomSummaryKey(roomId)));
    }

    public void saveRoomSummary(Long roomId, ChatRoomDetailResponse response) {
        chatRoomSummaryRedisTemplate.opsForValue().set(roomSummaryKey(roomId), response, ROOM_SUMMARY_TTL);
    }

    public void evictRoomSummary(Long roomId) {
        chatRoomSummaryRedisTemplate.delete(roomSummaryKey(roomId));
    }

    private String roomSummaryKey(Long roomId) {
        return ROOM_SUMMARY_KEY_PREFIX + roomId;
    }
}

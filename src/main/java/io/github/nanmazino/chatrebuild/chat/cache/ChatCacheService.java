package io.github.nanmazino.chatrebuild.chat.cache;

import io.github.nanmazino.chatrebuild.chat.dto.response.ChatRoomDetailResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class ChatCacheService {

    private static final Logger log = LoggerFactory.getLogger(ChatCacheService.class);

    private final ChatCacheRepository chatCacheRepository;

    public Optional<ChatRoomDetailResponse> findRoomSummary(Long roomId) {
        try {
            return chatCacheRepository.findRoomSummary(roomId);
        } catch (RuntimeException exception) {
            log.warn("Redis room summary cache read failed. Falling back to DB. roomId={}", roomId, exception);
            return Optional.empty();
        }
    }

    public void saveRoomSummary(Long roomId, ChatRoomDetailResponse summary) {
        try {
            chatCacheRepository.saveRoomSummary(roomId, summary);
        } catch (RuntimeException exception) {
            log.warn("Redis room summary cache write failed. Returning DB result. roomId={}", roomId, exception);
        }
    }

    public void evictRoomSummaryAfterCommit(Long roomId) {
        runAfterCommit(() -> evictRoomSummary(roomId));
    }

    public void evictRoomSummary(Long roomId) {
        try {
            chatCacheRepository.evictRoomSummary(roomId);
        } catch (RuntimeException exception) {
            log.warn("Redis room summary cache eviction failed. Keeping DB commit. roomId={}", roomId, exception);
        }
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}

package io.github.nanmazino.chatrebuild.chat.cache;

import io.github.nanmazino.chatrebuild.chat.dto.response.ChatRoomDetailResponse;
import java.util.Optional;
import java.util.function.Supplier;
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

    public ChatRoomDetailResponse getOrLoadRoomSummary(Long roomId, Supplier<ChatRoomDetailResponse> loader) {
        try {
            Optional<ChatRoomDetailResponse> cachedSummary = chatCacheRepository.findRoomSummary(roomId);
            if (cachedSummary.isPresent()) {
                return cachedSummary.get();
            }
        } catch (RuntimeException exception) {
            log.warn("Redis room summary cache read failed. Falling back to DB. roomId={}", roomId, exception);
        }

        ChatRoomDetailResponse loadedSummary = loader.get();

        try {
            chatCacheRepository.saveRoomSummary(roomId, loadedSummary);
        } catch (RuntimeException exception) {
            log.warn("Redis room summary cache write failed. Returning DB result. roomId={}", roomId, exception);
        }

        return loadedSummary;
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

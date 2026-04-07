package io.github.nanmazino.chatrebuild.chat.repository;

import io.github.nanmazino.chatrebuild.chat.entity.ChatMessageType;
import java.time.LocalDateTime;

public interface ChatMessageHistoryProjection {

    Long getMessageId();

    Long getSenderId();

    String getSenderNickname();

    String getContent();

    ChatMessageType getType();

    LocalDateTime getCreatedAt();
}

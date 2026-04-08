package io.github.nanmazino.chatrebuild.chat.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatReadRequest(
    @NotNull(message = "lastReadMessageId가 올바르지 않습니다.")
    @Positive(message = "lastReadMessageId가 올바르지 않습니다.")
    Long lastReadMessageId
) {
}

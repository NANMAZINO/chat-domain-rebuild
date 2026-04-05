package io.github.nanmazino.chatrebuild.chat.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.nanmazino.chatrebuild.chat.entity.ChatMessageType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatSendRequest(
    @NotBlank(message = "메시지 내용은 필수입니다.")
    String content,

    @NotNull(message = "메시지 타입은 필수입니다.")
    ChatMessageType type
) {

    @AssertTrue(message = "메시지 타입은 TEXT만 보낼 수 있습니다.")
    public boolean isClientSendTypeValid() {
        return type == null || type == ChatMessageType.TEXT;
    }
}

package io.github.nanmazino.chatrebuild.chat.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nanmazino.chatrebuild.chat.dto.request.ChatSendRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatSendPayloadValidator {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public void validate(Message<?> message) {
        ChatSendRequest request;

        try {
            request = objectMapper.readValue(extractPayloadBytes(message), ChatSendRequest.class);
        } catch (IOException exception) {
            throw new MessageConversionException("메시지 payload를 해석할 수 없습니다.", exception);
        }

        Set<ConstraintViolation<ChatSendRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private byte[] extractPayloadBytes(Message<?> message) {
        Object payload = message.getPayload();

        if (payload instanceof byte[] bytes) {
            return bytes;
        }

        if (payload instanceof String text) {
            return text.getBytes(StandardCharsets.UTF_8);
        }

        throw new MessageConversionException("지원하지 않는 메시지 payload 타입입니다: " + payload.getClass().getName());
    }
}

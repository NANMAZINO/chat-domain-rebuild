package io.github.nanmazino.chatrebuild.global.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nanmazino.chatrebuild.global.exception.ErrorCode;
import io.github.nanmazino.chatrebuild.global.response.ApiResponse;
import io.github.nanmazino.chatrebuild.global.response.ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

@Component("stompSubProtocolErrorHandler")
@RequiredArgsConstructor
public class StompErrorHandler extends StompSubProtocolErrorHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable exception) {
        ErrorCode errorCode = resolveErrorCode(clientMessage, exception);
        byte[] payload = serialize(errorCode);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(errorCode.getMessage());
        accessor.setContentType(MimeTypeUtils.APPLICATION_JSON);
        accessor.setContentLength(payload.length);
        accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(payload, accessor.getMessageHeaders());
    }

    private ErrorCode resolveErrorCode(Message<byte[]> clientMessage, Throwable exception) {
        for (Throwable cause = exception; cause != null && cause.getCause() != cause; cause = cause.getCause()) {
            if (cause instanceof AuthenticationCredentialsNotFoundException) {
                return ErrorCode.AUTH_UNAUTHORIZED;
            }

            if (cause instanceof AccessDeniedException) {
                return ErrorCode.AUTH_FORBIDDEN;
            }
        }

        if (isUnauthenticatedSessionFrame(clientMessage, exception)) {
            return ErrorCode.AUTH_UNAUTHORIZED;
        }

        return ErrorCode.INTERNAL_SERVER_ERROR;
    }

    private boolean isUnauthenticatedSessionFrame(Message<byte[]> clientMessage, Throwable exception) {
        StompHeaderAccessor accessor = clientMessage == null
            ? null
            : StompHeaderAccessor.wrap(clientMessage);

        if (accessor == null || (accessor.getCommand() != StompCommand.SUBSCRIBE && accessor.getCommand() != StompCommand.SEND)) {
            return false;
        }

        // Spring rejects SUBSCRIBE/SEND before CONNECT as an unknown STOMP session.
        for (Throwable cause = exception; cause != null && cause.getCause() != cause; cause = cause.getCause()) {
            if (cause instanceof IllegalStateException
                && cause.getMessage() != null
                && cause.getMessage().startsWith("Unknown session:")) {
                return true;
            }
        }

        return false;
    }

    private byte[] serialize(ErrorCode errorCode) {
        try {
            return objectMapper.writeValueAsBytes(
                ApiResponse.failure(ErrorResponse.from(errorCode, errorCode.getMessage()))
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("STOMP 에러 응답 직렬화에 실패했습니다.", exception);
        }
    }
}

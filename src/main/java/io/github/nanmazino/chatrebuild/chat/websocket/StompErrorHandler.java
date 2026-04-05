package io.github.nanmazino.chatrebuild.chat.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nanmazino.chatrebuild.global.exception.CustomException;
import io.github.nanmazino.chatrebuild.global.exception.ErrorCode;
import io.github.nanmazino.chatrebuild.global.response.ApiResponse;
import io.github.nanmazino.chatrebuild.global.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
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
        ResolvedError resolvedError = resolveError(clientMessage, exception);
        byte[] payload = serialize(resolvedError);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(resolvedError.message());
        accessor.setContentType(MimeTypeUtils.APPLICATION_JSON);
        accessor.setContentLength(payload.length);
        accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(payload, accessor.getMessageHeaders());
    }

    private ResolvedError resolveError(Message<byte[]> clientMessage, Throwable exception) {
        for (Throwable cause = exception; cause != null && cause.getCause() != cause; cause = cause.getCause()) {
            if (cause instanceof AuthenticationCredentialsNotFoundException) {
                return ResolvedError.from(ErrorCode.AUTH_UNAUTHORIZED);
            }

            if (cause instanceof AccessDeniedException) {
                return ResolvedError.from(ErrorCode.AUTH_FORBIDDEN);
            }

            if (cause instanceof MethodArgumentNotValidException validationException) {
                String message = validationException.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .findFirst()
                    .map(fieldError -> fieldError.getDefaultMessage())
                    .orElseGet(() -> validationException.getBindingResult()
                        .getAllErrors()
                        .stream()
                        .findFirst()
                        .map(error -> error.getDefaultMessage())
                        .orElse(ErrorCode.COMMON_VALIDATION_ERROR.getMessage()));

                return new ResolvedError(ErrorCode.COMMON_VALIDATION_ERROR, message);
            }

            if (cause instanceof ConstraintViolationException validationException) {
                String message = validationException.getConstraintViolations()
                    .stream()
                    .findFirst()
                    .map(violation -> violation.getMessage())
                    .orElse(ErrorCode.COMMON_VALIDATION_ERROR.getMessage());

                return new ResolvedError(ErrorCode.COMMON_VALIDATION_ERROR, message);
            }

            if (cause instanceof MessageConversionException) {
                return ResolvedError.from(ErrorCode.COMMON_VALIDATION_ERROR);
            }

            if (cause instanceof CustomException customException) {
                return new ResolvedError(customException.getErrorCode(), customException.getMessage());
            }
        }

        if (isUnauthenticatedSessionFrame(clientMessage, exception)) {
            return ResolvedError.from(ErrorCode.AUTH_UNAUTHORIZED);
        }

        return ResolvedError.from(ErrorCode.INTERNAL_SERVER_ERROR);
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

    private byte[] serialize(ResolvedError resolvedError) {
        try {
            return objectMapper.writeValueAsBytes(
                ApiResponse.failure(ErrorResponse.from(resolvedError.errorCode(), resolvedError.message()))
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("STOMP 에러 응답 직렬화에 실패했습니다.", exception);
        }
    }

    private record ResolvedError(
        ErrorCode errorCode,
        String message
    ) {

        private static ResolvedError from(ErrorCode errorCode) {
            return new ResolvedError(errorCode, errorCode.getMessage());
        }
    }
}

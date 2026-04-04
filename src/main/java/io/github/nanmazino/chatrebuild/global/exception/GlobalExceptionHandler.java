package io.github.nanmazino.chatrebuild.global.exception;

import io.github.nanmazino.chatrebuild.global.response.ApiResponse;
import io.github.nanmazino.chatrebuild.global.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException exception) {
        ErrorCode errorCode = exception.getErrorCode();

        return ResponseEntity.status(errorCode.getStatus())
            .body(ApiResponse.failure(ErrorResponse.from(errorCode, exception.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
        MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .findFirst()
            .map(FieldError::getDefaultMessage)
            .orElse(ErrorCode.COMMON_VALIDATION_ERROR.getMessage());

        return ResponseEntity.badRequest()
            .body(ApiResponse.failure(ErrorResponse.from(ErrorCode.COMMON_VALIDATION_ERROR, message)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
        ConstraintViolationException exception
    ) {
        String message = exception.getConstraintViolations()
            .stream()
            .findFirst()
            .map(violation -> violation.getMessage())
            .orElse(ErrorCode.COMMON_VALIDATION_ERROR.getMessage());

        return ResponseEntity.badRequest()
            .body(ApiResponse.failure(ErrorResponse.from(ErrorCode.COMMON_VALIDATION_ERROR, message)));
    }

    @ExceptionHandler({
        AccessDeniedException.class,
        AuthorizationDeniedException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(Exception exception) {
        return ResponseEntity.status(ErrorCode.AUTH_FORBIDDEN.getStatus())
            .body(ApiResponse.failure(
                ErrorResponse.from(ErrorCode.AUTH_FORBIDDEN, ErrorCode.AUTH_FORBIDDEN.getMessage())
            ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        log.error("Unhandled exception", exception);

        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
            .body(ApiResponse.failure(
                ErrorResponse.from(ErrorCode.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR.getMessage())
            ));
    }
}

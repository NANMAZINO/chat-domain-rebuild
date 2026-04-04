package io.github.nanmazino.chatrebuild.global.exception;

import lombok.Getter;

@Getter
public abstract class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    protected CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    protected CustomException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}

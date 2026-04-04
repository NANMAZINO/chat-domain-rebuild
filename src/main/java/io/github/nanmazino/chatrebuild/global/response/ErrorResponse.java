package io.github.nanmazino.chatrebuild.global.response;

import io.github.nanmazino.chatrebuild.global.exception.ErrorCode;

public record ErrorResponse(
    String code,
    String message
) {

    public static ErrorResponse from(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getCode(), message);
    }
}

package io.github.nanmazino.chatrebuild.chat.exception;

import io.github.nanmazino.chatrebuild.global.exception.CustomException;
import io.github.nanmazino.chatrebuild.global.exception.ErrorCode;

public class InvalidLastReadMessageIdException extends CustomException {

    public InvalidLastReadMessageIdException() {
        super(ErrorCode.COMMON_VALIDATION_ERROR, "lastReadMessageId가 올바르지 않습니다.");
    }
}

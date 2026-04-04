package io.github.nanmazino.chatrebuild.user.exception;

import io.github.nanmazino.chatrebuild.global.exception.CustomException;
import io.github.nanmazino.chatrebuild.global.exception.ErrorCode;

public class DuplicateEmailException extends CustomException {

    public DuplicateEmailException() {
        super(ErrorCode.USER_EMAIL_DUPLICATED);
    }
}

package io.github.nanmazino.chatrebuild.auth.exception;

import io.github.nanmazino.chatrebuild.global.exception.CustomException;
import io.github.nanmazino.chatrebuild.global.exception.ErrorCode;

public class InvalidLoginCredentialsException extends CustomException {

    public InvalidLoginCredentialsException() {
        super(ErrorCode.LOGIN_INVALID_CREDENTIALS);
    }
}

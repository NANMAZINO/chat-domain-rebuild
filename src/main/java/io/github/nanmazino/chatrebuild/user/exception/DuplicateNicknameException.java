package io.github.nanmazino.chatrebuild.user.exception;

import io.github.nanmazino.chatrebuild.global.exception.CustomException;
import io.github.nanmazino.chatrebuild.global.exception.ErrorCode;

public class DuplicateNicknameException extends CustomException {

    public DuplicateNicknameException() {
        super(ErrorCode.USER_NICKNAME_DUPLICATED);
    }
}

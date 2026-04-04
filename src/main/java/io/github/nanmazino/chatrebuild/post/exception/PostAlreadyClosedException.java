package io.github.nanmazino.chatrebuild.post.exception;

import io.github.nanmazino.chatrebuild.global.exception.CustomException;
import io.github.nanmazino.chatrebuild.global.exception.ErrorCode;

public class PostAlreadyClosedException extends CustomException {

    public PostAlreadyClosedException() {
        super(ErrorCode.POST_ALREADY_CLOSED);
    }
}

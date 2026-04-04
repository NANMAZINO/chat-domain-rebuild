package io.github.nanmazino.chatrebuild.post.exception;

import io.github.nanmazino.chatrebuild.global.exception.CustomException;
import io.github.nanmazino.chatrebuild.global.exception.ErrorCode;

public class PostNotFoundException extends CustomException {

    public PostNotFoundException() {
        super(ErrorCode.POST_NOT_FOUND);
    }
}

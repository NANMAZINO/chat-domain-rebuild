package io.github.nanmazino.chatrebuild.chat.exception;

import io.github.nanmazino.chatrebuild.global.exception.CustomException;
import io.github.nanmazino.chatrebuild.global.exception.ErrorCode;

public class InvalidChatMessageCursorException extends CustomException {

    public InvalidChatMessageCursorException() {
        super(ErrorCode.CHAT_MESSAGE_INVALID_CURSOR);
    }
}

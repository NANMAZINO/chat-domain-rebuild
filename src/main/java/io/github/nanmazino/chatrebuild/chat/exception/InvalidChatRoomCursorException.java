package io.github.nanmazino.chatrebuild.chat.exception;

import io.github.nanmazino.chatrebuild.global.exception.CustomException;
import io.github.nanmazino.chatrebuild.global.exception.ErrorCode;

public class InvalidChatRoomCursorException extends CustomException {

    public InvalidChatRoomCursorException() {
        super(ErrorCode.COMMON_VALIDATION_ERROR, "cursorRoomId는 cursorLastMessageAt와 함께 전달해야 합니다.");
    }
}

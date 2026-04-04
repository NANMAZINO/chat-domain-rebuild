package io.github.nanmazino.chatrebuild.chat.exception;

import io.github.nanmazino.chatrebuild.global.exception.CustomException;
import io.github.nanmazino.chatrebuild.global.exception.ErrorCode;

public class ChatRoomFullException extends CustomException {

    public ChatRoomFullException() {
        super(ErrorCode.CHAT_ROOM_FULL);
    }
}

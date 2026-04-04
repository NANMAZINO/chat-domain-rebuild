package io.github.nanmazino.chatrebuild.chat.exception;

import io.github.nanmazino.chatrebuild.global.exception.CustomException;
import io.github.nanmazino.chatrebuild.global.exception.ErrorCode;

public class ChatMemberNotFoundException extends CustomException {

    public ChatMemberNotFoundException() {
        super(ErrorCode.CHAT_MEMBER_NOT_FOUND);
    }
}

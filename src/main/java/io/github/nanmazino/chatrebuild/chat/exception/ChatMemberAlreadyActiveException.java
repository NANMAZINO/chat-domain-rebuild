package io.github.nanmazino.chatrebuild.chat.exception;

import io.github.nanmazino.chatrebuild.global.exception.CustomException;
import io.github.nanmazino.chatrebuild.global.exception.ErrorCode;

public class ChatMemberAlreadyActiveException extends CustomException {

    public ChatMemberAlreadyActiveException() {
        super(ErrorCode.CHAT_MEMBER_ALREADY_ACTIVE);
    }
}

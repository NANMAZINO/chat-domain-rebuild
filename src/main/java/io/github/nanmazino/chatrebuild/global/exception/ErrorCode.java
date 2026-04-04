package io.github.nanmazino.chatrebuild.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "인증이 필요합니다."),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", "접근 권한이 없습니다."),
    LOGIN_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "LOGIN_INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."),
    COMMON_VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "COMMON_VALIDATION_ERROR", "요청 값이 올바르지 않습니다."),
    COMMON_RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_RESOURCE_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
    USER_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "USER_EMAIL_DUPLICATED", "이미 사용 중인 이메일입니다."),
    USER_NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "USER_NICKNAME_DUPLICATED", "이미 사용 중인 닉네임입니다."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "게시글을 찾을 수 없습니다."),
    POST_ALREADY_CLOSED(HttpStatus.CONFLICT, "POST_ALREADY_CLOSED", "이미 모집이 종료된 게시글입니다."),
    CHAT_ROOM_FULL(HttpStatus.CONFLICT, "CHAT_ROOM_FULL", "채팅방 정원이 가득 찼습니다."),
    CHAT_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_MEMBER_NOT_FOUND", "채팅방 참여 멤버를 찾을 수 없습니다."),
    CHAT_MEMBER_ALREADY_ACTIVE(HttpStatus.CONFLICT, "CHAT_MEMBER_ALREADY_ACTIVE", "이미 참여 중인 채팅방입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

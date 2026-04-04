package io.github.nanmazino.chatrebuild.user.dto.response;

public record SignUpResponse(
    Long userId,
    String email,
    String nickname
) {

}

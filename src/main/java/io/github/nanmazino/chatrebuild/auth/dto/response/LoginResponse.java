package io.github.nanmazino.chatrebuild.auth.dto.response;

public record LoginResponse(
    String accessToken,
    String tokenType,
    LoginUser user
) {

    public static LoginResponse of(String accessToken, Long userId, String email, String nickname) {
        return new LoginResponse(accessToken, "Bearer", new LoginUser(userId, email, nickname));
    }

    public record LoginUser(
        Long userId,
        String email,
        String nickname
    ) {
    }
}

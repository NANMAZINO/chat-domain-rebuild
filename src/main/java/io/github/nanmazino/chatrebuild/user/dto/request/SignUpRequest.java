package io.github.nanmazino.chatrebuild.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이어야 합니다.")
    String email,

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, max = 255, message = "비밀번호는 8자 이상 255자 이하여야 합니다.")
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,255}$",
        message = "비밀번호는 영문, 숫자, 특수문자를 포함해야 합니다."
    )
    String password,

    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
    String nickname
) {

}

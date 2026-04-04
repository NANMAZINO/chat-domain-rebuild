package io.github.nanmazino.chatrebuild.post.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreatePostRequest(
    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 255, message = "제목은 255자 이하여야 합니다.")
    String title,

    @NotBlank(message = "내용은 필수입니다.")
    String content,

    @Positive(message = "최대 참여 인원은 1명 이상이어야 합니다.")
    int maxParticipants
) {

}

package io.github.nanmazino.chatrebuild.post.dto.response;

import io.github.nanmazino.chatrebuild.post.entity.PostStatus;
import java.time.LocalDateTime;

public record DeletePostResponse(
    Long postId,
    PostStatus status,
    LocalDateTime deletedAt
) {

}

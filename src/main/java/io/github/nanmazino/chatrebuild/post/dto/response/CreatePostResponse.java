package io.github.nanmazino.chatrebuild.post.dto.response;

import io.github.nanmazino.chatrebuild.post.entity.PostStatus;
import java.time.LocalDateTime;

public record CreatePostResponse(
    Long postId,
    String title,
    String content,
    int maxParticipants,
    PostStatus status,
    PostAuthorResponse author,
    LocalDateTime createdAt
) {

}

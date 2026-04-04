package io.github.nanmazino.chatrebuild.post.dto.response;

import io.github.nanmazino.chatrebuild.post.entity.PostStatus;
import java.time.LocalDateTime;

public record PostSummaryResponse(
    Long postId,
    String title,
    int maxParticipants,
    PostStatus status,
    String authorNickname,
    LocalDateTime createdAt
) {

}

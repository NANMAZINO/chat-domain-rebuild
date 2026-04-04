package io.github.nanmazino.chatrebuild.post.dto.response;

import java.util.List;

public record PostListResponse(
    List<PostSummaryResponse> items,
    int page,
    int size,
    boolean hasNext
) {

}

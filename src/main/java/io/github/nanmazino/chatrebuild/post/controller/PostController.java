package io.github.nanmazino.chatrebuild.post.controller;

import io.github.nanmazino.chatrebuild.chat.service.ChatMembershipService;
import io.github.nanmazino.chatrebuild.global.response.ApiResponse;
import io.github.nanmazino.chatrebuild.global.security.JwtPrincipal;
import io.github.nanmazino.chatrebuild.post.dto.request.CreatePostRequest;
import io.github.nanmazino.chatrebuild.post.dto.request.UpdatePostRequest;
import io.github.nanmazino.chatrebuild.post.dto.response.ClosePostResponse;
import io.github.nanmazino.chatrebuild.post.dto.response.CreatePostResponse;
import io.github.nanmazino.chatrebuild.post.dto.response.DeletePostResponse;
import io.github.nanmazino.chatrebuild.post.dto.response.JoinPostResponse;
import io.github.nanmazino.chatrebuild.post.dto.response.LeavePostResponse;
import io.github.nanmazino.chatrebuild.post.dto.response.PostDetailResponse;
import io.github.nanmazino.chatrebuild.post.dto.response.PostListResponse;
import io.github.nanmazino.chatrebuild.post.entity.PostStatus;
import io.github.nanmazino.chatrebuild.post.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Validated
@Tag(name = "Post", description = "게시글 관련 API")
public class PostController {

    private final PostService postService;
    private final ChatMembershipService chatMembershipService;

    @PostMapping
    @Operation(summary = "게시글 생성", description = "인증 사용자가 게시글을 생성합니다.")
    public ResponseEntity<ApiResponse<CreatePostResponse>> createPost(
        @AuthenticationPrincipal JwtPrincipal principal,
        @Valid @RequestBody CreatePostRequest request
    ) {
        CreatePostResponse response = postService.createPost(principal.userId(), request);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "게시글 목록 조회", description = "공개 게시글 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<PostListResponse>> getPosts(
        @RequestParam(required = false)
        @PositiveOrZero(message = "page는 0 이상이어야 합니다.")
        Integer page,
        @RequestParam(required = false)
        @Positive(message = "size는 1 이상이어야 합니다.")
        Integer size,
        @RequestParam(required = false) PostStatus status,
        @RequestParam(required = false) String keyword
    ) {
        PostListResponse response = postService.getPosts(page, size, status, keyword);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{postId}")
    @Operation(summary = "게시글 상세 조회", description = "게시글 상세를 조회합니다.")
    public ResponseEntity<ApiResponse<PostDetailResponse>> getPost(@PathVariable Long postId) {
        PostDetailResponse response = postService.getPost(postId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{postId}/join")
    @Operation(summary = "게시글 참여", description = "인증 사용자가 게시글과 연결된 채팅방에 참여합니다.")
    public ResponseEntity<ApiResponse<JoinPostResponse>> joinPost(
        @PathVariable Long postId,
        @AuthenticationPrincipal JwtPrincipal principal
    ) {
        JoinPostResponse response = chatMembershipService.joinPost(postId, principal.userId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{postId}/leave")
    @Operation(summary = "게시글 나가기", description = "인증 사용자가 게시글과 연결된 채팅방에서 나갑니다.")
    public ResponseEntity<ApiResponse<LeavePostResponse>> leavePost(
        @PathVariable Long postId,
        @AuthenticationPrincipal JwtPrincipal principal
    ) {
        LeavePostResponse response = chatMembershipService.leavePost(postId, principal.userId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{postId}")
    @Operation(summary = "게시글 수정", description = "작성자가 게시글을 수정합니다.")
    public ResponseEntity<ApiResponse<PostDetailResponse>> updatePost(
        @PathVariable Long postId,
        @AuthenticationPrincipal JwtPrincipal principal,
        @Valid @RequestBody UpdatePostRequest request
    ) {
        PostDetailResponse response = postService.updatePost(postId, principal.userId(), request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{postId}/close")
    @Operation(summary = "게시글 모집 종료", description = "작성자가 게시글 모집을 종료합니다.")
    public ResponseEntity<ApiResponse<ClosePostResponse>> closePost(
        @PathVariable Long postId,
        @AuthenticationPrincipal JwtPrincipal principal
    ) {
        ClosePostResponse response = postService.closePost(postId, principal.userId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{postId}")
    @Operation(summary = "게시글 삭제", description = "작성자가 게시글을 soft delete 처리합니다.")
    public ResponseEntity<ApiResponse<DeletePostResponse>> deletePost(
        @PathVariable Long postId,
        @AuthenticationPrincipal JwtPrincipal principal
    ) {
        DeletePostResponse response = postService.deletePost(postId, principal.userId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

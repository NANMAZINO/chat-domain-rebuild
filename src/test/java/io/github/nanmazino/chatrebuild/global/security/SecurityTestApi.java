package io.github.nanmazino.chatrebuild.global.security;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecurityTestApi {

    @GetMapping("/api/posts")
    Map<String, Object> getPosts() {
        return Map.of("postCount", 0);
    }

    @GetMapping("/api/posts/{postId}")
    Map<String, Object> getPost(@PathVariable Long postId) {
        return Map.of("postId", postId, "title", "test-post");
    }

    @GetMapping("/api/test/protected")
    Map<String, Object> protectedEndpoint(@AuthenticationPrincipal JwtPrincipal principal) {
        return Map.of(
            "userId", principal.userId(),
            "email", principal.email(),
            "nickname", principal.nickname()
        );
    }

    @PatchMapping("/api/posts/{postId}/close")
    Map<String, Object> closePost(@PathVariable Long postId, @AuthenticationPrincipal JwtPrincipal principal) {
        return Map.of("postId", postId, "closedBy", principal.userId());
    }

    @DeleteMapping("/api/posts/{postId}")
    Map<String, Object> deletePost(@PathVariable Long postId, @AuthenticationPrincipal JwtPrincipal principal) {
        return Map.of("postId", postId, "deletedBy", principal.userId());
    }

    @GetMapping("/api/test/admin")
    @PreAuthorize("hasRole('ADMIN')")
    Map<String, String> adminOnlyEndpoint() {
        return Map.of("message", "admin");
    }
}

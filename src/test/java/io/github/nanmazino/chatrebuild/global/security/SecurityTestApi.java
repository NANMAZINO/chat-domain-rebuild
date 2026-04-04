package io.github.nanmazino.chatrebuild.global.security;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecurityTestApi {

    @GetMapping("/api/test/protected")
    Map<String, Object> protectedEndpoint(@AuthenticationPrincipal JwtPrincipal principal) {
        return Map.of(
            "userId", principal.userId(),
            "email", principal.email(),
            "nickname", principal.nickname()
        );
    }

    @GetMapping("/api/test/admin")
    @PreAuthorize("hasRole('ADMIN')")
    Map<String, String> adminOnlyEndpoint() {
        return Map.of("message", "admin");
    }
}

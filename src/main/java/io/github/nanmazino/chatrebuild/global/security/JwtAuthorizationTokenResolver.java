package io.github.nanmazino.chatrebuild.global.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JwtAuthorizationTokenResolver {

    private static final String BEARER_PREFIX = "Bearer ";

    public String resolveToken(HttpServletRequest request) {
        return extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
    }

    public String resolveToken(StompHeaderAccessor accessor) {
        return extractBearerToken(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION));
    }

    private String extractBearerToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }

        return authorizationHeader.substring(BEARER_PREFIX.length());
    }
}

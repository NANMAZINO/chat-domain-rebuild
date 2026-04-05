package io.github.nanmazino.chatrebuild.global.security;

import io.github.nanmazino.chatrebuild.chat.service.ChatMembershipService;
import io.github.nanmazino.chatrebuild.chat.exception.ChatMemberNotFoundException;
import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompJwtChannelInterceptor implements ChannelInterceptor {

    private static final Pattern SUBSCRIBE_DESTINATION_PATTERN = Pattern.compile("^/sub/chat-rooms/(\\d+)$");
    private static final Pattern SEND_DESTINATION_PATTERN = Pattern.compile("^/pub/chat-rooms/(\\d+)/messages$");

    private final JwtAuthorizationTokenResolver jwtAuthorizationTokenResolver;
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final ChatMembershipService chatMembershipService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        return switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor, message);
            case SUBSCRIBE -> authorize(accessor, message, SUBSCRIBE_DESTINATION_PATTERN);
            case SEND -> authorize(accessor, message, SEND_DESTINATION_PATTERN);
            default -> message;
        };
    }

    private Message<?> authenticate(StompHeaderAccessor accessor, Message<?> message) {
        String token = jwtAuthorizationTokenResolver.resolveToken(accessor);

        if (!jwtTokenProvider.validateToken(token)) {
            throw new AuthenticationCredentialsNotFoundException("유효한 STOMP 인증 토큰이 필요합니다.");
        }

        try {
            Long userId = jwtTokenProvider.getUserId(token);
            UserDetails userDetails = customUserDetailsService.loadUserById(userId);
            UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
                );

            accessor.setUser(authentication);
            return message;
        } catch (UsernameNotFoundException | IllegalArgumentException exception) {
            throw new AuthenticationCredentialsNotFoundException("유효한 STOMP 인증 토큰이 필요합니다.", exception);
        }
    }

    private Message<?> authorize(
        StompHeaderAccessor accessor,
        Message<?> message,
        Pattern destinationPattern
    ) {
        JwtPrincipal principal = extractJwtPrincipal(accessor.getUser());
        Long roomId = extractRoomId(accessor.getDestination(), destinationPattern);

        try {
            chatMembershipService.getActiveMember(roomId, principal.userId());
        } catch (ChatMemberNotFoundException exception) {
            throw new AccessDeniedException("채팅방 접근 권한이 없습니다.", exception);
        }

        return message;
    }

    private JwtPrincipal extractJwtPrincipal(@Nullable Principal principal) {
        if (principal instanceof Authentication authentication
            && authentication.getPrincipal() instanceof JwtPrincipal jwtPrincipal) {
            return jwtPrincipal;
        }

        throw new AuthenticationCredentialsNotFoundException("STOMP 인증 정보가 없습니다.");
    }

    private Long extractRoomId(String destination, Pattern destinationPattern) {
        Matcher matcher = destinationPattern.matcher(destination == null ? "" : destination);

        if (!matcher.matches()) {
            throw new AccessDeniedException("허용되지 않은 destination 입니다.");
        }

        return Long.parseLong(matcher.group(1));
    }
}

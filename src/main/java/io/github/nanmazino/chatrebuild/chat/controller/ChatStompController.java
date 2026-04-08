package io.github.nanmazino.chatrebuild.chat.controller;

import io.github.nanmazino.chatrebuild.chat.dto.request.ChatSendRequest;
import io.github.nanmazino.chatrebuild.chat.service.ChatMessageService;
import io.github.nanmazino.chatrebuild.global.security.JwtPrincipal;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatMessageService chatMessageService;

    @MessageMapping("/chat-rooms/{roomId}/messages")
    public void sendMessage(
        @DestinationVariable Long roomId,
        @Payload ChatSendRequest request,
        Principal principal
    ) {
        JwtPrincipal jwtPrincipal = extractJwtPrincipal(principal);
        chatMessageService.sendMessage(roomId, jwtPrincipal.userId(), request);
    }

    private JwtPrincipal extractJwtPrincipal(Principal principal) {
        if (principal instanceof Authentication authentication
            && authentication.getPrincipal() instanceof JwtPrincipal jwtPrincipal) {
            return jwtPrincipal;
        }

        throw new AuthenticationCredentialsNotFoundException("STOMP 인증 정보가 없습니다.");
    }
}

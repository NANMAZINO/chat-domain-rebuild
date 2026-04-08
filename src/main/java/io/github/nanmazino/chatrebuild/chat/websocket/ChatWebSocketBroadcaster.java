package io.github.nanmazino.chatrebuild.chat.websocket;

import io.github.nanmazino.chatrebuild.chat.dto.response.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatWebSocketBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcast(ChatMessageResponse response) {
        messagingTemplate.convertAndSend("/sub/chat-rooms/" + response.roomId(), response);
    }
}

package io.github.nanmazino.chatrebuild.chat.websocket;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.messaging.StompSubProtocolHandler;
import org.springframework.web.socket.messaging.SubProtocolHandler;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

@Component
public class StompErrorHandlerConfigurer implements SmartInitializingSingleton {

    private final WebSocketHandler webSocketHandler;
    private final StompErrorHandler stompErrorHandler;

    public StompErrorHandlerConfigurer(
        @Qualifier("subProtocolWebSocketHandler") WebSocketHandler webSocketHandler,
        StompErrorHandler stompErrorHandler
    ) {
        this.webSocketHandler = webSocketHandler;
        this.stompErrorHandler = stompErrorHandler;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!(webSocketHandler instanceof SubProtocolWebSocketHandler subProtocolWebSocketHandler)) {
            return;
        }

        if (subProtocolWebSocketHandler.getDefaultProtocolHandler() instanceof StompSubProtocolHandler stompHandler) {
            stompHandler.setErrorHandler(stompErrorHandler);
        }

        for (SubProtocolHandler protocolHandler : subProtocolWebSocketHandler.getProtocolHandlers()) {
            if (protocolHandler instanceof StompSubProtocolHandler stompSubProtocolHandler) {
                stompSubProtocolHandler.setErrorHandler(stompErrorHandler);
            }
        }
    }
}

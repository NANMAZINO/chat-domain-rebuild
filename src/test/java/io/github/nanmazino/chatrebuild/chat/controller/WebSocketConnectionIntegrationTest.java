package io.github.nanmazino.chatrebuild.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nanmazino.chatrebuild.support.IntegrationTestSupport;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketConnectionIntegrationTest extends IntegrationTestSupport {

    @LocalServerPort
    private int port;

    private StompSession stompSession;

    @AfterEach
    void tearDown() {
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.disconnect();
        }
    }

    @Test
    @DisplayName("웹소켓 STOMP 엔드포인트에 연결할 수 있다")
    void connectWebSocketEndpoint() throws Exception {
        StompSession session = connect();

        assertThat(session.isConnected()).isTrue();
    }

    @Test
    @DisplayName("채팅방 destination을 구독할 수 있다")
    void subscribeChatRoomDestination() throws Exception {
        StompSession session = connect();

        StompSession.Subscription subscription = session.subscribe(
            "/sub/chat-rooms/1",
            new NoOpFrameHandler()
        );

        assertThat(subscription).isNotNull();
        assertThat(session.isConnected()).isTrue();
    }

    private StompSession connect() throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        CompletableFuture<Throwable> transportFailure = new CompletableFuture<>();
        StompSession session = stompClient.connectAsync(
            "ws://127.0.0.1:" + port + "/ws-stomp",
            new StompSessionHandlerAdapter() {
                @Override
                public void handleTransportError(StompSession session, Throwable exception) {
                    transportFailure.complete(exception);
                }
            }
        ).get(5, TimeUnit.SECONDS);

        if (transportFailure.isDone()) {
            throw new AssertionError("WebSocket transport error", transportFailure.get());
        }

        this.stompSession = session;
        return session;
    }

    private static class NoOpFrameHandler implements StompFrameHandler {

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return String.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            throw new AssertionError("Unexpected STOMP frame received: " + payload);
        }
    }
}

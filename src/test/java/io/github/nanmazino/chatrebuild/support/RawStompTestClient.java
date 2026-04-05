package io.github.nanmazino.chatrebuild.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class RawStompTestClient implements AutoCloseable {

    private static final long FRAME_TIMEOUT_SECONDS = 5;
    private static final long NO_FRAME_TIMEOUT_MILLIS = 700;

    private final int port;
    private final List<WebSocketSession> sessions = new ArrayList<>();

    public RawStompTestClient(int port) {
        this.port = port;
    }

    public Connection connect(String token) throws Exception {
        Connection connection = openConnection();
        connection.send(connectFrame(token));

        return connection.withConnectedFrame(connection.awaitFrame());
    }

    public Connection openConnection() throws Exception {
        FrameCollectingWebSocketHandler handler = new FrameCollectingWebSocketHandler();
        WebSocketSession session = new StandardWebSocketClient()
            .execute(handler, "ws://127.0.0.1:" + port + "/ws-stomp")
            .get(FRAME_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        sessions.add(session);
        return new Connection(session, handler);
    }

    public static String connectFrame(String token) {
        String authorizationHeader = token == null ? "" : "Authorization:Bearer " + token + "\n";

        return "CONNECT\n"
            + "accept-version:1.2\n"
            + "host:127.0.0.1\n"
            + authorizationHeader
            + "\n"
            + "\u0000";
    }

    public static String subscribeFrame(Long roomId) {
        return "SUBSCRIBE\n"
            + "id:sub-" + roomId + "\n"
            + "destination:/sub/chat-rooms/" + roomId + "\n"
            + "\n"
            + "\u0000";
    }

    public static String sendFrame(Long roomId, String body) {
        String normalizedBody = body.strip();
        int contentLength = normalizedBody.getBytes(StandardCharsets.UTF_8).length;

        return "SEND\n"
            + "destination:/pub/chat-rooms/" + roomId + "/messages\n"
            + "content-type:application/json\n"
            + "content-length:" + contentLength + "\n"
            + "\n"
            + normalizedBody
            + "\u0000";
    }

    public static String frameCommand(String frame) {
        return normalizeFrame(frame).split("\n", 2)[0];
    }

    public static JsonNode parseBody(ObjectMapper objectMapper, String frame) throws Exception {
        return objectMapper.readTree(frameBody(frame));
    }

    public static String frameBody(String frame) {
        String normalized = normalizeFrame(frame);
        int bodyStart = normalized.indexOf("\n\n");

        if (bodyStart < 0) {
            return "";
        }

        return normalized.substring(bodyStart + 2).replace("\u0000", "");
    }

    public static String normalizeFrame(String frame) {
        return frame.replace("\r", "");
    }

    @Override
    public void close() throws Exception {
        Exception lastException = null;

        for (WebSocketSession session : sessions) {
            if (session == null || !session.isOpen()) {
                continue;
            }

            try {
                session.close();
            } catch (Exception exception) {
                lastException = exception;
            }
        }

        sessions.clear();

        if (lastException != null) {
            throw lastException;
        }
    }

    public static final class Connection {

        private final WebSocketSession session;
        private final FrameCollectingWebSocketHandler handler;
        private String connectedFrame;

        private Connection(WebSocketSession session, FrameCollectingWebSocketHandler handler) {
            this.session = session;
            this.handler = handler;
        }

        public Connection withConnectedFrame(String connectedFrame) {
            this.connectedFrame = connectedFrame;
            return this;
        }

        public String connectedFrame() {
            return connectedFrame;
        }

        public void send(String frame) throws Exception {
            session.sendMessage(new TextMessage(frame));
        }

        public String awaitFrame() throws Exception {
            String frame = handler.frames.poll(FRAME_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (frame == null) {
                throw new AssertionError("Expected a STOMP frame but none was received.");
            }

            return frame;
        }

        public void assertNoFrame() throws Exception {
            String frame = handler.frames.poll(NO_FRAME_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

            if (frame != null) {
                throw new AssertionError("Unexpected STOMP frame received: " + frame);
            }
        }
    }

    private static final class FrameCollectingWebSocketHandler extends TextWebSocketHandler {

        private final LinkedBlockingQueue<String> frames = new LinkedBlockingQueue<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            frames.offer(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            if (status.getCode() != CloseStatus.NORMAL.getCode()) {
                frames.offer("CLOSED\n\n" + status + "\u0000");
            }
        }
    }
}

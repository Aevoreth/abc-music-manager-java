package com.aevoreth.abcmm.domain.setplay.relay;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Outbound WebSocket to Cloudflare Set Play relay.
 * Leader sends JSON text; assistant receives snapshots.
 * URL: {@code wss://host/api/rooms/CODE/ws}. Leader sends {@code Authorization: Bearer} (relay token).
 */
public final class SetPlayRelayClient implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public interface Listener {
        void onConnected();

        void onDisconnected();

        default void onClosed(int code, String reason) {
            onDisconnected();
        }

        void onStateReceived(Map<String, Object> data);

        void onError(String message);
    }

    private final HttpClient httpClient;
    private final Listener listener;
    private final AtomicReference<WebSocket> socket = new AtomicReference<>();
    private final StringBuilder textBuffer = new StringBuilder();

    public SetPlayRelayClient(Listener listener) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build(), listener);
    }

    public SetPlayRelayClient(HttpClient httpClient, Listener listener) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public void openAssistant(String baseUrl, String roomCode) {
        open(joinWsUrl(baseUrl, roomCode), null);
    }

    public void openLeader(String baseUrl, String roomCode, String relayToken) {
        open(joinWsUrl(baseUrl, roomCode), relayToken);
    }

    public void sendSnapshot(Map<String, Object> payload) {
        WebSocket ws = socket.get();
        if (ws == null || !isOpen()) {
            return;
        }
        try {
            String json = MAPPER.writeValueAsString(payload);
            ws.sendText(json, true);
        } catch (Exception ex) {
            listener.onError(ex.getMessage() == null ? "Failed to send snapshot" : ex.getMessage());
        }
    }

    public boolean isOpen() {
        WebSocket ws = socket.get();
        return ws != null && !ws.isInputClosed() && !ws.isOutputClosed();
    }

    @Override
    public void close() {
        WebSocket ws = socket.getAndSet(null);
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    private void open(String url, String bearerToken) {
        close();
        textBuffer.setLength(0);
        java.net.http.WebSocket.Builder builder = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(30));
        if (bearerToken != null && !bearerToken.isBlank()) {
            builder.header("Authorization", "Bearer " + bearerToken.strip());
        }
        builder.buildAsync(URI.create(url), new WsListener())
                .whenComplete((ws, err) -> {
                    if (err != null) {
                        listener.onError(err.getMessage() == null ? "WebSocket connect failed" : err.getMessage());
                    } else {
                        socket.set(ws);
                    }
                });
    }

    static String joinWsUrl(String baseUrl, String roomCode) {
        String b = SetPlayShareUrls.relayWsOrigin(baseUrl).replaceAll("/+$", "");
        String code = URLEncoder.encode(
                (roomCode == null ? "" : roomCode).strip().toUpperCase(),
                StandardCharsets.UTF_8);
        return b + "/api/rooms/" + code + "/ws";
    }

    private final class WsListener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket webSocket) {
            socket.set(webSocket);
            listener.onConnected();
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String msg = textBuffer.toString();
                textBuffer.setLength(0);
                try {
                    Map<String, Object> parsed = MAPPER.readValue(msg, new TypeReference<>() {
                    });
                    listener.onStateReceived(parsed);
                } catch (Exception ex) {
                    listener.onError("Invalid JSON from relay");
                }
            }
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            socket.compareAndSet(webSocket, null);
            listener.onClosed(statusCode, reason);
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            listener.onError(error.getMessage() == null ? "WebSocket error" : error.getMessage());
            WebSocket.Listener.super.onError(webSocket, error);
        }
    }
}

package com.aevoreth.abcmm.domain.setplay.relay;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Outbound WebSocket to Cloudflare Set Play relay.
 * Leader sends JSON text; assistant receives snapshots.
 * URL: {@code wss://host/api/rooms/CODE/ws}. Leader sends {@code Authorization: Bearer} (relay token).
 * Each {@code open} bumps a generation so stale sockets cannot steal the connection or fire close.
 */
public final class SetPlayRelayClient implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public interface Listener {
        void onConnected();

        void onDisconnected();

        default void onClosed(int code, String reason) {
            onDisconnected();
        }

        default void onReconnecting(int attempt) {
        }

        default void onHello(String role) {
        }

        void onStateReceived(Map<String, Object> data);

        void onError(String message);
    }

    private final HttpClient httpClient;
    private final Listener listener;
    private final AtomicReference<WebSocket> socket = new AtomicReference<>();
    private final AtomicLong generation = new AtomicLong();
    private final Object sendLock = new Object();
    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;
    private CompletableFuture<WebSocket> inFlight;
    private String pendingJson;
    private volatile boolean userClosed = true;
    private volatile boolean wantedLeader;
    private String lastUrl;
    private String lastToken;
    private ScheduledFuture<?> pingTask;
    private ScheduledFuture<?> reconnectTask;
    private int reconnectAttempt;

    public SetPlayRelayClient(Listener listener) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build(), listener, newRelayScheduler(), true);
    }

    public SetPlayRelayClient(HttpClient httpClient, Listener listener) {
        this(httpClient, listener, newRelayScheduler(), true);
    }

    SetPlayRelayClient(HttpClient httpClient, Listener listener, ScheduledExecutorService scheduler) {
        this(httpClient, listener, scheduler, false);
    }

    private SetPlayRelayClient(
            HttpClient httpClient,
            Listener listener,
            ScheduledExecutorService scheduler,
            boolean ownsScheduler) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.ownsScheduler = ownsScheduler;
    }

    private static ScheduledExecutorService newRelayScheduler() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "set-play-relay");
            t.setDaemon(true);
            return t;
        });
    }

    public void openAssistant(String baseUrl, String roomCode) {
        open(joinWsUrl(baseUrl, roomCode), null, false);
    }

    public void openLeader(String baseUrl, String roomCode, String relayToken) {
        open(joinWsUrl(baseUrl, roomCode), relayToken, true);
    }

    public void sendSnapshot(Map<String, Object> payload) {
        WebSocket ws = socket.get();
        if (ws == null || !isOpen()) {
            return;
        }
        try {
            String json = MAPPER.writeValueAsString(payload);
            synchronized (sendLock) {
                if (inFlight != null && !inFlight.isDone()) {
                    pendingJson = json;
                    return;
                }
                sendNow(ws, json);
            }
        } catch (Exception ex) {
            listener.onError(ex.getMessage() == null ? "Failed to send snapshot" : ex.getMessage());
        }
    }

    private void sendNow(WebSocket ws, String json) {
        try {
            inFlight = ws.sendText(json, true);
            inFlight.whenComplete((ok, err) -> {
                if (err != null) {
                    listener.onError(err.getMessage() == null ? "Failed to send snapshot" : err.getMessage());
                }
                synchronized (sendLock) {
                    String next = pendingJson;
                    pendingJson = null;
                    WebSocket current = socket.get();
                    if (next != null && current != null && isOpen()) {
                        sendNow(current, next);
                    } else {
                        inFlight = null;
                    }
                }
            });
        } catch (Exception ex) {
            inFlight = null;
            listener.onError(ex.getMessage() == null ? "Failed to send snapshot" : ex.getMessage());
        }
    }

    public boolean isOpen() {
        WebSocket ws = socket.get();
        return ws != null && !ws.isInputClosed() && !ws.isOutputClosed();
    }

    /** User disconnect: do not auto-reconnect. */
    @Override
    public void close() {
        userClosed = true;
        cancelReconnect();
        stopPing();
        generation.incrementAndGet();
        synchronized (sendLock) {
            pendingJson = null;
            inFlight = null;
        }
        closeSocketQuietly();
    }

    /** Close the socket and stop background tasks (panel dispose). */
    public void shutdown() {
        close();
        if (ownsScheduler) {
            scheduler.shutdownNow();
        }
    }

    private void open(String url, String bearerToken, boolean leader) {
        userClosed = false;
        wantedLeader = leader;
        lastUrl = url;
        lastToken = bearerToken;
        reconnectAttempt = 0;
        cancelReconnect();
        stopPing();
        long gen = generation.incrementAndGet();
        synchronized (sendLock) {
            pendingJson = null;
            inFlight = null;
        }
        closeSocketQuietly();
        startHandshake(url, bearerToken, gen);
    }

    private void startHandshake(String url, String bearerToken, long gen) {
        java.net.http.WebSocket.Builder builder = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(30));
        if (bearerToken != null && !bearerToken.isBlank()) {
            builder.header("Authorization", "Bearer " + bearerToken.strip());
        }
        builder.buildAsync(URI.create(url), new WsListener(gen))
                .whenComplete((ws, err) -> {
                    if (!isCurrent(gen)) {
                        if (ws != null) {
                            closeWebSocketQuietly(ws);
                        }
                        return;
                    }
                    if (err != null) {
                        listener.onError(err.getMessage() == null ? "WebSocket connect failed" : err.getMessage());
                        scheduleReconnect(gen);
                    }
                    // Success: {@link WsListener#onOpen} owns the socket.
                });
    }

    static String joinWsUrl(String baseUrl, String roomCode) {
        String b = SetPlayShareUrls.relayWsOrigin(baseUrl).replaceAll("/+$", "");
        String code = URLEncoder.encode(
                (roomCode == null ? "" : roomCode).strip().toUpperCase(),
                StandardCharsets.UTF_8);
        return b + "/api/rooms/" + code + "/ws";
    }

    boolean isCurrent(long gen) {
        return gen == generation.get();
    }

    long currentGeneration() {
        return generation.get();
    }

    void handleOpen(long gen, WebSocket webSocket) {
        if (!isCurrent(gen)) {
            closeWebSocketQuietly(webSocket);
            return;
        }
        socket.set(webSocket);
        startPing(gen);
        listener.onConnected();
    }

    void handleText(long gen, String msg) {
        if (!isCurrent(gen)) {
            return;
        }
        try {
            Map<String, Object> parsed = MAPPER.readValue(msg, new TypeReference<>() {
            });
            Object type = parsed.get("type");
            if (SetPlaySync.HELLO_TYPE.equals(String.valueOf(type))) {
                String role = parsed.get("role") == null ? "" : String.valueOf(parsed.get("role"));
                if (wantedLeader && !"leader".equals(role)) {
                    userClosed = true;
                    generation.incrementAndGet();
                    stopPing();
                    closeSocketQuietly();
                    listener.onError(
                            "Relay token was not accepted as leader. Check Settings → Set Playback.");
                    return;
                }
                listener.onHello(role);
                return;
            }
            if (SetPlaySync.PING_TYPE.equals(String.valueOf(type))) {
                return;
            }
            listener.onStateReceived(parsed);
        } catch (Exception ex) {
            listener.onError("Invalid JSON from relay");
        }
    }

    void handleClose(long gen, int statusCode, String reason) {
        if (!isCurrent(gen)) {
            return;
        }
        stopPing();
        socket.set(null);
        listener.onClosed(statusCode, reason);
        if (SetPlayRelayRules.shouldAutoReconnect(userClosed, reason)) {
            scheduleReconnect(gen);
        }
    }

    void handleError(long gen, String message) {
        if (!isCurrent(gen)) {
            return;
        }
        listener.onError(message == null ? "WebSocket error" : message);
    }

    private void startPing(long gen) {
        stopPing();
        pingTask = scheduler.scheduleAtFixedRate(() -> {
            if (!isCurrent(gen)) {
                return;
            }
            WebSocket ws = socket.get();
            if (ws == null || !isOpen()) {
                return;
            }
            try {
                ws.sendPing(ByteBuffer.wrap(new byte[0]));
            } catch (Exception ignored) {
                // next close/error will reconnect
            }
        }, SetPlayRelayRules.PING_INTERVAL_MS, SetPlayRelayRules.PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopPing() {
        ScheduledFuture<?> task = pingTask;
        pingTask = null;
        if (task != null) {
            task.cancel(false);
        }
    }

    private void scheduleReconnect(long failedGen) {
        if (!isCurrent(failedGen) || userClosed || lastUrl == null || lastUrl.isBlank()) {
            return;
        }
        cancelReconnect();
        reconnectAttempt++;
        int attempt = reconnectAttempt;
        long delay = SetPlayRelayRules.reconnectDelayMillis(attempt);
        listener.onReconnecting(attempt);
        reconnectTask = scheduler.schedule(() -> {
            if (userClosed || !isCurrent(failedGen)) {
                return;
            }
            long gen = generation.incrementAndGet();
            startHandshake(lastUrl, lastToken, gen);
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void cancelReconnect() {
        ScheduledFuture<?> task = reconnectTask;
        reconnectTask = null;
        if (task != null) {
            task.cancel(false);
        }
    }

    private void closeSocketQuietly() {
        WebSocket ws = socket.getAndSet(null);
        closeWebSocketQuietly(ws);
    }

    private static void closeWebSocketQuietly(WebSocket ws) {
        if (ws == null) {
            return;
        }
        try {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        } catch (Exception ignored) {
            // ignore
        }
    }

    private final class WsListener implements WebSocket.Listener {
        private final long gen;
        private final StringBuilder textBuffer = new StringBuilder();

        WsListener(long gen) {
            this.gen = gen;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            handleOpen(gen, webSocket);
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (!isCurrent(gen)) {
                return WebSocket.Listener.super.onText(webSocket, data, last);
            }
            textBuffer.append(data);
            if (last) {
                String msg = textBuffer.toString();
                textBuffer.setLength(0);
                handleText(gen, msg);
            }
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            handleClose(gen, statusCode, reason);
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            handleError(gen, error.getMessage() == null ? "WebSocket error" : error.getMessage());
            WebSocket.Listener.super.onError(webSocket, error);
        }
    }
}

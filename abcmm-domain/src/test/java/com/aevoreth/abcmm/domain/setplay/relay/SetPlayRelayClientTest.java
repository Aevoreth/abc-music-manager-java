package com.aevoreth.abcmm.domain.setplay.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SetPlayRelayClientTest {

    private ScheduledExecutorService scheduler;
    private RecordingListener listener;
    private SetPlayRelayClient client;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "set-play-relay-test");
            t.setDaemon(true);
            return t;
        });
        listener = new RecordingListener();
        client = new SetPlayRelayClient(HttpClient.newHttpClient(), listener, scheduler);
    }

    @AfterEach
    void tearDown() {
        client.close();
        scheduler.shutdownNow();
    }

    @Test
    void joinWsUrl() {
        assertEquals(
                "wss://relay.example/api/rooms/AB12CD3/ws",
                SetPlayRelayClient.joinWsUrl("https://relay.example", "ab12cd3"));
    }

    @Test
    void staleCloseDoesNotNotify() {
        long current = client.currentGeneration();
        client.handleClose(current - 1, 1000, "bye");
        assertEquals(0, listener.closed.size());
        assertEquals(0, listener.disconnected);
    }

    @Test
    void currentCloseNotifies() {
        client.handleClose(client.currentGeneration(), 1000, "going away");
        assertEquals(1, listener.closed.size());
        assertEquals("going away", listener.closed.get(0));
        assertEquals(1, listener.disconnected);
    }

    @Test
    void staleTextDoesNotNotify() {
        client.handleText(client.currentGeneration() - 1, "{\"type\":\"set_play_state_v2\",\"revision\":1}");
        assertTrue(listener.states.isEmpty());
        assertTrue(listener.errors.isEmpty());
    }

    @Test
    void helloDispatchesRoleNotState() {
        client.handleText(client.currentGeneration(), "{\"type\":\"set_play_hello\",\"role\":\"assistant\"}");
        assertEquals(List.of("assistant"), listener.hellos);
        assertTrue(listener.states.isEmpty());
    }

    @Test
    void pingIsIgnored() {
        client.handleText(client.currentGeneration(), "{\"type\":\"ping\"}");
        assertTrue(listener.states.isEmpty());
        assertTrue(listener.hellos.isEmpty());
        assertTrue(listener.errors.isEmpty());
    }

    @Test
    void snapshotIsForwarded() {
        client.handleText(
                client.currentGeneration(),
                "{\"type\":\"set_play_state_v2\",\"revision\":3}");
        assertEquals(1, listener.states.size());
        assertEquals("set_play_state_v2", listener.states.get(0).get("type"));
    }

    @Test
    void closeBumpsGenerationSoPriorEventsAreIgnored() {
        long before = client.currentGeneration();
        client.close();
        assertTrue(client.currentGeneration() > before);
        client.handleClose(before, 1000, "bye");
        client.handleText(before, "{\"type\":\"set_play_state_v2\"}");
        assertEquals(0, listener.closed.size());
        assertTrue(listener.states.isEmpty());
    }

    @Test
    void invalidJsonReportsError() {
        client.handleText(client.currentGeneration(), "not-json");
        assertEquals(List.of("Invalid JSON from relay"), listener.errors);
    }

    @Test
    void isCurrentTracksGeneration() {
        long gen = client.currentGeneration();
        assertTrue(client.isCurrent(gen));
        assertFalse(client.isCurrent(gen - 1));
        client.close();
        assertFalse(client.isCurrent(gen));
    }

    private static final class RecordingListener implements SetPlayRelayClient.Listener {
        int disconnected;
        final List<String> closed = new ArrayList<>();
        final List<String> hellos = new ArrayList<>();
        final List<Map<String, Object>> states = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

        @Override
        public void onConnected() {
        }

        @Override
        public void onDisconnected() {
            disconnected++;
        }

        @Override
        public void onClosed(int code, String reason) {
            closed.add(reason);
            onDisconnected();
        }

        @Override
        public void onHello(String role) {
            hellos.add(role);
        }

        @Override
        public void onStateReceived(Map<String, Object> data) {
            states.add(data);
        }

        @Override
        public void onError(String message) {
            errors.add(message);
        }
    }
}

package com.aevoreth.abcmm.domain.setplay.relay;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HTTP client for Set Play Cloudflare relay (create room).
 * Mirrors Python {@code set_play_relay_http}.
 */
public final class SetPlayRelayHttp {

    private static final String USER_AGENT =
            "ABC-Music-Manager/1.0 (Set Play relay client; desktop)";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;

    public SetPlayRelayHttp() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build());
    }

    public SetPlayRelayHttp(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public record RoomCredentials(String roomCode, String leaderToken) {
    }

    /**
     * POST {@code /api/rooms} → room code + leader token.
     *
     * @throws IOException on transport / HTTP / parse failure
     */
    public RoomCredentials createRelayRoom(String baseUrl) throws IOException, InterruptedException {
        String origin = SetPlayShareUrls.relayHttpsOrigin(baseUrl);
        URI uri = URI.create(origin + "/api/rooms");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new IOException("Relay request failed: " + ex.getMessage(), ex);
        }

        int code = response.statusCode();
        String body = response.body() == null ? "" : response.body();
        if (code < 200 || code >= 300) {
            String hint = "";
            if (code == 403) {
                hint = " Cloudflare (or a network filter) may be blocking non-browser clients. "
                        + "In Cloudflare Dashboard → Security, try lowering Bot Fight Mode for this zone, "
                        + "or add a WAF exception for POST /api/rooms on your worker. "
                        + "Also confirm the relay URL is only the worker host "
                        + "(e.g. wss://….workers.dev), not a path under a different app.";
            }
            String snippet = body.length() > 800 ? body.substring(0, 800) : body;
            throw new IOException("HTTP " + code + "." + hint + " Body: " + snippet);
        }

        Map<String, Object> data = MAPPER.readValue(body, new TypeReference<>() {
        });
        Object roomCode = data.get("roomCode");
        Object leaderToken = data.get("leaderToken");
        if (roomCode == null || leaderToken == null) {
            throw new IOException("Relay response missing roomCode/leaderToken: " + body);
        }
        return new RoomCredentials(String.valueOf(roomCode), String.valueOf(leaderToken));
    }
}

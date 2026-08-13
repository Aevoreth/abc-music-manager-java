package com.aevoreth.abcmm.domain.setplay.relay;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HTTP client for the Set Play Cloudflare relay (D1 sessions + R2 zips).
 */
public final class SetPlayRelayHttp {

    public static final int MAX_ZIP_BYTES = 2 * 1024 * 1024;

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

    public record SessionSummary(
            String code,
            String name,
            Long setlistId,
            String setName,
            String notes,
            String setDate,
            String setTime,
            boolean zipAvailable,
            String expiresAt) {
    }

    public record CreateResult(String roomCode, String passphrase, String name) {
    }

    public List<SessionSummary> listSessions(String baseUrl, String relayToken)
            throws IOException, InterruptedException {
        HttpResponse<String> response = send(baseUrl, "/api/sessions", "GET", relayToken, null, null);
        Map<String, Object> data = parseObject(response.body());
        List<SessionSummary> out = new ArrayList<>();
        Object raw = data.get("sessions");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    out.add(mapSession(map));
                }
            }
        }
        return out;
    }

    public CreateResult createSession(
            String baseUrl,
            String relayToken,
            String name,
            Long setlistId,
            String setName,
            String notes,
            String setDate,
            String setTime,
            Map<String, Object> state) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("setlistId", setlistId);
        body.put("setName", setName);
        body.put("notes", notes);
        body.put("setDate", setDate);
        body.put("setTime", setTime);
        body.put("state", state);
        HttpResponse<String> response = send(
                baseUrl, "/api/sessions", "POST", relayToken, MAPPER.writeValueAsString(body), null);
        Map<String, Object> data = parseObject(response.body());
        return new CreateResult(
                String.valueOf(data.get("roomCode")),
                String.valueOf(data.get("passphrase")),
                String.valueOf(data.getOrDefault("name", name)));
    }

    public void renameSession(String baseUrl, String relayToken, String code, String name)
            throws IOException, InterruptedException {
        Map<String, Object> body = Map.of("name", name);
        send(baseUrl, "/api/sessions/" + code, "PATCH", relayToken, MAPPER.writeValueAsString(body), null);
    }

    public void republishSession(
            String baseUrl,
            String relayToken,
            String code,
            Long setlistId,
            String setName,
            String notes,
            String setDate,
            String setTime,
            Map<String, Object> state) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("republish", true);
        body.put("setlistId", setlistId);
        body.put("setName", setName);
        body.put("notes", notes);
        body.put("setDate", setDate);
        body.put("setTime", setTime);
        body.put("state", state);
        send(baseUrl, "/api/sessions/" + code, "PATCH", relayToken, MAPPER.writeValueAsString(body), null);
    }

    public void clearSession(String baseUrl, String relayToken, String code)
            throws IOException, InterruptedException {
        send(baseUrl, "/api/sessions/" + code + "/clear", "POST", relayToken, "{}", null);
    }

    public void deleteSession(String baseUrl, String relayToken, String code)
            throws IOException, InterruptedException {
        send(baseUrl, "/api/sessions/" + code, "DELETE", relayToken, null, null);
    }

    public void uploadZip(
            String baseUrl, String relayToken, String code, byte[] zipBytes, String expiresAtIso)
            throws IOException, InterruptedException {
        if (zipBytes.length > MAX_ZIP_BYTES) {
            throw new IOException("Zip is larger than 2 MB.");
        }
        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("Content-Type", "application/zip");
        if (expiresAtIso != null && !expiresAtIso.isBlank()) {
            extra.put("X-Expires-At", expiresAtIso);
        }
        sendBytes(baseUrl, "/api/sessions/" + code + "/zip", "PUT", relayToken, zipBytes, extra);
    }

    public byte[] downloadZip(String baseUrl, String code, String passphrase)
            throws IOException, InterruptedException {
        String origin = SetPlayShareUrls.relayHttpsOrigin(baseUrl);
        URI uri = URI.create(origin + "/api/sessions/" + code + "/zip");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/zip")
                .header("User-Agent", USER_AGENT)
                .header("X-Zip-Passphrase", passphrase == null ? "" : passphrase)
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " downloading zip.");
        }
        return response.body() == null ? new byte[0] : response.body();
    }

    private HttpResponse<String> send(
            String baseUrl,
            String path,
            String method,
            String relayToken,
            String jsonBody,
            Map<String, String> extraHeaders) throws IOException, InterruptedException {
        String origin = SetPlayShareUrls.relayHttpsOrigin(baseUrl);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(origin + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .header("Authorization", "Bearer " + (relayToken == null ? "" : relayToken.strip()));
        if (extraHeaders != null) {
            extraHeaders.forEach(builder::header);
        }
        HttpRequest.BodyPublisher pub = jsonBody == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(jsonBody);
        if (jsonBody != null) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, pub);
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        requireOk(response.statusCode(), response.body());
        return response;
    }

    private void sendBytes(
            String baseUrl,
            String path,
            String method,
            String relayToken,
            byte[] body,
            Map<String, String> extraHeaders) throws IOException, InterruptedException {
        String origin = SetPlayShareUrls.relayHttpsOrigin(baseUrl);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(origin + path))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", USER_AGENT)
                .header("Authorization", "Bearer " + (relayToken == null ? "" : relayToken.strip()));
        extraHeaders.forEach(builder::header);
        builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        requireOk(response.statusCode(), response.body());
    }

    private static void requireOk(int code, String body) throws IOException {
        if (code < 200 || code >= 300) {
            String snippet = body == null ? "" : (body.length() > 800 ? body.substring(0, 800) : body);
            String hint = "";
            if (code == 403) {
                hint = " Cloudflare Bot Fight Mode or WAF may be blocking this client. "
                        + "Prefer *.workers.dev, or skip Bot Fight for /api/* on a custom domain.";
            }
            throw new IOException("HTTP " + code + "." + hint + " Body: " + snippet);
        }
    }

    private static Map<String, Object> parseObject(String body) throws IOException {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        return MAPPER.readValue(body, new TypeReference<>() {
        });
    }

    private static SessionSummary mapSession(Map<?, ?> map) {
        return new SessionSummary(
                str(map.get("code")),
                str(map.get("name")),
                toLong(map.get("setlistId")),
                str(map.get("setName")),
                str(map.get("notes")),
                str(map.get("setDate")),
                str(map.get("setTime")),
                Boolean.TRUE.equals(map.get("zipAvailable")) || "true".equalsIgnoreCase(String.valueOf(map.get("zipAvailable"))),
                str(map.get("expiresAt")));
    }

    private static String str(Object value) {
        return value == null || "null".equals(String.valueOf(value)) ? null : String.valueOf(value);
    }

    private static Long toLong(Object value) {
        if (value == null || "null".equals(String.valueOf(value))) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

package com.aevoreth.abcmm.domain.setplay.relay;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * Build and parse Set Play share links ({@code /playback?set=CODE}).
 * Mirrors Python {@code set_play_share_url} / relay URL helpers.
 */
public final class SetPlayShareUrls {

    private SetPlayShareUrls() {
    }

    public record ParsedShareLink(String relayWsUrl, String roomCode, String passphrase) {
        public ParsedShareLink(String relayWsUrl, String roomCode) {
            this(relayWsUrl, roomCode, null);
        }
    }

    /** Strip accidental {@code /api/rooms} suffix if user pasted a full API path. */
    public static String normalizeRelayBaseUrl(String url) {
        String u = (url == null ? "" : url).strip().replaceAll("/+$", "");
        String low = u.toLowerCase(Locale.ROOT);
        for (String suffix : new String[]{"/api/rooms/ws", "/api/rooms"}) {
            if (low.endsWith(suffix)) {
                u = u.substring(0, u.length() - suffix.length()).replaceAll("/+$", "");
                low = u.toLowerCase(Locale.ROOT);
            }
        }
        return u;
    }

    /** Worker origin (https) for REST calls — no trailing slash. */
    public static String relayHttpsOrigin(String url) {
        String u = normalizeRelayBaseUrl(url);
        if (u.startsWith("wss://")) {
            return "https://" + u.substring(6);
        }
        if (u.startsWith("https://")) {
            return u;
        }
        if (u.startsWith("http://")) {
            return u;
        }
        if (u.startsWith("ws://")) {
            return "http://" + u.substring(5);
        }
        return "https://" + u;
    }

    /** Worker origin as wss:// (or ws://) — no trailing slash. */
    public static String relayWsOrigin(String url) {
        String https = relayHttpsOrigin(url);
        if (https.startsWith("https://")) {
            return "wss://" + https.substring(8);
        }
        if (https.startsWith("http://")) {
            return "ws://" + https.substring(7);
        }
        return "wss://" + https;
    }

    public static String buildPlaybackShareUrl(String relayBaseUrl, String roomCode) {
        String origin = relayHttpsOrigin(relayBaseUrl);
        String code = (roomCode == null ? "" : roomCode).strip().toUpperCase(Locale.ROOT);
        return origin + "/playback?set=" + code;
    }

    public static String buildDownloadShareUrl(String relayBaseUrl, String roomCode, String passphrase) {
        String base = buildPlaybackShareUrl(relayBaseUrl, roomCode);
        String pin = passphrase == null ? "" : passphrase.strip();
        if (pin.isEmpty()) {
            return base;
        }
        return base + "#p=" + pin;
    }

    public static String httpsToWssWorkerUrl(String httpsUrl) {
        String u = (httpsUrl == null ? "" : httpsUrl).strip().replaceAll("/+$", "");
        if (u.startsWith("https://")) {
            return "wss://" + u.substring(8);
        }
        if (u.startsWith("http://")) {
            return "ws://" + u.substring(7);
        }
        return u;
    }

    /**
     * Parse a playback share URL or a bare room code.
     *
     * @param fallbackRelayUrl required for bare codes (active Settings relay)
     */
    public static Optional<ParsedShareLink> parseShareOrCode(String text, String fallbackRelayUrl) {
        String raw = text == null ? "" : text.strip();
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        String pinFromBare = null;
        int hash = raw.indexOf('#');
        if (hash >= 0 && !looksLikeUrl(raw)) {
            pinFromBare = extractPinFromFragment(raw.substring(hash + 1));
            raw = raw.substring(0, hash).strip();
        }

        if (looksLikeUrl(raw)) {
            try {
                URI parsed = URI.create(normalizeUrlForParse(raw));
                if (parsed.getHost() == null || parsed.getHost().isBlank()) {
                    return Optional.empty();
                }
                String code = extractCodeFromQuery(parsed.getRawQuery());
                if (code.isEmpty()) {
                    code = extractCodeFromRoomsPath(parsed.getPath());
                }
                if (code.length() < 5) {
                    return Optional.empty();
                }
                String scheme = parsed.getScheme();
                boolean https = scheme == null
                        || scheme.equalsIgnoreCase("https")
                        || scheme.equalsIgnoreCase("wss")
                        || scheme.isBlank();
                String origin = (https ? "https://" : "http://") + parsed.getHost()
                        + (parsed.getPort() > 0 ? ":" + parsed.getPort() : "");
                String pin = extractPinFromFragment(parsed.getFragment());
                return Optional.of(new ParsedShareLink(relayWsOrigin(origin), code, pin));
            } catch (IllegalArgumentException ex) {
                return Optional.empty();
            }
        }

        StringBuilder codeBuf = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = Character.toUpperCase(raw.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                codeBuf.append(c);
            }
        }
        String code = codeBuf.toString();
        if (code.length() < 5) {
            return Optional.empty();
        }
        String base = normalizeRelayBaseUrl(fallbackRelayUrl);
        if (base.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ParsedShareLink(relayWsOrigin(base), code, pinFromBare));
    }

    private static String extractPinFromFragment(String fragment) {
        if (fragment == null || fragment.isBlank()) {
            return null;
        }
        String raw = fragment.strip();
        if (raw.regionMatches(true, 0, "p=", 0, 2)) {
            String val = raw.substring(2).strip();
            return val.isEmpty() ? null : val;
        }
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            if ("p".equalsIgnoreCase(part.substring(0, eq))) {
                String val = part.substring(eq + 1).strip();
                return val.isEmpty() ? null : val;
            }
        }
        return null;
    }

    private static boolean looksLikeUrl(String text) {
        String low = text.toLowerCase(Locale.ROOT);
        return low.startsWith("http://")
                || low.startsWith("https://")
                || low.startsWith("ws://")
                || low.startsWith("wss://");
    }

    private static String normalizeUrlForParse(String text) {
        String low = text.toLowerCase(Locale.ROOT);
        if (low.startsWith("wss://")) {
            return "https://" + text.substring(6);
        }
        if (low.startsWith("ws://")) {
            return "http://" + text.substring(5);
        }
        return text;
    }

    private static String extractCodeFromQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        for (String part : rawQuery.split("&")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8);
            if ("set".equalsIgnoreCase(key) || "code".equalsIgnoreCase(key)) {
                String val = URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8).strip();
                if (!val.isEmpty()) {
                    return val.toUpperCase(Locale.ROOT);
                }
            }
        }
        return "";
    }

    private static String extractCodeFromRoomsPath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            if ("rooms".equalsIgnoreCase(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1].strip().toUpperCase(Locale.ROOT);
            }
        }
        return "";
    }
}

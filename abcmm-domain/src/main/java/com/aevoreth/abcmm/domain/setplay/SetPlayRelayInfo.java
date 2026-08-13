package com.aevoreth.abcmm.domain.setplay;

/**
 * Named Cloudflare relay stored in SQLite (schema v13+).
 */
public record SetPlayRelayInfo(
        long id,
        String name,
        String url,
        String token,
        int retentionDays,
        int sortOrder) {

    public static final int DEFAULT_RETENTION_DAYS = 14;

    public String tokenOrEmpty() {
        return token == null ? "" : token;
    }

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    public String normalizedUrl() {
        return url == null ? "" : url.strip().replaceAll("/+$", "");
    }
}

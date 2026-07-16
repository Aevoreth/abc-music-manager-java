package com.aevoreth.abcmm.storage;

import java.time.Instant;

/**
 * Shared timestamp helper for SQLite {@code created_at} / {@code updated_at} columns.
 */
public final class SqliteTimestamps {

    private SqliteTimestamps() {
    }

    public static String now() {
        return Instant.now().toString();
    }
}

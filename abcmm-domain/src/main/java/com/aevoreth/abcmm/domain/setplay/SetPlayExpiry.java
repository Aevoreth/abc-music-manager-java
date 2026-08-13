package com.aevoreth.abcmm.domain.setplay;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Zip expiry: session date/time in America/New_York plus relay retention days.
 */
public final class SetPlayExpiry {

    public static final ZoneId ZONE = ZoneId.of("America/New_York");

    private SetPlayExpiry() {
    }

    public static ZonedDateTime sessionDateTime(LocalDate date, LocalTime time) {
        LocalDate d = date == null ? LocalDate.now(ZONE).plusDays(7) : date;
        LocalTime t = time == null ? LocalTime.of(19, 0) : time;
        return ZonedDateTime.of(d, t, ZONE);
    }

    public static String expiresAtIso(LocalDate date, LocalTime time, int retentionDays) {
        int days = Math.max(1, retentionDays);
        return sessionDateTime(date, time).plusDays(days).toInstant().toString();
    }
}

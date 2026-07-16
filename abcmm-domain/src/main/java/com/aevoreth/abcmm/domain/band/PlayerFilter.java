package com.aevoreth.abcmm.domain.band;

import java.util.List;

/**
 * Optional filters for {@link PlayerRepository#listPlayers(PlayerFilter)}.
 * Null / empty fields mean "no filter" for that dimension.
 */
public record PlayerFilter(
        String nameSubstring,
        Integer levelMin,
        Integer levelMax,
        String classSubstring,
        List<Long> instrumentIds) {

    public static PlayerFilter none() {
        return new PlayerFilter(null, null, null, null, null);
    }

    public boolean isEmpty() {
        return blank(nameSubstring)
                && levelMin == null
                && levelMax == null
                && blank(classSubstring)
                && (instrumentIds == null || instrumentIds.isEmpty());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

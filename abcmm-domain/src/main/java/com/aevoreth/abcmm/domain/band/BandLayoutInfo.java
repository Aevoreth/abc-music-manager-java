package com.aevoreth.abcmm.domain.band;

/**
 * Band layout row from the {@code BandLayout} table.
 */
public record BandLayoutInfo(
        long id,
        long bandId,
        String name,
        String exportColumnOrderJson,
        int sortOrder) {
}

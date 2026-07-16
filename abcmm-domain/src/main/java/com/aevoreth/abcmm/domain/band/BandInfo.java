package com.aevoreth.abcmm.domain.band;

/**
 * Band row from the {@code Band} table.
 */
public record BandInfo(long id, String name, String notes, int sortOrder) {
}

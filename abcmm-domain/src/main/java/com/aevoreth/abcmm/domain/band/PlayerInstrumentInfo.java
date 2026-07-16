package com.aevoreth.abcmm.domain.band;

/**
 * Player possession/proficiency for one instrument.
 */
public record PlayerInstrumentInfo(
        long instrumentId,
        String instrumentName,
        boolean hasInstrument,
        boolean hasProficiency,
        String notes) {
}

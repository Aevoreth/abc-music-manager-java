package com.aevoreth.abcmm.domain.band;

/**
 * Player card placement on a band layout grid.
 */
public record BandLayoutSlotInfo(
        long id,
        long bandLayoutId,
        long playerId,
        String playerName,
        int x,
        int y,
        int widthUnits,
        int heightUnits) {
}

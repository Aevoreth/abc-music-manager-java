package com.aevoreth.abcmm.domain.optimizer;

/**
 * One setlist assignment override row. A null {@code partNumber} is an explicit
 * idle override. A player who does not appear in the list has no row, so the
 * song-layout assignment remains the fallback.
 */
public record AssignmentOverride(long setlistItemId, long playerId, Integer partNumber) {

    public AssignmentOverride {
        if (setlistItemId <= 0) {
            throw new IllegalArgumentException("setlistItemId");
        }
        if (playerId <= 0) {
            throw new IllegalArgumentException("playerId");
        }
        if (partNumber != null && partNumber <= 0) {
            throw new IllegalArgumentException("partNumber");
        }
    }
}

package com.aevoreth.abcmm.domain.setlist;

/**
 * Per-setlist-item player → part override.
 */
public record SetlistBandAssignmentInfo(
        long id,
        long setlistItemId,
        long playerId,
        Integer partNumber) {
}

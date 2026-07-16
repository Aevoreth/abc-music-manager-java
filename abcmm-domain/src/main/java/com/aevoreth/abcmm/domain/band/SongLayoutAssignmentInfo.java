package com.aevoreth.abcmm.domain.band;

/**
 * Player → part assignment within a song layout. {@code partNumber} may be null (no part).
 */
public record SongLayoutAssignmentInfo(
        long id,
        long songLayoutId,
        long playerId,
        Integer partNumber) {
}

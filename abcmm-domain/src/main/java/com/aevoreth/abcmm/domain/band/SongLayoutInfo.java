package com.aevoreth.abcmm.domain.band;

/**
 * Per-song part layout scoped to a band layout.
 */
public record SongLayoutInfo(long id, long songId, long bandLayoutId, String name) {
}

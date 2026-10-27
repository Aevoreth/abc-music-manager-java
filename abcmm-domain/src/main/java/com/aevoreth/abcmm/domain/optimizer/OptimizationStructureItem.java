package com.aevoreth.abcmm.domain.optimizer;

/**
 * Setlist item fields that make an optimization stale: identity, order, song,
 * linked song layout, and the song's parts text. Change duration and notes are omitted.
 */
public record OptimizationStructureItem(
        long id,
        int position,
        long songId,
        Long songLayoutId,
        String partsText) {

    public OptimizationStructureItem {
        if (id <= 0) {
            throw new IllegalArgumentException("id");
        }
        if (songId <= 0) {
            throw new IllegalArgumentException("songId");
        }
        if (songLayoutId != null && songLayoutId <= 0) {
            throw new IllegalArgumentException("songLayoutId");
        }
        partsText = partsText == null ? "" : partsText;
    }
}

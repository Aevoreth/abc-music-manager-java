package com.aevoreth.abcmm.optimizer;

/**
 * One required part on one setlist-item occurrence.
 */
public record OccurrencePart(long setlistItemId, int partNumber) {

    public OccurrencePart {
        if (setlistItemId <= 0) {
            throw new IllegalArgumentException("setlistItemId");
        }
        if (partNumber <= 0) {
            throw new IllegalArgumentException("partNumber");
        }
    }
}

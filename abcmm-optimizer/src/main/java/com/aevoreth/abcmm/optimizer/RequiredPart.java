package com.aevoreth.abcmm.optimizer;

/**
 * One required ABC part on a setlist-item occurrence.
 * {@code instrumentId} is null when the part has no resolvable physical instrument.
 */
public record RequiredPart(int partNumber, Long instrumentId) {

    public RequiredPart {
        if (partNumber <= 0) {
            throw new IllegalArgumentException("partNumber");
        }
    }
}

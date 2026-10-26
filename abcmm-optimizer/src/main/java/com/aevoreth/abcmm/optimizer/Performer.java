package com.aevoreth.abcmm.optimizer;

import java.util.Set;

/**
 * Active layout performer. {@code ownedInstrumentIds} contains only instruments
 * with {@code has_instrument = true}. Proficiency is ignored.
 */
public record Performer(long playerId, Set<Long> ownedInstrumentIds) {

    public Performer {
        if (playerId <= 0) {
            throw new IllegalArgumentException("playerId");
        }
        ownedInstrumentIds = Set.copyOf(ownedInstrumentIds);
    }
}

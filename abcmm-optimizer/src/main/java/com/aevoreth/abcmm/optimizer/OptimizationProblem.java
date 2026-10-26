package com.aevoreth.abcmm.optimizer;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable optimizer input for one setlist. Names, titles, and account data are omitted
 * so a report can be printed without exposing a private library.
 */
public record OptimizationProblem(
        long setlistId,
        boolean locked,
        long bandLayoutId,
        List<Performer> performers,
        List<SongOccurrence> occurrences,
        List<BaselineAssignment> baseline,
        List<Pin> pins,
        Map<Long, Set<Long>> equivalentInstrumentIds) {

    public OptimizationProblem {
        if (setlistId <= 0) {
            throw new IllegalArgumentException("setlistId");
        }
        if (bandLayoutId <= 0) {
            throw new IllegalArgumentException("bandLayoutId");
        }
        performers = List.copyOf(performers);
        occurrences = List.copyOf(occurrences);
        baseline = List.copyOf(baseline);
        pins = List.copyOf(pins);
        equivalentInstrumentIds = copyEquivalence(equivalentInstrumentIds);
    }

    public int itemCount() {
        return occurrences.size();
    }

    public int partCount() {
        int count = 0;
        for (SongOccurrence occurrence : occurrences) {
            count += occurrence.parts().size();
        }
        return count;
    }

    /** Distinct non-null required instrument ids, including ids the catalog does not know. */
    public int instrumentCount() {
        Set<Long> ids = new HashSet<>();
        for (SongOccurrence occurrence : occurrences) {
            for (RequiredPart part : occurrence.parts()) {
                if (part.instrumentId() != null) {
                    ids.add(part.instrumentId());
                }
            }
        }
        return ids.size();
    }

    public int playerCount() {
        return performers.size();
    }

    public int transitionCount() {
        return Math.max(0, occurrences.size() - 1);
    }

    /**
     * Instrument the player is effectively holding on this occurrence.
     * Null means idle, or that the player has no baseline row.
     * A loaded problem has one baseline row per layout player per item, so null from
     * a loader-built problem means idle.
     */
    public Long effectiveInstrumentId(long setlistItemId, long playerId) {
        for (BaselineAssignment assignment : baseline) {
            if (assignment.setlistItemId() == setlistItemId && assignment.playerId() == playerId) {
                return assignment.instrumentId();
            }
        }
        return null;
    }

    public BaselineAssignment baseline(long setlistItemId, long playerId) {
        for (BaselineAssignment assignment : baseline) {
            if (assignment.setlistItemId() == setlistItemId && assignment.playerId() == playerId) {
                return assignment;
            }
        }
        return null;
    }

    private static Map<Long, Set<Long>> copyEquivalence(Map<Long, Set<Long>> source) {
        Objects.requireNonNull(source, "equivalentInstrumentIds");
        Map<Long, Set<Long>> copy = new LinkedHashMap<>();
        for (Map.Entry<Long, Set<Long>> entry : source.entrySet()) {
            copy.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }
}

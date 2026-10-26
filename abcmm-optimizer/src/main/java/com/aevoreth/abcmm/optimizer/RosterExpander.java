package com.aevoreth.abcmm.optimizer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Adds idle performers so the same songs can be scored with a larger roster.
 * Library extras are used first. Any shortfall is filled with synthetic players
 * whose possessions are a partial window of the required instruments.
 */
public final class RosterExpander {

    public static final long SYNTHETIC_ID_START = 1_000_000L;
    public static final String LAYOUT = "layout";
    public static final String LIBRARY_EXTRAS = "library-extras";
    public static final String SYNTHETIC_EXTRAS = "synthetic-extras";
    public static final String MIXED_EXTRAS = "library-extras+synthetic-extras";

    private RosterExpander() {
    }

    /**
     * @param libraryExtras non-layout performers, already ordered by preference.
     *                      Players already on the layout are skipped.
     */
    public static RosterExpansion expand(
            OptimizationProblem problem,
            int targetPlayers,
            List<Performer> libraryExtras) {
        if (problem == null) {
            throw new IllegalArgumentException("problem");
        }
        if (libraryExtras == null) {
            throw new IllegalArgumentException("libraryExtras");
        }
        if (targetPlayers < problem.playerCount()) {
            throw new IllegalArgumentException("targetPlayers");
        }
        if (targetPlayers == problem.playerCount()) {
            return new RosterExpansion(problem, LAYOUT, 0, 0);
        }

        List<Performer> performers = new ArrayList<>(problem.performers());
        Set<Long> ids = new LinkedHashSet<>();
        for (Performer performer : performers) {
            ids.add(performer.playerId());
        }
        int libraryUsed = 0;
        for (Performer extra : libraryExtras) {
            if (performers.size() == targetPlayers) {
                break;
            }
            if (extra == null || !ids.add(extra.playerId())) {
                continue;
            }
            performers.add(extra);
            libraryUsed++;
        }

        List<Long> required = requiredInstruments(problem);
        int syntheticUsed = 0;
        long nextId = SYNTHETIC_ID_START;
        while (performers.size() < targetPlayers) {
            while (!ids.add(nextId)) {
                if (nextId == Long.MAX_VALUE) {
                    throw new IllegalArgumentException("synthetic id");
                }
                nextId++;
            }
            performers.add(new Performer(nextId, syntheticOwned(required, syntheticUsed)));
            syntheticUsed++;
            if (nextId != Long.MAX_VALUE) {
                nextId++;
            }
        }

        List<BaselineAssignment> baseline = new ArrayList<>(problem.baseline());
        for (int index = problem.playerCount(); index < performers.size(); index++) {
            long playerId = performers.get(index).playerId();
            for (SongOccurrence occurrence : problem.occurrences()) {
                baseline.add(new BaselineAssignment(occurrence.setlistItemId(), playerId, null, null));
            }
        }
        OptimizationProblem expanded = new OptimizationProblem(
                problem.setlistId(),
                problem.locked(),
                problem.bandLayoutId(),
                performers,
                problem.occurrences(),
                baseline,
                problem.pins(),
                problem.equivalentInstrumentIds());
        return new RosterExpansion(expanded, tag(libraryUsed, syntheticUsed), libraryUsed, syntheticUsed);
    }

    /**
     * Four or more required instruments use offsets {@code k}, {@code k+1}, and {@code k+3}.
     * Smaller catalogs keep a proper subset so a synthetic player does not own every instrument
     * unless the setlist only demands one.
     */
    static Set<Long> syntheticOwned(List<Long> instruments, int index) {
        int count = instruments.size();
        if (count == 0) {
            return Set.of();
        }
        if (count == 1) {
            return Set.of(instruments.get(0));
        }
        Set<Long> owned = new LinkedHashSet<>();
        if (count >= 4) {
            owned.add(instruments.get(Math.floorMod(index, count)));
            owned.add(instruments.get(Math.floorMod(index + 1, count)));
            owned.add(instruments.get(Math.floorMod(index + 3, count)));
        } else {
            int keep = count - 1;
            for (int offset = 0; offset < keep; offset++) {
                owned.add(instruments.get(Math.floorMod(index + offset, count)));
            }
        }
        return Set.copyOf(owned);
    }

    private static List<Long> requiredInstruments(OptimizationProblem problem) {
        Set<Long> ids = new TreeSet<>();
        for (SongOccurrence occurrence : problem.occurrences()) {
            for (RequiredPart part : occurrence.parts()) {
                if (part.instrumentId() != null) {
                    ids.add(part.instrumentId());
                }
            }
        }
        return new ArrayList<>(ids);
    }

    private static String tag(int libraryUsed, int syntheticUsed) {
        if (libraryUsed > 0 && syntheticUsed > 0) {
            return MIXED_EXTRAS;
        }
        if (libraryUsed > 0) {
            return LIBRARY_EXTRAS;
        }
        if (syntheticUsed > 0) {
            return SYNTHETIC_EXTRAS;
        }
        return LAYOUT;
    }

    public record RosterExpansion(
            OptimizationProblem problem,
            String tag,
            int libraryExtras,
            int syntheticExtras) {
    }
}

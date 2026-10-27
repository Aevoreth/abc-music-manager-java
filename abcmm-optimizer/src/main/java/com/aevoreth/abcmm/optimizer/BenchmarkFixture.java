package com.aevoreth.abcmm.optimizer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tiny public problem for the benchmark harness. Its scores are not a band result.
 */
public final class BenchmarkFixture {

    private BenchmarkFixture() {
    }

    public static OptimizationProblem problem() {
        List<Performer> performers = List.of(
                new Performer(10, Set.of(1L, 2L, 3L)),
                new Performer(11, Set.of(2L, 3L, 4L)),
                new Performer(12, Set.of(1L, 4L, 5L)),
                new Performer(13, Set.of(3L, 5L)));
        List<SongOccurrence> occurrences = List.of(
                song(101, 1L, 2L, 3L),
                song(102, 4L, 5L, 1L),
                song(103, 2L, 3L, 4L),
                song(104, 5L, 1L, 3L));
        List<BaselineAssignment> baseline = List.of(
                play(101, 10, 1, 1L), play(101, 11, 2, 2L), play(101, 13, 3, 3L), idle(101, 12),
                play(102, 11, 1, 4L), play(102, 12, 2, 5L), play(102, 10, 3, 1L), idle(102, 13),
                play(103, 10, 1, 2L), play(103, 13, 2, 3L), play(103, 11, 3, 4L), idle(103, 12),
                play(104, 12, 1, 5L), play(104, 10, 2, 1L), play(104, 13, 3, 3L), idle(104, 11));
        return new OptimizationProblem(
                1,
                false,
                1,
                performers,
                occurrences,
                baseline,
                List.of(),
                equivalence());
    }

    /**
     * Public stress fixture: 12 active parts, three owned instruments per character, optional idle
     * extras.
     */
    public static OptimizationProblem synthetic(int songCount, int playerCount, long seed) {
        if (songCount < 1 || playerCount < 12) {
            throw new IllegalArgumentException("Need songs and at least 12 characters");
        }
        List<Performer> performers = new java.util.ArrayList<>();
        Map<Long, String> instruments = new LinkedHashMap<>();
        for (long id = 1; id <= 8; id++) instruments.put(id, "Synthetic instrument " + id);
        for (int player = 0; player < playerCount; player++) {
            performers.add(
                    new Performer(
                            10 + player,
                            Set.of(
                                    (long) (player % 8 + 1),
                                    (long) ((player + 1) % 8 + 1),
                                    (long) ((player + 3) % 8 + 1))));
        }
        java.util.Random fixture = new java.util.Random(seed);
        List<SongOccurrence> songs = new java.util.ArrayList<>();
        List<BaselineAssignment> baseline = new java.util.ArrayList<>();
        for (int song = 0; song < songCount; song++) {
            List<RequiredPart> parts = new java.util.ArrayList<>();
            for (int player = 0; player < playerCount; player++) {
                if (player < 12) {
                    List<Long> owned =
                            performers.get(player).ownedInstrumentIds().stream().sorted().toList();
                    long instrument = owned.get(fixture.nextInt(owned.size()));
                    parts.add(new RequiredPart(player + 1, instrument));
                    baseline.add(play(100 + song, 10 + player, player + 1, instrument));
                } else {
                    baseline.add(idle(100 + song, 10 + player));
                }
            }
            songs.add(new SongOccurrence(100 + song, 100 + song, song, parts));
        }
        return new OptimizationProblem(
                1,
                false,
                1,
                performers,
                songs,
                baseline,
                List.of(),
                PhysicalInstrumentEquivalence.equivalentIds(instruments));
    }

    private static SongOccurrence song(long itemId, long first, long second, long third) {
        return new SongOccurrence(itemId, itemId, (int) (itemId - 101), List.of(
                new RequiredPart(1, first),
                new RequiredPart(2, second),
                new RequiredPart(3, third)));
    }

    private static BaselineAssignment play(long itemId, long playerId, int part, long instrumentId) {
        return new BaselineAssignment(itemId, playerId, part, instrumentId);
    }

    private static BaselineAssignment idle(long itemId, long playerId) {
        return new BaselineAssignment(itemId, playerId, null, null);
    }

    private static Map<Long, Set<Long>> equivalence() {
        Map<Long, String> names = new LinkedHashMap<>();
        names.put(1L, "Lute");
        names.put(2L, "Harp");
        names.put(3L, "Flute");
        names.put(4L, "Fiddle");
        names.put(5L, "Drum");
        return PhysicalInstrumentEquivalence.equivalentIds(names);
    }
}

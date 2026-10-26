package com.aevoreth.abcmm.optimizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One forward pass. Each song is filled before the next is considered.
 * Pins are placed first. Every other part goes to the eligible player with the
 * smallest incoming switch, then the smallest player id. Idle in the previous
 * song costs nothing. Later songs, roles, and keep are ignored.
 * This is a comparison point, not an optimal assignment.
 */
public final class GreedyBaseline {

    private GreedyBaseline() {
    }

    public static Result assign(OptimizationProblem problem) {
        if (problem == null) {
            throw new IllegalArgumentException("problem");
        }
        List<Performer> byId = new ArrayList<>(problem.performers());
        byId.sort(Comparator.comparingLong(Performer::playerId));
        List<AssignedSlot> slots = new ArrayList<>();
        Map<Long, Long> previous = new HashMap<>();
        boolean hasPrevious = false;
        for (SongOccurrence occurrence : problem.occurrences()) {
            SongChoice choice = fill(problem, occurrence, byId, previous, hasPrevious);
            if (choice.diagnostics != null) {
                return new Result.Failed(choice.diagnostics);
            }
            slots.addAll(choice.slots);
            previous = choice.playing;
            hasPrevious = true;
        }
        Assignment assignment = new Assignment(slots);
        List<Diagnostic> problems = AssignmentValidator.validate(problem, assignment);
        if (!problems.isEmpty()) {
            return new Result.Failed(problems);
        }
        return new Result.Assigned(assignment);
    }

    private static SongChoice fill(
            OptimizationProblem problem,
            SongOccurrence occurrence,
            List<Performer> byId,
            Map<Long, Long> previous,
            boolean hasPrevious) {
        Map<Long, AssignedSlot> slotByPlayer = new HashMap<>();
        Set<Integer> covered = new HashSet<>();
        Set<Long> used = new HashSet<>();
        for (Pin pin : problem.pins()) {
            if (pin.setlistItemId() != occurrence.setlistItemId()) {
                continue;
            }
            RequiredPart required = required(occurrence, pin.partNumber());
            if (required == null) {
                return SongChoice.fail(Diagnostic.baseline(
                        "pin_unknown_part",
                        Diagnostics.detail("item", pin.setlistItemId(), "part", pin.partNumber())));
            }
            Performer performer = performer(problem, pin.playerId());
            if (performer == null) {
                return SongChoice.fail(Diagnostic.baseline(
                        "pin_unknown_player",
                        Diagnostics.detail("item", pin.setlistItemId(), "player", pin.playerId())));
            }
            if (!used.add(pin.playerId()) || !covered.add(pin.partNumber())) {
                return SongChoice.fail(Diagnostic.baseline(
                        "pin_conflict",
                        Diagnostics.detail(
                                "item", pin.setlistItemId(),
                                "part", pin.partNumber(),
                                "player", pin.playerId())));
            }
            if (required.instrumentId() == null
                    || !owns(problem, performer, required.instrumentId())) {
                return SongChoice.fail(Diagnostic.baseline(
                        "unowned_instrument",
                        Diagnostics.detail(
                                "item", pin.setlistItemId(),
                                "part", pin.partNumber(),
                                "player", pin.playerId())));
            }
            slotByPlayer.put(pin.playerId(), slot(occurrence, pin.playerId(), required));
        }

        List<RequiredPart> parts = new ArrayList<>(occurrence.parts());
        parts.sort(Comparator.comparingInt(RequiredPart::partNumber));
        for (RequiredPart part : parts) {
            if (covered.contains(part.partNumber())) {
                continue;
            }
            if (part.instrumentId() == null) {
                return SongChoice.fail(Diagnostic.baseline(
                        "unresolved_instrument",
                        Diagnostics.detail("item", occurrence.setlistItemId(), "part", part.partNumber())));
            }
            Performer best = null;
            int bestCost = Integer.MAX_VALUE;
            for (Performer candidate : byId) {
                if (used.contains(candidate.playerId()) || !owns(problem, candidate, part.instrumentId())) {
                    continue;
                }
                int cost = cost(hasPrevious, previous.get(candidate.playerId()), part.instrumentId(), problem);
                if (best == null || cost < bestCost || (cost == bestCost && candidate.playerId() < best.playerId())) {
                    best = candidate;
                    bestCost = cost;
                }
            }
            if (best == null) {
                return SongChoice.fail(Diagnostic.baseline(
                        "uncovered_part",
                        Diagnostics.detail("item", occurrence.setlistItemId(), "part", part.partNumber())));
            }
            used.add(best.playerId());
            covered.add(part.partNumber());
            slotByPlayer.put(best.playerId(), slot(occurrence, best.playerId(), part));
        }

        List<AssignedSlot> slots = new ArrayList<>();
        Map<Long, Long> playing = new HashMap<>();
        for (Performer performer : problem.performers()) {
            AssignedSlot assigned = slotByPlayer.get(performer.playerId());
            if (assigned == null) {
                slots.add(new AssignedSlot(occurrence.setlistItemId(), performer.playerId(), null, null));
            } else {
                slots.add(assigned);
                playing.put(performer.playerId(), assigned.instrumentId());
            }
        }
        return SongChoice.ready(slots, playing);
    }

    private static int cost(boolean hasPrevious, Long previous, long required, OptimizationProblem problem) {
        if (!hasPrevious || previous == null) {
            return 0;
        }
        if (PhysicalInstrumentEquivalence.sameInstrument(previous, required, problem.equivalentInstrumentIds())) {
            return 0;
        }
        return 1;
    }

    private static boolean owns(OptimizationProblem problem, Performer performer, long instrumentId) {
        return PhysicalInstrumentEquivalence.owns(
                performer.ownedInstrumentIds(),
                instrumentId,
                problem.equivalentInstrumentIds());
    }

    private static AssignedSlot slot(SongOccurrence occurrence, long playerId, RequiredPart part) {
        return new AssignedSlot(occurrence.setlistItemId(), playerId, part.partNumber(), part.instrumentId());
    }

    private static RequiredPart required(SongOccurrence occurrence, int partNumber) {
        for (RequiredPart part : occurrence.parts()) {
            if (part.partNumber() == partNumber) {
                return part;
            }
        }
        return null;
    }

    private static Performer performer(OptimizationProblem problem, long playerId) {
        for (Performer performer : problem.performers()) {
            if (performer.playerId() == playerId) {
                return performer;
            }
        }
        return null;
    }

    private static final class SongChoice {
        private final List<AssignedSlot> slots;
        private final Map<Long, Long> playing;
        private final List<Diagnostic> diagnostics;

        private SongChoice(List<AssignedSlot> slots, Map<Long, Long> playing, List<Diagnostic> diagnostics) {
            this.slots = slots;
            this.playing = playing;
            this.diagnostics = diagnostics;
        }

        private static SongChoice ready(List<AssignedSlot> slots, Map<Long, Long> playing) {
            return new SongChoice(slots, playing, null);
        }

        private static SongChoice fail(Diagnostic diagnostic) {
            return new SongChoice(List.of(), Map.of(), List.of(diagnostic));
        }
    }

    public sealed interface Result permits Result.Assigned, Result.Failed {
        record Assigned(Assignment assignment) implements Result {
        }

        record Failed(List<Diagnostic> diagnostics) implements Result {
            public Failed {
                diagnostics = List.copyOf(diagnostics);
            }
        }
    }
}

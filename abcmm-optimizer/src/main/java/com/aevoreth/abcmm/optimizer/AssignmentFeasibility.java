package com.aevoreth.abcmm.optimizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks problem integrity and whether the baseline assignment can be used as a
 * feasible starting point. Intentional doubling is not supported: each required part
 * is covered once, and each player holds at most one part in a song.
 * Hard constraints are reported, not converted into score penalties.
 */
public final class AssignmentFeasibility {

    private AssignmentFeasibility() {
    }

    public static FeasibilityReport check(OptimizationProblem problem, Integer requestedPlayers) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (requestedPlayers != null && requestedPlayers != problem.playerCount()) {
            diagnostics.add(Diagnostic.integrity(
                    "players_mismatch",
                    Diagnostics.detail(
                            "requested", requestedPlayers,
                            "actual", problem.playerCount())));
        }
        Map<Long, Performer> performers = new HashMap<>();
        for (Performer performer : problem.performers()) {
            performers.put(performer.playerId(), performer);
        }
        Map<Long, SongOccurrence> occurrences = new HashMap<>();
        for (SongOccurrence occurrence : problem.occurrences()) {
            occurrences.put(occurrence.setlistItemId(), occurrence);
            checkParts(problem, occurrence, diagnostics);
            checkBaseline(problem, occurrence, performers, diagnostics);
        }
        checkPins(problem, occurrences, performers.keySet(), diagnostics);
        return new FeasibilityReport(diagnostics);
    }

    private static void checkParts(
            OptimizationProblem problem,
            SongOccurrence occurrence,
            List<Diagnostic> diagnostics) {
        Set<Integer> seen = new HashSet<>();
        for (RequiredPart part : occurrence.parts()) {
            if (!seen.add(part.partNumber())) {
                diagnostics.add(Diagnostic.integrity(
                        "duplicate_part_number",
                        Diagnostics.detail("item", occurrence.setlistItemId(), "part", part.partNumber())));
                continue;
            }
            if (part.instrumentId() == null) {
                diagnostics.add(Diagnostic.integrity(
                        "unresolved_instrument",
                        Diagnostics.detail("item", occurrence.setlistItemId(), "part", part.partNumber())));
            } else if (!problem.equivalentInstrumentIds().containsKey(part.instrumentId())) {
                diagnostics.add(Diagnostic.integrity(
                        "unknown_instrument",
                        Diagnostics.detail(
                                "item", occurrence.setlistItemId(),
                                "part", part.partNumber(),
                                "instrument", part.instrumentId())));
            }
        }
    }

    private static void checkBaseline(
            OptimizationProblem problem,
            SongOccurrence occurrence,
            Map<Long, Performer> performers,
            List<Diagnostic> diagnostics) {
        Map<Long, List<BaselineAssignment>> byPlayer = new LinkedHashMap<>();
        Map<Integer, Integer> coverage = new HashMap<>();
        for (BaselineAssignment assignment : problem.baseline()) {
            if (assignment.setlistItemId() != occurrence.setlistItemId()) {
                continue;
            }
            byPlayer.computeIfAbsent(assignment.playerId(), ignored -> new ArrayList<>()).add(assignment);
            if (assignment.partNumber() != null) {
                coverage.merge(assignment.partNumber(), 1, Integer::sum);
            }
        }
        for (Performer performer : problem.performers()) {
            List<BaselineAssignment> rows = byPlayer.getOrDefault(performer.playerId(), List.of());
            if (rows.isEmpty()) {
                diagnostics.add(Diagnostic.integrity(
                        "missing_baseline",
                        Diagnostics.detail(
                                "item", occurrence.setlistItemId(),
                                "player", performer.playerId())));
                continue;
            }
            if (rows.size() > 1) {
                diagnostics.add(Diagnostic.baseline(
                        "duplicate_player_assignment",
                        Diagnostics.detail(
                                "item", occurrence.setlistItemId(),
                                "player", performer.playerId())));
            }
            for (BaselineAssignment assignment : rows) {
                checkAssignment(problem, occurrence, performer, assignment, diagnostics);
            }
        }
        for (BaselineAssignment assignment : problem.baseline()) {
            if (assignment.setlistItemId() == occurrence.setlistItemId()
                    && !performers.containsKey(assignment.playerId())) {
                diagnostics.add(Diagnostic.baseline(
                        "assignment_unknown_player",
                        Diagnostics.detail(
                                "item", occurrence.setlistItemId(),
                                "player", assignment.playerId())));
            }
        }
        Set<Integer> requiredParts = new HashSet<>();
        for (RequiredPart part : occurrence.parts()) {
            requiredParts.add(part.partNumber());
        }
        for (int partNumber : requiredParts) {
            int assigned = coverage.getOrDefault(partNumber, 0);
            if (assigned == 0) {
                diagnostics.add(Diagnostic.baseline(
                        "uncovered_part",
                        Diagnostics.detail("item", occurrence.setlistItemId(), "part", partNumber)));
            } else if (assigned > 1) {
                diagnostics.add(Diagnostic.baseline(
                        "double_booked",
                        Diagnostics.detail("item", occurrence.setlistItemId(), "part", partNumber)));
            }
        }
    }

    private static void checkAssignment(
            OptimizationProblem problem,
            SongOccurrence occurrence,
            Performer performer,
            BaselineAssignment assignment,
            List<Diagnostic> diagnostics) {
        if (assignment.partNumber() == null) {
            return;
        }
        RequiredPart required = requiredPart(occurrence, assignment.partNumber());
        if (required == null) {
            diagnostics.add(Diagnostic.baseline(
                    "assignment_unknown_part",
                    Diagnostics.detail(
                            "item", occurrence.setlistItemId(),
                            "player", assignment.playerId(),
                            "part", assignment.partNumber())));
            return;
        }
        if (required.instrumentId() == null
                || !problem.equivalentInstrumentIds().containsKey(required.instrumentId())) {
            return;
        }
        if (!PhysicalInstrumentEquivalence.owns(
                performer.ownedInstrumentIds(),
                required.instrumentId(),
                problem.equivalentInstrumentIds())) {
            diagnostics.add(Diagnostic.baseline(
                    "unowned_instrument",
                    Diagnostics.detail(
                            "item", occurrence.setlistItemId(),
                            "part", assignment.partNumber(),
                            "player", assignment.playerId(),
                            "instrument", required.instrumentId())));
        }
    }

    private static void checkPins(
            OptimizationProblem problem,
            Map<Long, SongOccurrence> occurrences,
            Set<Long> playerIds,
            List<Diagnostic> diagnostics) {
        Map<String, Set<Long>> playersByItemPart = new LinkedHashMap<>();
        Map<String, Set<Integer>> partsByItemPlayer = new LinkedHashMap<>();
        for (Pin pin : problem.pins()) {
            playersByItemPart
                    .computeIfAbsent(pin.setlistItemId() + ":" + pin.partNumber(), ignored -> new LinkedHashSet<>())
                    .add(pin.playerId());
            partsByItemPlayer
                    .computeIfAbsent(pin.setlistItemId() + ":" + pin.playerId(), ignored -> new LinkedHashSet<>())
                    .add(pin.partNumber());
        }
        Set<String> contradictoryParts = new HashSet<>();
        Set<String> contradictoryPlayers = new HashSet<>();
        for (Pin pin : problem.pins()) {
            SongOccurrence occurrence = occurrences.get(pin.setlistItemId());
            if (occurrence == null) {
                diagnostics.add(Diagnostic.baseline(
                        "pin_unknown_item",
                        Diagnostics.detail(
                                "item", pin.setlistItemId(),
                                "part", pin.partNumber(),
                                "player", pin.playerId())));
                continue;
            }
            if (requiredPart(occurrence, pin.partNumber()) == null) {
                diagnostics.add(Diagnostic.baseline(
                        "pin_unknown_part",
                        Diagnostics.detail(
                                "item", pin.setlistItemId(),
                                "part", pin.partNumber(),
                                "player", pin.playerId())));
                continue;
            }
            if (!playerIds.contains(pin.playerId())) {
                diagnostics.add(Diagnostic.baseline(
                        "pin_unknown_player",
                        Diagnostics.detail(
                                "item", pin.setlistItemId(),
                                "part", pin.partNumber(),
                                "player", pin.playerId())));
                continue;
            }
            String partKey = pin.setlistItemId() + ":" + pin.partNumber();
            Set<Long> pinnedPlayers = playersByItemPart.get(partKey);
            if (pinnedPlayers.size() > 1) {
                if (contradictoryParts.add(partKey)) {
                    diagnostics.add(Diagnostic.baseline(
                            "pin_conflict",
                            Diagnostics.detail(
                                    "item", pin.setlistItemId(),
                                    "part", pin.partNumber(),
                                    "players", joinIds(pinnedPlayers))));
                }
                continue;
            }
            String playerKey = pin.setlistItemId() + ":" + pin.playerId();
            Set<Integer> pinnedParts = partsByItemPlayer.get(playerKey);
            if (pinnedParts.size() > 1) {
                if (contradictoryPlayers.add(playerKey)) {
                    String parts = pinnedParts.stream()
                            .sorted()
                            .map(String::valueOf)
                            .collect(Collectors.joining(","));
                    diagnostics.add(Diagnostic.baseline(
                            "pin_conflict",
                            Diagnostics.detail(
                                    "item", pin.setlistItemId(),
                                    "player", pin.playerId(),
                                    "parts", parts)));
                }
                continue;
            }
            BaselineAssignment assignment = problem.baseline(pin.setlistItemId(), pin.playerId());
            if (assignment == null
                    || assignment.partNumber() == null
                    || assignment.partNumber() != pin.partNumber()) {
                diagnostics.add(Diagnostic.baseline(
                        "pin_conflict",
                        Diagnostics.detail(
                                "item", pin.setlistItemId(),
                                "part", pin.partNumber(),
                                "player", pin.playerId())));
            }
        }
    }

    private static RequiredPart requiredPart(SongOccurrence occurrence, int partNumber) {
        for (RequiredPart part : occurrence.parts()) {
            if (part.partNumber() == partNumber) {
                return part;
            }
        }
        return null;
    }

    private static String joinIds(Set<Long> ids) {
        return ids.stream().sorted(Comparator.naturalOrder()).map(String::valueOf).collect(Collectors.joining(","));
    }
}

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
 * Hard constraints for one candidate assignment, including the baseline.
 * Intentional doubling is not supported: each required part is covered by exactly
 * one layout performer, and each performer plays at most one part in a song.
 * A later doubling mode would have to be an explicit policy. Extra rows stay
 * duplicates, not a second part. Failures are diagnostics, never score penalties.
 */
public final class AssignmentValidator {

    private AssignmentValidator() {
    }

    public static List<Diagnostic> validate(OptimizationProblem problem, Assignment assignment) {
        return validate(problem, assignment, null);
    }

    public static List<Diagnostic> validate(
            OptimizationProblem problem, Assignment assignment, OptimizationRules rules) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        Map<Long, Performer> performers = new HashMap<>();
        for (Performer performer : problem.performers()) {
            performers.put(performer.playerId(), performer);
        }
        Map<Long, SongOccurrence> occurrences = new HashMap<>();
        for (SongOccurrence occurrence : problem.occurrences()) {
            occurrences.put(occurrence.setlistItemId(), occurrence);
            checkParts(problem, occurrence, diagnostics);
            checkSlots(problem, occurrence, performers, assignment, diagnostics);
        }
        checkPins(problem, occurrences, performers.keySet(), assignment, diagnostics);
        if (rules != null) {
            checkRequiredInstruments(problem, rules, performers.keySet(), assignment, diagnostics);
        }
        return diagnostics;
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

    private static void checkSlots(
            OptimizationProblem problem,
            SongOccurrence occurrence,
            Map<Long, Performer> performers,
            Assignment assignment,
            List<Diagnostic> diagnostics) {
        Map<Long, List<AssignedSlot>> byPlayer = new LinkedHashMap<>();
        Map<Integer, Integer> coverage = new HashMap<>();
        for (AssignedSlot slot : assignment.slots()) {
            if (slot.setlistItemId() != occurrence.setlistItemId()) {
                continue;
            }
            byPlayer.computeIfAbsent(slot.playerId(), ignored -> new ArrayList<>()).add(slot);
            if (slot.partNumber() != null) {
                coverage.merge(slot.partNumber(), 1, Integer::sum);
            }
        }
        for (Performer performer : problem.performers()) {
            List<AssignedSlot> rows = byPlayer.getOrDefault(performer.playerId(), List.of());
            if (rows.isEmpty()) {
                diagnostics.add(Diagnostic.baseline(
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
            for (AssignedSlot slot : rows) {
                checkSlot(problem, occurrence, performer, slot, diagnostics);
            }
        }
        for (AssignedSlot slot : assignment.slots()) {
            if (slot.setlistItemId() == occurrence.setlistItemId()
                    && !performers.containsKey(slot.playerId())) {
                diagnostics.add(Diagnostic.baseline(
                        "assignment_unknown_player",
                        Diagnostics.detail(
                                "item", occurrence.setlistItemId(),
                                "player", slot.playerId())));
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

    private static void checkSlot(
            OptimizationProblem problem,
            SongOccurrence occurrence,
            Performer performer,
            AssignedSlot slot,
            List<Diagnostic> diagnostics) {
        if (slot.partNumber() == null) {
            return;
        }
        RequiredPart required = requiredPart(occurrence, slot.partNumber());
        if (required == null) {
            diagnostics.add(Diagnostic.baseline(
                    "assignment_unknown_part",
                    Diagnostics.detail(
                            "item", occurrence.setlistItemId(),
                            "player", slot.playerId(),
                            "part", slot.partNumber())));
            return;
        }
        if (required.instrumentId() == null
                || !problem.equivalentInstrumentIds().containsKey(required.instrumentId())) {
            return;
        }
        if (slot.instrumentId() != null
                && !PhysicalInstrumentEquivalence.sameInstrument(
                        slot.instrumentId(),
                        required.instrumentId(),
                        problem.equivalentInstrumentIds())) {
            diagnostics.add(Diagnostic.baseline(
                    "assignment_instrument_mismatch",
                    Diagnostics.detail(
                            "item", occurrence.setlistItemId(),
                            "player", slot.playerId(),
                            "part", slot.partNumber(),
                            "instrument", slot.instrumentId(),
                            "required", required.instrumentId())));
        }
        if (!PhysicalInstrumentEquivalence.owns(
                performer.ownedInstrumentIds(),
                required.instrumentId(),
                problem.equivalentInstrumentIds())) {
            diagnostics.add(Diagnostic.baseline(
                    "unowned_instrument",
                    Diagnostics.detail(
                            "item", occurrence.setlistItemId(),
                            "part", slot.partNumber(),
                            "player", slot.playerId(),
                            "instrument", required.instrumentId())));
        }
    }

    private static void checkPins(
            OptimizationProblem problem,
            Map<Long, SongOccurrence> occurrences,
            Set<Long> playerIds,
            Assignment assignment,
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
            AssignedSlot slot = assignment.slot(pin.setlistItemId(), pin.playerId());
            if (slot == null || slot.partNumber() == null || slot.partNumber() != pin.partNumber()) {
                diagnostics.add(Diagnostic.baseline(
                        "pin_conflict",
                        Diagnostics.detail(
                                "item", pin.setlistItemId(),
                                "part", pin.partNumber(),
                                "player", pin.playerId())));
            }
        }
    }

    /**
     * Required instruments fill the parts a song actually has. Extra players at the
     * same priority share those parts, and lower priorities are free.
     */
    private static void checkRequiredInstruments(
            OptimizationProblem problem,
            OptimizationRules rules,
            Set<Long> playerIds,
            Assignment assignment,
            List<Diagnostic> diagnostics) {
        RequiredInstrumentPlan plan = RequiredInstrumentPlan.build(problem, rules);
        List<SongOccurrence> occurrences = problem.occurrences();
        for (int song = 0; song < occurrences.size(); song++) {
            SongOccurrence occurrence = occurrences.get(song);
            int[] partByPlayer = new int[problem.performers().size()];
            java.util.Arrays.fill(partByPlayer, -1);
            for (int player = 0; player < partByPlayer.length; player++) {
                long playerId = problem.performers().get(player).playerId();
                if (!playerIds.contains(playerId)) {
                    continue;
                }
                AssignedSlot slot = assignment.slot(occurrence.setlistItemId(), playerId);
                if (slot == null || slot.partNumber() == null) {
                    continue;
                }
                partByPlayer[player] = partIndex(occurrence, slot.partNumber());
            }
            for (int player = 0; player < partByPlayer.length; player++) {
                if (!plan.mustPlay[song][player]) {
                    continue;
                }
                int part = partByPlayer[player];
                if (part >= 0 && plan.allowedPart[song][player][part]) {
                    continue;
                }
                diagnostics.add(Diagnostic.baseline(
                        "required_instrument",
                        Diagnostics.detail(
                                "item", occurrence.setlistItemId(),
                                "player", problem.performers().get(player).playerId())));
            }
            for (RequiredInstrumentPlan.Claim claim : plan.claims[song]) {
                if (RequiredInstrumentPlan.seated(plan, song, claim, partByPlayer) == claim.seats) {
                    continue;
                }
                diagnostics.add(Diagnostic.baseline(
                        "required_instrument",
                        Diagnostics.detail(
                                "item", occurrence.setlistItemId(),
                                "instrument", claim.instrumentId,
                                "seats", claim.seats)));
            }
        }
    }

    private static int partIndex(SongOccurrence occurrence, int partNumber) {
        List<RequiredPart> parts = occurrence.parts();
        for (int part = 0; part < parts.size(); part++) {
            if (parts.get(part).partNumber() == partNumber) {
                return part;
            }
        }
        return -1;
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

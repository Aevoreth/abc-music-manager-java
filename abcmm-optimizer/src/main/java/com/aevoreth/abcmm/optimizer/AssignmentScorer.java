package com.aevoreth.abcmm.optimizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Adjacent-song score. A player adds one to {@code C_t} only when that player has
 * a part in both songs and the physical instruments differ. Idle on either side
 * adds nothing, including when the last instrument before the idle song was different.
 * The first song has no incoming cost.
 *
 * <p>An assignment or rule set that breaks a hard constraint yields
 * {@link ScoreOutcome.Rejected} and no numeric score.
 */
public final class AssignmentScorer {

    private AssignmentScorer() {
    }

    public static ScoreOutcome score(
            OptimizationProblem problem,
            Assignment assignment,
            OptimizationRules rules) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        diagnostics.addAll(ruleDiagnostics(problem, rules));
        diagnostics.addAll(AssignmentValidator.validate(problem, assignment));
        if (!diagnostics.isEmpty()) {
            return new ScoreOutcome.Rejected(diagnostics);
        }
        return new ScoreOutcome.Accepted(breakdown(problem, assignment, rules));
    }

    private static List<Diagnostic> ruleDiagnostics(OptimizationProblem problem, OptimizationRules rules) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        Map<Long, Performer> performers = new HashMap<>();
        for (Performer performer : problem.performers()) {
            performers.put(performer.playerId(), performer);
        }
        Map<Long, SongOccurrence> occurrences = new HashMap<>();
        for (SongOccurrence occurrence : problem.occurrences()) {
            occurrences.put(occurrence.setlistItemId(), occurrence);
        }
        for (Map.Entry<Long, Double> entry : rules.playerSwitchMultiplier().entrySet()) {
            if (!performers.containsKey(entry.getKey())) {
                diagnostics.add(Diagnostic.integrity(
                        "rule_unknown_player",
                        Diagnostics.detail("player", entry.getKey(), "rule", "switch")));
            }
        }
        checkAffinities(problem, rules, performers, diagnostics);
        for (PartPreference preference : rules.partPreferences()) {
            checkPartReference(
                    occurrences,
                    performers,
                    preference.setlistItemId(),
                    preference.partNumber(),
                    preference.playerId(),
                    "preference",
                    diagnostics);
        }
        for (Long itemId : rules.itemKeepMultiplier().keySet()) {
            if (!occurrences.containsKey(itemId)) {
                diagnostics.add(Diagnostic.integrity(
                        "rule_unknown_item",
                        Diagnostics.detail("item", itemId, "rule", "keep")));
            }
        }
        for (OccurrencePart part : rules.partKeepMultiplier().keySet()) {
            SongOccurrence occurrence = occurrences.get(part.setlistItemId());
            if (occurrence == null) {
                diagnostics.add(Diagnostic.integrity(
                        "rule_unknown_item",
                        Diagnostics.detail("item", part.setlistItemId(), "part", part.partNumber(), "rule", "keep")));
                continue;
            }
            if (requiredPart(occurrence, part.partNumber()) == null) {
                diagnostics.add(Diagnostic.integrity(
                        "rule_unknown_part",
                        Diagnostics.detail("item", part.setlistItemId(), "part", part.partNumber(), "rule", "keep")));
            }
        }
        return diagnostics;
    }

    private static void checkAffinities(
            OptimizationProblem problem,
            OptimizationRules rules,
            Map<Long, Performer> performers,
            List<Diagnostic> diagnostics) {
        Map<Long, List<InstrumentAffinity>> byPlayer = new HashMap<>();
        for (InstrumentAffinity affinity : rules.instrumentAffinities()) {
            if (!performers.containsKey(affinity.playerId())) {
                diagnostics.add(Diagnostic.integrity(
                        "rule_unknown_player",
                        Diagnostics.detail("player", affinity.playerId(), "rule", "affinity")));
            }
            if (!problem.equivalentInstrumentIds().containsKey(affinity.instrumentId())) {
                diagnostics.add(Diagnostic.integrity(
                        "rule_unknown_instrument",
                        Diagnostics.detail(
                                "player", affinity.playerId(),
                                "instrument", affinity.instrumentId())));
            }
            byPlayer.computeIfAbsent(affinity.playerId(), ignored -> new ArrayList<>()).add(affinity);
        }
        for (Map.Entry<Long, List<InstrumentAffinity>> entry : byPlayer.entrySet()) {
            TreeSet<Long> overlapped = new TreeSet<>();
            List<InstrumentAffinity> affinities = entry.getValue();
            for (int i = 0; i < affinities.size(); i++) {
                for (int j = i + 1; j < affinities.size(); j++) {
                    long left = affinities.get(i).instrumentId();
                    long right = affinities.get(j).instrumentId();
                    if (PhysicalInstrumentEquivalence.sameInstrument(
                            left, right, problem.equivalentInstrumentIds())) {
                        overlapped.add(left);
                        overlapped.add(right);
                    }
                }
            }
            if (!overlapped.isEmpty()) {
                diagnostics.add(Diagnostic.integrity(
                        "rule_duplicate_affinity",
                        Diagnostics.detail(
                                "player", entry.getKey(),
                                "instruments", joinIds(overlapped))));
            }
        }
    }

    private static void checkPartReference(
            Map<Long, SongOccurrence> occurrences,
            Map<Long, Performer> performers,
            long itemId,
            int partNumber,
            long playerId,
            String rule,
            List<Diagnostic> diagnostics) {
        SongOccurrence occurrence = occurrences.get(itemId);
        if (occurrence == null) {
            diagnostics.add(Diagnostic.integrity(
                    "rule_unknown_item",
                    Diagnostics.detail("item", itemId, "part", partNumber, "player", playerId, "rule", rule)));
            return;
        }
        if (requiredPart(occurrence, partNumber) == null) {
            diagnostics.add(Diagnostic.integrity(
                    "rule_unknown_part",
                    Diagnostics.detail("item", itemId, "part", partNumber, "player", playerId, "rule", rule)));
        }
        if (!performers.containsKey(playerId)) {
            diagnostics.add(Diagnostic.integrity(
                    "rule_unknown_player",
                    Diagnostics.detail("item", itemId, "part", partNumber, "player", playerId, "rule", rule)));
        }
    }

    private static ScoreBreakdown breakdown(
            OptimizationProblem problem,
            Assignment assignment,
            OptimizationRules rules) {
        Map<Long, Set<Long>> equivalence = problem.equivalentInstrumentIds();
        List<Integer> changes = new ArrayList<>();
        double burden = 0.0d;
        int worst = 0;
        int overload = 0;
        List<SongOccurrence> occurrences = problem.occurrences();
        for (int i = 1; i < occurrences.size(); i++) {
            SongOccurrence previous = occurrences.get(i - 1);
            SongOccurrence current = occurrences.get(i);
            int count = 0;
            for (Performer performer : problem.performers()) {
                Long previousInstrument = playedInstrument(previous, assignment, performer.playerId());
                Long currentInstrument = playedInstrument(current, assignment, performer.playerId());
                if (!switches(previousInstrument, currentInstrument, equivalence)) {
                    continue;
                }
                count++;
                burden += switchMultiplier(rules, performer.playerId());
            }
            changes.add(count);
            if (count > worst) {
                worst = count;
            }
            int excess = count - rules.targetChanges();
            if (excess > 0) {
                overload += excess * excess;
            }
        }

        double roleDeviation = 0.0d;
        Map<String, PartPreference> preferences = new HashMap<>();
        for (PartPreference preference : rules.partPreferences()) {
            preferences.put(preference.setlistItemId() + ":" + preference.partNumber(), preference);
        }
        Map<Long, List<InstrumentAffinity>> affinities = new HashMap<>();
        for (InstrumentAffinity affinity : rules.instrumentAffinities()) {
            affinities.computeIfAbsent(affinity.playerId(), ignored -> new ArrayList<>()).add(affinity);
        }
        for (SongOccurrence occurrence : occurrences) {
            for (RequiredPart part : occurrence.parts()) {
                AssignedSlot slot = playerOnPart(assignment, occurrence.setlistItemId(), part.partNumber());
                if (slot == null || part.instrumentId() == null) {
                    continue;
                }
                PartPreference preference = preferences.get(occurrence.setlistItemId() + ":" + part.partNumber());
                if (preference != null) {
                    if (slot.playerId() != preference.playerId()) {
                        roleDeviation += preference.penalty();
                    }
                    continue;
                }
                roleDeviation += affinityMiss(
                        affinities.get(slot.playerId()), part.instrumentId(), equivalence);
            }
        }

        int changedAssignments = 0;
        double keepDeviation = 0.0d;
        for (SongOccurrence occurrence : occurrences) {
            for (Performer performer : problem.performers()) {
                BaselineAssignment baseline = problem.baseline(occurrence.setlistItemId(), performer.playerId());
                AssignedSlot slot = assignment.slot(occurrence.setlistItemId(), performer.playerId());
                Integer baselinePart = baseline == null ? null : baseline.partNumber();
                Integer candidatePart = slot == null ? null : slot.partNumber();
                if (samePart(baselinePart, candidatePart)) {
                    continue;
                }
                changedAssignments++;
                Integer multiplierPart = baselinePart != null ? baselinePart : candidatePart;
                keepDeviation += keepMultiplier(rules, occurrence.setlistItemId(), multiplierPart);
            }
        }

        int totalChanges = 0;
        Map<Integer, Integer> histogram = new HashMap<>();
        for (int count : changes) {
            totalChanges += count;
            histogram.merge(count, 1, Integer::sum);
        }
        double smoothContribution = rules.smoothWeight() * overload;
        double totalContribution = rules.totalWeight() * burden;
        double roleContribution = rules.roleWeight() * roleDeviation;
        double keepContribution = rules.keepWeight() * keepDeviation;
        double weightedScore = smoothContribution + totalContribution + roleContribution + keepContribution;
        return new ScoreBreakdown(
                rules.targetChanges(),
                changes,
                totalChanges,
                worst,
                overload,
                histogram,
                burden,
                roleDeviation,
                keepDeviation,
                changedAssignments,
                rules.smoothWeight(),
                rules.totalWeight(),
                rules.roleWeight(),
                rules.keepWeight(),
                smoothContribution,
                totalContribution,
                roleContribution,
                keepContribution,
                weightedScore);
    }

    private static boolean switches(Long previous, Long current, Map<Long, Set<Long>> equivalence) {
        return previous != null
                && current != null
                && !PhysicalInstrumentEquivalence.sameInstrument(previous, current, equivalence);
    }

    private static Long playedInstrument(SongOccurrence occurrence, Assignment assignment, long playerId) {
        AssignedSlot slot = assignment.slot(occurrence.setlistItemId(), playerId);
        if (slot == null || slot.partNumber() == null) {
            return null;
        }
        RequiredPart part = requiredPart(occurrence, slot.partNumber());
        if (part == null) {
            return null;
        }
        return part.instrumentId();
    }

    private static AssignedSlot playerOnPart(Assignment assignment, long setlistItemId, int partNumber) {
        for (AssignedSlot slot : assignment.slots()) {
            if (slot.setlistItemId() == setlistItemId
                    && slot.partNumber() != null
                    && slot.partNumber() == partNumber) {
                return slot;
            }
        }
        return null;
    }

    private static double affinityMiss(
            List<InstrumentAffinity> affinities,
            long instrumentId,
            Map<Long, Set<Long>> equivalence) {
        if (affinities == null || affinities.isEmpty()) {
            return 0.0d;
        }
        double highest = 0.0d;
        for (InstrumentAffinity affinity : affinities) {
            if (PhysicalInstrumentEquivalence.sameInstrument(affinity.instrumentId(), instrumentId, equivalence)) {
                return 0.0d;
            }
            if (affinity.missPenalty() > highest) {
                highest = affinity.missPenalty();
            }
        }
        return highest;
    }

    private static double switchMultiplier(OptimizationRules rules, long playerId) {
        Double player = rules.playerSwitchMultiplier().get(playerId);
        return player != null ? player : rules.defaultSwitchMultiplier();
    }

    private static double keepMultiplier(OptimizationRules rules, long itemId, Integer partNumber) {
        if (partNumber != null) {
            Double part = rules.partKeepMultiplier().get(new OccurrencePart(itemId, partNumber));
            if (part != null) {
                return part;
            }
        }
        Double item = rules.itemKeepMultiplier().get(itemId);
        return item != null ? item : 1.0d;
    }

    private static boolean samePart(Integer left, Integer right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.intValue() == right.intValue();
    }

    private static RequiredPart requiredPart(SongOccurrence occurrence, int partNumber) {
        for (RequiredPart part : occurrence.parts()) {
            if (part.partNumber() == partNumber) {
                return part;
            }
        }
        return null;
    }

    private static String joinIds(TreeSet<Long> ids) {
        StringBuilder text = new StringBuilder();
        for (Long id : ids) {
            if (text.length() > 0) {
                text.append(',');
            }
            text.append(id);
        }
        return text.toString();
    }
}

package com.aevoreth.abcmm.optimizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Before-and-after view of one solved assignment. Raw switch counts stay separate
 * from the weighted score. A rejected assignment leaves that side's breakdown null
 * and still reports switch counts and changed parts.
 */
public record AssignmentComparison(
        ScoreBreakdown before,
        ScoreBreakdown after,
        Map<Long, Integer> beforeSwitches,
        Map<Long, Integer> afterSwitches,
        List<SlotChange> changedSlots) {

    /**
     * One layout player's part on one setlist item. A null part is idle.
     */
    public record SlotChange(
            long setlistItemId,
            long playerId,
            Integer beforePart,
            Integer afterPart) {
    }

    public AssignmentComparison {
        beforeSwitches = copyCounts(beforeSwitches);
        afterSwitches = copyCounts(afterSwitches);
        changedSlots = List.copyOf(changedSlots);
    }

    public static AssignmentComparison compare(
            OptimizationProblem problem,
            Assignment beforeAssignment,
            Assignment afterAssignment,
            OptimizationRules rules) {
        Objects.requireNonNull(problem, "problem");
        Objects.requireNonNull(beforeAssignment, "beforeAssignment");
        Objects.requireNonNull(afterAssignment, "afterAssignment");
        Objects.requireNonNull(rules, "rules");
        return new AssignmentComparison(
                accepted(problem, beforeAssignment, rules),
                accepted(problem, afterAssignment, rules),
                AssignmentScorer.switchesByPlayer(problem, beforeAssignment),
                AssignmentScorer.switchesByPlayer(problem, afterAssignment),
                changes(problem, beforeAssignment, afterAssignment));
    }

    /** Transitions whose raw change count is strictly above the score's target. */
    public static int aboveTarget(ScoreBreakdown breakdown) {
        if (breakdown == null) {
            return 0;
        }
        int count = 0;
        for (int changes : breakdown.changesPerTransition()) {
            if (changes > breakdown.target()) {
                count++;
            }
        }
        return count;
    }

    private static ScoreBreakdown accepted(
            OptimizationProblem problem,
            Assignment assignment,
            OptimizationRules rules) {
        ScoreOutcome scored = AssignmentScorer.score(problem, assignment, rules);
        if (scored instanceof ScoreOutcome.Accepted accepted) {
            return accepted.breakdown();
        }
        return null;
    }

    private static List<SlotChange> changes(
            OptimizationProblem problem,
            Assignment beforeAssignment,
            Assignment afterAssignment) {
        List<SlotChange> changed = new ArrayList<>();
        for (SongOccurrence occurrence : problem.occurrences()) {
            for (Performer performer : problem.performers()) {
                Integer beforePart = part(beforeAssignment, occurrence.setlistItemId(), performer.playerId());
                Integer afterPart = part(afterAssignment, occurrence.setlistItemId(), performer.playerId());
                if (Objects.equals(beforePart, afterPart)) {
                    continue;
                }
                changed.add(new SlotChange(
                        occurrence.setlistItemId(), performer.playerId(), beforePart, afterPart));
            }
        }
        return changed;
    }

    private static Integer part(Assignment assignment, long setlistItemId, long playerId) {
        AssignedSlot slot = assignment.slot(setlistItemId, playerId);
        return slot == null ? null : slot.partNumber();
    }

    private static Map<Long, Integer> copyCounts(Map<Long, Integer> counts) {
        Objects.requireNonNull(counts, "counts");
        return Map.copyOf(new LinkedHashMap<>(counts));
    }
}

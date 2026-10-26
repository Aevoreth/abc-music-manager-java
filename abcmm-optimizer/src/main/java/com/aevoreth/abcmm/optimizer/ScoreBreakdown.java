package com.aevoreth.abcmm.optimizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic metrics for one accepted assignment. {@code weightedScore} is the
 * sum of the four contributions. Raw counts stay integers when a player multiplier
 * or a keep multiplier changes the weighted terms. {@code worstTransition} is reported
 * and is not part of the weighted score.
 */
public record ScoreBreakdown(
        int target,
        List<Integer> changesPerTransition,
        int totalChanges,
        int worstTransition,
        int overload,
        Map<Integer, Integer> histogram,
        double weightedSwitchBurden,
        double roleDeviation,
        double keepDeviation,
        int changedAssignments,
        double smoothWeight,
        double totalWeight,
        double roleWeight,
        double keepWeight,
        double smoothContribution,
        double totalContribution,
        double roleContribution,
        double keepContribution,
        double weightedScore) {

    public ScoreBreakdown {
        changesPerTransition = List.copyOf(Objects.requireNonNull(changesPerTransition, "changesPerTransition"));
        histogram = Map.copyOf(Objects.requireNonNull(histogram, "histogram"));
    }

    /**
     * Id-free text for the same fields the scorer stored. {@code weighted_score}
     * is the sum of the four contribution lines.
     */
    public String format() {
        StringBuilder text = new StringBuilder();
        line(text, "changes", Integer.toString(totalChanges));
        line(text, "worst_transition", Integer.toString(worstTransition));
        line(text, "overload", Integer.toString(overload));
        line(text, "target", Integer.toString(target));
        line(text, "weighted_switch_burden", Double.toString(weightedSwitchBurden));
        line(text, "role_deviation", Double.toString(roleDeviation));
        line(text, "keep_deviation", Double.toString(keepDeviation));
        line(text, "changed_assignments", Integer.toString(changedAssignments));
        line(text, "smooth_weight", Double.toString(smoothWeight));
        line(text, "total_weight", Double.toString(totalWeight));
        line(text, "role_weight", Double.toString(roleWeight));
        line(text, "keep_weight", Double.toString(keepWeight));
        line(text, "smooth_contribution", Double.toString(smoothContribution));
        line(text, "total_contribution", Double.toString(totalContribution));
        line(text, "role_contribution", Double.toString(roleContribution));
        line(text, "keep_contribution", Double.toString(keepContribution));
        line(text, "weighted_score", Double.toString(weightedScore));
        text.append("histogram: ").append(histogramText()).append('\n');
        for (int i = 0; i < changesPerTransition.size(); i++) {
            text.append("transition: ")
                    .append(i)
                    .append(" changes=")
                    .append(changesPerTransition.get(i))
                    .append('\n');
        }
        return text.toString();
    }

    private String histogramText() {
        if (histogram.isEmpty()) {
            return "none";
        }
        List<Integer> keys = new ArrayList<>(histogram.keySet());
        keys.sort(Comparator.naturalOrder());
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                text.append(',');
            }
            Integer key = keys.get(i);
            text.append(key).append('=').append(histogram.get(key));
        }
        return text.toString();
    }

    private static void line(StringBuilder text, String label, String value) {
        text.append(label).append(": ").append(value).append('\n');
    }
}

package com.aevoreth.abcmm.optimizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable assignment and its incremental score. Totals are re-summed in the same
 * song, part, player, and transition order as {@link AssignmentScorer}.
 */
final class CandidateState {

    final ProblemIndex index;
    final int[][] partByPlayer;
    final int[][] playerByPart;
    final double[][] switchContribution;
    final int[] transitionChanges;
    final double[][] roleContribution;
    final double[][] keepContribution;
    final int[][] keepChanged;

    int totalChanges;
    int worst;
    int overload;
    double burden;
    double roleDeviation;
    double keepDeviation;
    int changedAssignments;
    double weightedScore;

    private CandidateState(ProblemIndex index) {
        this.index = index;
        int songs = index.songs;
        int players = index.players;
        int transitions = Math.max(0, songs - 1);
        partByPlayer = new int[songs][players];
        playerByPart = new int[songs][];
        roleContribution = new double[songs][];
        keepContribution = new double[songs][players];
        keepChanged = new int[songs][players];
        for (int song = 0; song < songs; song++) {
            Arrays.fill(partByPlayer[song], -1);
            playerByPart[song] = new int[index.partCounts[song]];
            Arrays.fill(playerByPart[song], -1);
            roleContribution[song] = new double[index.partCounts[song]];
        }
        switchContribution = new double[transitions][players];
        transitionChanges = new int[transitions];
    }

    static CandidateState create(ProblemIndex index) {
        return new CandidateState(index);
    }

    static CandidateState fromAssignment(ProblemIndex index, Assignment assignment) {
        CandidateState state = create(index);
        for (int song = 0; song < index.songs; song++) {
            for (int player = 0; player < index.players; player++) {
                AssignedSlot slot = assignment.slot(index.itemIds[song], index.playerIds[player]);
                state.partByPlayer[song][player] =
                        slot.partNumber() == null ? -1 : index.partIndex(song, slot.partNumber());
            }
            state.rebuild(song);
        }
        state.rescoreAll();
        return state;
    }

    CandidateState copy() {
        CandidateState copy = create(index);
        for (int song = 0; song < index.songs; song++) {
            copy.partByPlayer[song] = Arrays.copyOf(partByPlayer[song], partByPlayer[song].length);
            copy.playerByPart[song] = Arrays.copyOf(playerByPart[song], playerByPart[song].length);
            copy.roleContribution[song] = Arrays.copyOf(roleContribution[song], roleContribution[song].length);
            copy.keepContribution[song] = Arrays.copyOf(keepContribution[song], keepContribution[song].length);
            copy.keepChanged[song] = Arrays.copyOf(keepChanged[song], keepChanged[song].length);
        }
        for (int transition = 0; transition < transitionChanges.length; transition++) {
            copy.switchContribution[transition] = Arrays.copyOf(
                    switchContribution[transition], switchContribution[transition].length);
            copy.transitionChanges[transition] = transitionChanges[transition];
        }
        copy.totalChanges = totalChanges;
        copy.worst = worst;
        copy.overload = overload;
        copy.burden = burden;
        copy.roleDeviation = roleDeviation;
        copy.keepDeviation = keepDeviation;
        copy.changedAssignments = changedAssignments;
        copy.weightedScore = weightedScore;
        return copy;
    }

    int[] snapshot(int song) {
        return Arrays.copyOf(partByPlayer[song], partByPlayer[song].length);
    }

    void restore(int song, int[] parts) {
        System.arraycopy(parts, 0, partByPlayer[song], 0, parts.length);
        rebuild(song);
    }

    void rebuild(int song) {
        Arrays.fill(playerByPart[song], -1);
        for (int player = 0; player < index.players; player++) {
            int part = partByPlayer[song][player];
            if (part >= 0) {
                playerByPart[song][part] = player;
            }
        }
    }

    void rescoreAll() {
        for (int song = 0; song < index.songs; song++) {
            recomputeRole(song);
            recomputeKeep(song);
        }
        for (int transition = 0; transition < transitionChanges.length; transition++) {
            recomputeTransition(transition);
        }
        recomputeAggregates();
    }

    /** Recomputes song {@code song} and only the transitions that touch it. */
    void rescoreSong(int song) {
        recomputeRole(song);
        recomputeKeep(song);
        if (song > 0) recomputeTransition(song - 1);
        if (song + 1 < index.songs) recomputeTransition(song);
        // Preserve canonical floating-point summation, including fractional weights.
        recomputeAggregates();
    }

    void rescoreSongs(int... songs) {
        boolean[] seen = new boolean[index.songs];
        boolean[] transitions = new boolean[transitionChanges.length];
        for (int song : songs) {
            if (seen[song]) {
                continue;
            }
            seen[song] = true;
            recomputeRole(song);
            recomputeKeep(song);
            if (song > 0) {
                transitions[song - 1] = true;
            }
            if (song < index.songs - 1) {
                transitions[song] = true;
            }
        }
        for (int transition = 0; transition < transitions.length; transition++) {
            if (transitions[transition]) {
                recomputeTransition(transition);
            }
        }
        recomputeAggregates();
    }

    ScoreBreakdown breakdown() {
        List<Integer> changes = new ArrayList<>(transitionChanges.length);
        Map<Integer, Integer> histogram = new HashMap<>();
        for (int count : transitionChanges) {
            changes.add(count);
            histogram.merge(count, 1, Integer::sum);
        }
        double smoothContribution = index.rules.smoothWeight() * overload;
        double totalContribution = index.rules.totalWeight() * burden;
        double roleContributionTotal = index.rules.roleWeight() * roleDeviation;
        double keepContributionTotal = index.rules.keepWeight() * keepDeviation;
        return new ScoreBreakdown(
                index.rules.targetChanges(),
                changes,
                totalChanges,
                worst,
                overload,
                histogram,
                burden,
                roleDeviation,
                keepDeviation,
                changedAssignments,
                index.rules.smoothWeight(),
                index.rules.totalWeight(),
                index.rules.roleWeight(),
                index.rules.keepWeight(),
                smoothContribution,
                totalContribution,
                roleContributionTotal,
                keepContributionTotal,
                weightedScore);
    }

    Assignment toAssignment() {
        List<AssignedSlot> slots = new ArrayList<>();
        for (int song = 0; song < index.songs; song++) {
            for (int player = 0; player < index.players; player++) {
                int part = partByPlayer[song][player];
                if (part < 0) {
                    slots.add(new AssignedSlot(index.itemIds[song], index.playerIds[player], null, null));
                } else {
                    slots.add(new AssignedSlot(
                            index.itemIds[song],
                            index.playerIds[player],
                            index.partNumbers[song][part],
                            index.instrumentIds[song][part]));
                }
            }
        }
        return new Assignment(slots);
    }

    private void recomputeRole(int song) {
        for (int part = 0; part < index.partCounts[song]; part++) {
            int player = playerByPart[song][part];
            if (player < 0 || index.instrumentIds[song][part] == null) {
                roleContribution[song][part] = 0.0d;
                continue;
            }
            if (index.hasPreference[song][part]) {
                roleContribution[song][part] = player == index.preferencePlayer[song][part]
                        ? 0.0d
                        : index.preferencePenalty[song][part];
            } else {
                roleContribution[song][part] = index.affinityMiss[song][part][player];
            }
        }
    }

    private void recomputeKeep(int song) {
        for (int player = 0; player < index.players; player++) {
            Integer baselinePart = index.baselinePartNumber[song][player];
            int candidate = partByPlayer[song][player];
            Integer candidatePart = candidate < 0 ? null : index.partNumbers[song][candidate];
            if (samePart(baselinePart, candidatePart)) {
                keepContribution[song][player] = 0.0d;
                keepChanged[song][player] = 0;
                continue;
            }
            keepChanged[song][player] = 1;
            Integer lookup = baselinePart != null ? baselinePart : candidatePart;
            keepContribution[song][player] = index.keepMultiplier(song, lookup);
        }
    }

    private void recomputeTransition(int transition) {
        int count = 0;
        for (int player = 0; player < index.players; player++) {
            Long previous = playedInstrument(transition, player);
            Long current = playedInstrument(transition + 1, player);
            if (!switches(previous, current)) {
                switchContribution[transition][player] = 0.0d;
                continue;
            }
            count++;
            switchContribution[transition][player] = index.switchMultiplier[player];
        }
        transitionChanges[transition] = count;
    }

    private void recomputeAggregates() {
        double nextBurden = 0.0d;
        int nextChanges = 0;
        int nextWorst = 0;
        int nextOverload = 0;
        int target = index.rules.targetChanges();
        for (int transition = 0; transition < transitionChanges.length; transition++) {
            for (int player = 0; player < index.players; player++) {
                nextBurden += switchContribution[transition][player];
            }
            int count = transitionChanges[transition];
            nextChanges += count;
            if (count > nextWorst) {
                nextWorst = count;
            }
            int excess = count - target;
            if (excess > 0) {
                nextOverload += excess * excess;
            }
        }
        double nextRole = 0.0d;
        for (int song = 0; song < index.songs; song++) {
            for (int part = 0; part < index.partCounts[song]; part++) {
                nextRole += roleContribution[song][part];
            }
        }
        double nextKeep = 0.0d;
        int nextChanged = 0;
        for (int song = 0; song < index.songs; song++) {
            for (int player = 0; player < index.players; player++) {
                nextKeep += keepContribution[song][player];
                nextChanged += keepChanged[song][player];
            }
        }
        burden = nextBurden;
        totalChanges = nextChanges;
        worst = nextWorst;
        overload = nextOverload;
        roleDeviation = nextRole;
        keepDeviation = nextKeep;
        changedAssignments = nextChanged;
        weightedScore = index.rules.smoothWeight() * overload
                + index.rules.totalWeight() * burden
                + index.rules.roleWeight() * roleDeviation
                + index.rules.keepWeight() * keepDeviation;
    }

    private Long playedInstrument(int song, int player) {
        int part = partByPlayer[song][player];
        if (part < 0) {
            return null;
        }
        return index.instrumentIds[song][part];
    }

    private boolean switches(Long previous, Long current) {
        return previous != null
                && current != null
                && !PhysicalInstrumentEquivalence.sameInstrument(
                        previous, current, index.problem.equivalentInstrumentIds());
    }

    private static boolean samePart(Integer left, Integer right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.intValue() == right.intValue();
    }
}

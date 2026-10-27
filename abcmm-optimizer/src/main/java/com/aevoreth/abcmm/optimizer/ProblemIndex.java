package com.aevoreth.abcmm.optimizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compact lookup tables for one problem and one rule set. Built only after rule
 * and structural diagnostics are known to be empty. Part indexes follow each
 * occurrence's part list, not part-number order.
 */
final class ProblemIndex {

    final OptimizationProblem problem;
    final OptimizationRules rules;
    final int songs;
    final int players;
    final long[] playerIds;
    final long[] itemIds;
    final int[] partCounts;
    final int[][] partNumbers;
    final Long[][] instrumentIds;
    final boolean[][][] owns;
    final int[][] pinnedPlayer;
    final int[][] pinnedPart;
    /** True when this player must take one of {@link #allowedPart} on this song. */
    final boolean[][] mustPlay;
    /** Parts that satisfy a required instrument. Indexed for strict players and tied claims. */
    final boolean[][][] allowedPart;
    /** Tied players of one priority, of whom exactly {@code seats} must take the instrument. */
    final RequiredInstrumentPlan.Claim[][] requiredClaims;
    final boolean[] baselineSongLegal;
    final int[][] baselinePart;
    final Integer[][] baselinePartNumber;
    final double[] switchMultiplier;
    final double[] itemKeep;
    final boolean[][] hasPreference;
    final int[][] preferencePlayer;
    final double[][] preferencePenalty;
    final double[][][] affinityMiss;
    final Map<OccurrencePart, Double> partKeep;

    private ProblemIndex(
            OptimizationProblem problem,
            OptimizationRules rules,
            long[] playerIds,
            long[] itemIds,
            int[] partCounts,
            int[][] partNumbers,
            Long[][] instrumentIds,
            boolean[][][] owns,
            int[][] pinnedPlayer,
            int[][] pinnedPart,
            boolean[][] mustPlay,
            boolean[][][] allowedPart,
            RequiredInstrumentPlan.Claim[][] requiredClaims,
            boolean[] baselineSongLegal,
            int[][] baselinePart,
            Integer[][] baselinePartNumber,
            double[] switchMultiplier,
            double[] itemKeep,
            boolean[][] hasPreference,
            int[][] preferencePlayer,
            double[][] preferencePenalty,
            double[][][] affinityMiss) {
        this.problem = problem;
        this.rules = rules;
        this.songs = itemIds.length;
        this.players = playerIds.length;
        this.playerIds = playerIds;
        this.itemIds = itemIds;
        this.partCounts = partCounts;
        this.partNumbers = partNumbers;
        this.instrumentIds = instrumentIds;
        this.owns = owns;
        this.pinnedPlayer = pinnedPlayer;
        this.pinnedPart = pinnedPart;
        this.mustPlay = mustPlay;
        this.allowedPart = allowedPart;
        this.requiredClaims = requiredClaims;
        this.baselineSongLegal = baselineSongLegal;
        this.baselinePart = baselinePart;
        this.baselinePartNumber = baselinePartNumber;
        this.switchMultiplier = switchMultiplier;
        this.itemKeep = itemKeep;
        this.hasPreference = hasPreference;
        this.preferencePlayer = preferencePlayer;
        this.preferencePenalty = preferencePenalty;
        this.affinityMiss = affinityMiss;
        this.partKeep = rules.partKeepMultiplier();
    }

    static ProblemIndex build(OptimizationProblem problem, OptimizationRules rules) {
        int songs = problem.occurrences().size();
        int players = problem.performers().size();
        long[] playerIds = new long[players];
        Map<Long, Integer> playerIndex = new HashMap<>();
        for (int player = 0; player < players; player++) {
            playerIds[player] = problem.performers().get(player).playerId();
            playerIndex.put(playerIds[player], player);
        }
        long[] itemIds = new long[songs];
        Map<Long, Integer> songIndex = new HashMap<>();
        int[] partCounts = new int[songs];
        int[][] partNumbers = new int[songs][];
        Long[][] instrumentIds = new Long[songs][];
        boolean[][][] owns = new boolean[songs][][];
        for (int song = 0; song < songs; song++) {
            SongOccurrence occurrence = problem.occurrences().get(song);
            itemIds[song] = occurrence.setlistItemId();
            songIndex.putIfAbsent(itemIds[song], song);
            List<RequiredPart> parts = occurrence.parts();
            partCounts[song] = parts.size();
            partNumbers[song] = new int[parts.size()];
            instrumentIds[song] = new Long[parts.size()];
            owns[song] = new boolean[parts.size()][players];
            for (int part = 0; part < parts.size(); part++) {
                RequiredPart required = parts.get(part);
                partNumbers[song][part] = required.partNumber();
                instrumentIds[song][part] = required.instrumentId();
                if (required.instrumentId() == null) {
                    continue;
                }
                for (int player = 0; player < players; player++) {
                    owns[song][part][player] = PhysicalInstrumentEquivalence.owns(
                            problem.performers().get(player).ownedInstrumentIds(),
                            required.instrumentId(),
                            problem.equivalentInstrumentIds());
                }
            }
        }

        int[][] pinnedPlayer = fill(songs, partCounts, -1);
        int[][] pinnedPart = constant(songs, players, -1);
        for (Pin pin : problem.pins()) {
            Integer song = songIndex.get(pin.setlistItemId());
            Integer player = playerIndex.get(pin.playerId());
            if (song == null || player == null) {
                continue;
            }
            int part = partIndex(partNumbers[song], pin.partNumber());
            if (part < 0) {
                continue;
            }
            if (pinnedPlayer[song][part] < 0) {
                pinnedPlayer[song][part] = player;
            }
            if (pinnedPart[song][player] < 0) {
                pinnedPart[song][player] = part;
            }
        }

        RequiredInstrumentPlan required = RequiredInstrumentPlan.build(problem, rules);
        boolean[][] mustPlay = required.mustPlay;
        boolean[][][] allowedPart = required.allowedPart;
        RequiredInstrumentPlan.Claim[][] requiredClaims = required.claims;

        int[][] rowCount = new int[songs][players];
        Integer[][] baselinePartNumber = new Integer[songs][players];
        Long[][] baselineInstrument = new Long[songs][players];
        int[][] baselinePart = constant(songs, players, -1);
        for (BaselineAssignment row : problem.baseline()) {
            Integer song = songIndex.get(row.setlistItemId());
            Integer player = playerIndex.get(row.playerId());
            if (song == null || player == null) {
                continue;
            }
            if (rowCount[song][player] == 0) {
                baselinePartNumber[song][player] = row.partNumber();
                baselineInstrument[song][player] = row.instrumentId();
                int part = row.partNumber() == null ? -1 : partIndex(partNumbers[song], row.partNumber());
                baselinePart[song][player] = part;
            }
            rowCount[song][player]++;
        }

        boolean[] baselineSongLegal = new boolean[songs];
        for (int song = 0; song < songs; song++) {
            baselineSongLegal[song] = songLegal(
                    problem,
                    song,
                    playerIndex,
                    partNumbers[song],
                    instrumentIds[song],
                    owns[song],
                    rowCount[song],
                    baselinePart[song],
                    baselineInstrument[song],
                    pinnedPlayer[song],
                    pinnedPart[song],
                    mustPlay[song],
                    allowedPart[song],
                    requiredClaims[song]);
        }

        double[] switchMultiplier = new double[players];
        for (int player = 0; player < players; player++) {
            Double custom = rules.playerSwitchMultiplier().get(playerIds[player]);
            switchMultiplier[player] = custom != null ? custom : rules.defaultSwitchMultiplier();
        }
        double[] itemKeep = new double[songs];
        for (int song = 0; song < songs; song++) {
            Double custom = rules.itemKeepMultiplier().get(itemIds[song]);
            itemKeep[song] = custom != null ? custom : 1.0d;
        }

        boolean[][] hasPreference = new boolean[songs][];
        int[][] preferencePlayer = new int[songs][];
        double[][] preferencePenalty = new double[songs][];
        double[][][] affinityMiss = new double[songs][][];
        Map<Long, List<InstrumentAffinity>> affinities = new HashMap<>();
        for (InstrumentAffinity affinity : rules.instrumentAffinities()) {
            affinities.computeIfAbsent(affinity.playerId(), ignored -> new ArrayList<>()).add(affinity);
        }
        for (int song = 0; song < songs; song++) {
            int parts = partCounts[song];
            hasPreference[song] = new boolean[parts];
            preferencePlayer[song] = new int[parts];
            preferencePenalty[song] = new double[parts];
            affinityMiss[song] = new double[parts][players];
            for (int part = 0; part < parts; part++) {
                preferencePlayer[song][part] = -1;
            }
        }
        for (PartPreference preference : rules.partPreferences()) {
            Integer song = songIndex.get(preference.setlistItemId());
            Integer player = playerIndex.get(preference.playerId());
            if (song == null || player == null) {
                continue;
            }
            int part = partIndex(partNumbers[song], preference.partNumber());
            if (part < 0) {
                continue;
            }
            hasPreference[song][part] = true;
            preferencePlayer[song][part] = player;
            preferencePenalty[song][part] = preference.penalty();
        }
        for (int song = 0; song < songs; song++) {
            for (int part = 0; part < partCounts[song]; part++) {
                Long instrument = instrumentIds[song][part];
                if (instrument == null) {
                    continue;
                }
                for (int player = 0; player < players; player++) {
                    affinityMiss[song][part][player] = affinityMiss(
                            affinities.get(playerIds[player]),
                            instrument,
                            problem.equivalentInstrumentIds());
                }
            }
        }
        return new ProblemIndex(
                problem,
                rules,
                playerIds,
                itemIds,
                partCounts,
                partNumbers,
                instrumentIds,
                owns,
                pinnedPlayer,
                pinnedPart,
                mustPlay,
                allowedPart,
                requiredClaims,
                baselineSongLegal,
                baselinePart,
                baselinePartNumber,
                switchMultiplier,
                itemKeep,
                hasPreference,
                preferencePlayer,
                preferencePenalty,
                affinityMiss);
    }

    int partIndex(int song, int partNumber) {
        return partIndex(partNumbers[song], partNumber);
    }

    double keepMultiplier(int song, Integer partNumber) {
        if (partNumber != null) {
            Double part = partKeep.get(new OccurrencePart(itemIds[song], partNumber));
            if (part != null) {
                return part;
            }
        }
        return itemKeep[song];
    }

    private static boolean songLegal(
            OptimizationProblem problem,
            int song,
            Map<Long, Integer> playerIndex,
            int[] partNumbers,
            Long[] instrumentIds,
            boolean[][] owns,
            int[] rowCount,
            int[] baselinePart,
            Long[] baselineInstrument,
            int[] pinnedPlayer,
            int[] pinnedPart,
            boolean[] mustPlay,
            boolean[][] allowedPart,
            RequiredInstrumentPlan.Claim[] claims) {
        SongOccurrence occurrence = problem.occurrences().get(song);
        for (int player = 0; player < rowCount.length; player++) {
            if (rowCount[player] != 1) {
                return false;
            }
        }
        int[] coverage = new int[partNumbers.length];
        for (int player = 0; player < rowCount.length; player++) {
            int part = baselinePart[player];
            if (part == -1) {
                if (baselinePartNumber(problem, occurrence.setlistItemId(), player, playerIndex) != null) {
                    return false;
                }
                continue;
            }
            if (part < 0) {
                return false;
            }
            coverage[part]++;
            if (!owns[part][player]) {
                return false;
            }
            Long stored = baselineInstrument[player];
            Long required = instrumentIds[part];
            if (stored != null
                    && required != null
                    && !PhysicalInstrumentEquivalence.sameInstrument(
                            stored, required, problem.equivalentInstrumentIds())) {
                return false;
            }
        }
        for (int count : coverage) {
            if (count != 1) {
                return false;
            }
        }
        for (int part = 0; part < pinnedPlayer.length; part++) {
            int player = pinnedPlayer[part];
            if (player >= 0 && baselinePart[player] != part) {
                return false;
            }
        }
        for (int player = 0; player < pinnedPart.length; player++) {
            int part = pinnedPart[player];
            if (part >= 0 && baselinePart[player] != part) {
                return false;
            }
        }
        for (int player = 0; player < mustPlay.length; player++) {
            if (!mustPlay[player]) {
                continue;
            }
            int part = baselinePart[player];
            if (part < 0 || !allowedPart[player][part]) {
                return false;
            }
        }
        for (RequiredInstrumentPlan.Claim claim : claims) {
            int onInstrument = 0;
            for (int player : claim.players) {
                int part = baselinePart[player];
                if (part >= 0 && allowedPart[player][part]) {
                    onInstrument++;
                }
            }
            if (onInstrument != claim.seats) {
                return false;
            }
        }
        for (BaselineAssignment row : problem.baseline()) {
            if (row.setlistItemId() != occurrence.setlistItemId()) {
                continue;
            }
            if (!playerIndex.containsKey(row.playerId())) {
                return false;
            }
        }
        return true;
    }

    private static Integer baselinePartNumber(
            OptimizationProblem problem,
            long itemId,
            int player,
            Map<Long, Integer> playerIndex) {
        long playerId = problem.performers().get(player).playerId();
        for (BaselineAssignment row : problem.baseline()) {
            if (row.setlistItemId() == itemId && row.playerId() == playerId) {
                return row.partNumber();
            }
        }
        return null;
    }

    private static int partIndex(int[] partNumbers, int partNumber) {
        for (int part = 0; part < partNumbers.length; part++) {
            if (partNumbers[part] == partNumber) {
                return part;
            }
        }
        return -1;
    }

    private static double affinityMiss(
            List<InstrumentAffinity> affinities,
            long instrumentId,
            Map<Long, java.util.Set<Long>> equivalence) {
        if (affinities == null || affinities.isEmpty()) {
            return 0.0d;
        }
        double highest = 0.0d;
        for (InstrumentAffinity affinity : affinities) {
            if (PhysicalInstrumentEquivalence.sameInstrument(
                    affinity.instrumentId(), instrumentId, equivalence)) {
                return 0.0d;
            }
            if (affinity.required()) {
                continue;
            }
            if (affinity.missPenalty() > highest) {
                highest = affinity.missPenalty();
            }
        }
        return highest;
    }

    private static int[][] constant(int rows, int columns, int value) {
        int[][] table = new int[rows][columns];
        for (int row = 0; row < rows; row++) {
            java.util.Arrays.fill(table[row], value);
        }
        return table;
    }

    private static int[][] fill(int songs, int[] partCounts, int value) {
        int[][] table = new int[songs][];
        for (int song = 0; song < songs; song++) {
            table[song] = new int[partCounts[song]];
            java.util.Arrays.fill(table[song], value);
        }
        return table;
    }
}

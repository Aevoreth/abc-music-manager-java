package com.aevoreth.abcmm.optimizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides who must take a required instrument on each song. When more players
 * require an instrument than the song has parts for it, the highest priorities
 * take the open parts. Players who share the priority that runs out of parts
 * stay in a claim: that many of them must take it, and the search chooses which.
 * Everyone below that priority is free to play anything else. A part pinned to
 * someone else is not available.
 */
final class RequiredInstrumentPlan {

    final boolean[][] mustPlay;
    final boolean[][][] allowedPart;
    final Claim[][] claims;

    private RequiredInstrumentPlan(boolean[][] mustPlay, boolean[][][] allowedPart, Claim[][] claims) {
        this.mustPlay = mustPlay;
        this.allowedPart = allowedPart;
        this.claims = claims;
    }

    static RequiredInstrumentPlan build(OptimizationProblem problem, OptimizationRules rules) {
        int songs = problem.occurrences().size();
        int players = problem.performers().size();
        Map<Long, Integer> playerIndex = new HashMap<>();
        for (int player = 0; player < players; player++) {
            playerIndex.put(problem.performers().get(player).playerId(), player);
        }
        Map<Long, Integer> songIndex = new HashMap<>();
        int[] partCounts = new int[songs];
        Long[][] instrumentIds = new Long[songs][];
        boolean[][][] owns = new boolean[songs][][];
        for (int song = 0; song < songs; song++) {
            SongOccurrence occurrence = problem.occurrences().get(song);
            songIndex.putIfAbsent(occurrence.setlistItemId(), song);
            List<RequiredPart> parts = occurrence.parts();
            partCounts[song] = parts.size();
            instrumentIds[song] = new Long[parts.size()];
            owns[song] = new boolean[parts.size()][players];
            for (int part = 0; part < parts.size(); part++) {
                RequiredPart required = parts.get(part);
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
        int[][] pinnedPlayer = new int[songs][];
        int[][] pinnedPart = new int[songs][players];
        for (int song = 0; song < songs; song++) {
            pinnedPlayer[song] = new int[partCounts[song]];
            java.util.Arrays.fill(pinnedPlayer[song], -1);
            java.util.Arrays.fill(pinnedPart[song], -1);
        }
        for (Pin pin : problem.pins()) {
            Integer song = songIndex.get(pin.setlistItemId());
            Integer player = playerIndex.get(pin.playerId());
            if (song == null || player == null) {
                continue;
            }
            int part = partIndex(problem.occurrences().get(song), pin.partNumber());
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

        boolean[][] mustPlay = new boolean[songs][players];
        boolean[][][] allowedPart = new boolean[songs][players][];
        Claim[][] claims = new Claim[songs][];
        for (int song = 0; song < songs; song++) {
            for (int player = 0; player < players; player++) {
                allowedPart[song][player] = new boolean[partCounts[song]];
            }
            claims[song] = claimsForSong(
                    problem,
                    rules,
                    song,
                    playerIndex,
                    instrumentIds[song],
                    owns[song],
                    pinnedPlayer[song],
                    pinnedPart[song],
                    mustPlay[song],
                    allowedPart[song]);
        }
        return new RequiredInstrumentPlan(mustPlay, allowedPart, claims);
    }

    static boolean satisfied(RequiredInstrumentPlan plan, int song, int[] partByPlayer) {
        for (int player = 0; player < plan.mustPlay[song].length; player++) {
            if (!plan.mustPlay[song][player]) {
                continue;
            }
            int part = partByPlayer[player];
            if (part < 0 || !plan.allowedPart[song][player][part]) {
                return false;
            }
        }
        for (Claim claim : plan.claims[song]) {
            if (seated(plan, song, claim, partByPlayer) != claim.seats) {
                return false;
            }
        }
        return true;
    }

    static int seated(RequiredInstrumentPlan plan, int song, Claim claim, int[] partByPlayer) {
        int onInstrument = 0;
        for (int player : claim.players) {
            int part = partByPlayer[player];
            if (part >= 0 && plan.allowedPart[song][player][part]) {
                onInstrument++;
            }
        }
        return onInstrument;
    }

    private static Claim[] claimsForSong(
            OptimizationProblem problem,
            OptimizationRules rules,
            int song,
            Map<Long, Integer> playerIndex,
            Long[] instrumentIds,
            boolean[][] owns,
            int[] pinnedPlayer,
            int[] pinnedPart,
            boolean[] mustPlay,
            boolean[][] allowedPart) {
        List<Long> contests = new ArrayList<>();
        for (InstrumentAffinity affinity : rules.instrumentAffinities()) {
            if (!affinity.required() || !playerIndex.containsKey(affinity.playerId())) {
                continue;
            }
            boolean known = false;
            for (Long contest : contests) {
                if (PhysicalInstrumentEquivalence.sameInstrument(
                        contest, affinity.instrumentId(), problem.equivalentInstrumentIds())) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                contests.add(affinity.instrumentId());
            }
        }
        contests.sort(Comparator.naturalOrder());
        boolean[] obligated = new boolean[mustPlay.length];
        List<Claim> claims = new ArrayList<>();
        for (Long contest : contests) {
            List<Integer> parts = new ArrayList<>();
            for (int part = 0; part < instrumentIds.length; part++) {
                if (instrumentIds[part] != null
                        && PhysicalInstrumentEquivalence.sameInstrument(
                                contest, instrumentIds[part], problem.equivalentInstrumentIds())) {
                    parts.add(part);
                }
            }
            if (parts.isEmpty()) {
                continue;
            }
            List<Competitor> competitors = new ArrayList<>();
            for (int player = 0; player < obligated.length; player++) {
                if (obligated[player] || !playsContest(rules, problem, player, contest)) {
                    continue;
                }
                int pinned = pinnedPart[player];
                if (pinned >= 0 && !parts.contains(pinned)) {
                    continue;
                }
                if (!ownsAny(owns, player, parts)) {
                    continue;
                }
                competitors.add(new Competitor(player, priority(rules, problem, player, contest)));
            }
            int slots = 0;
            List<Competitor> forced = new ArrayList<>();
            for (int part : parts) {
                int pinned = pinnedPlayer[part];
                if (pinned < 0) {
                    slots++;
                    continue;
                }
                Competitor holder = find(competitors, pinned);
                if (holder != null) {
                    forced.add(holder);
                }
            }
            competitors.removeAll(forced);
            for (Competitor holder : forced) {
                mark(holder.player, parts, owns, mustPlay, allowedPart, obligated, true);
            }
            competitors.sort(Comparator
                    .comparingDouble(Competitor::priority).reversed()
                    .thenComparingInt(Competitor::player));
            int remaining = slots;
            int index = 0;
            while (index < competitors.size() && remaining > 0) {
                int end = index + 1;
                while (end < competitors.size()
                        && Double.compare(competitors.get(end).priority, competitors.get(index).priority) == 0) {
                    end++;
                }
                int size = end - index;
                if (size <= remaining) {
                    for (int cursor = index; cursor < end; cursor++) {
                        mark(competitors.get(cursor).player, parts, owns, mustPlay, allowedPart, obligated, true);
                    }
                    remaining -= size;
                } else {
                    int[] tied = new int[size];
                    for (int cursor = 0; cursor < size; cursor++) {
                        tied[cursor] = competitors.get(index + cursor).player;
                        mark(tied[cursor], parts, owns, mustPlay, allowedPart, obligated, false);
                    }
                    claims.add(new Claim(contest, tied, remaining));
                    remaining = 0;
                }
                index = end;
            }
        }
        return claims.toArray(Claim[]::new);
    }

    private static boolean playsContest(
            OptimizationRules rules,
            OptimizationProblem problem,
            int player,
            long contest) {
        long playerId = problem.performers().get(player).playerId();
        for (InstrumentAffinity affinity : rules.instrumentAffinities()) {
            if (affinity.required()
                    && affinity.playerId() == playerId
                    && PhysicalInstrumentEquivalence.sameInstrument(
                            contest, affinity.instrumentId(), problem.equivalentInstrumentIds())) {
                return true;
            }
        }
        return false;
    }

    private static double priority(
            OptimizationRules rules,
            OptimizationProblem problem,
            int player,
            long contest) {
        long playerId = problem.performers().get(player).playerId();
        double highest = 0.0d;
        for (InstrumentAffinity affinity : rules.instrumentAffinities()) {
            if (affinity.required()
                    && affinity.playerId() == playerId
                    && PhysicalInstrumentEquivalence.sameInstrument(
                            contest, affinity.instrumentId(), problem.equivalentInstrumentIds())
                    && affinity.missPenalty() > highest) {
                highest = affinity.missPenalty();
            }
        }
        return highest;
    }

    private static boolean ownsAny(boolean[][] owns, int player, List<Integer> parts) {
        for (int part : parts) {
            if (owns[part][player]) {
                return true;
            }
        }
        return false;
    }

    private static Competitor find(List<Competitor> competitors, int player) {
        for (Competitor competitor : competitors) {
            if (competitor.player == player) {
                return competitor;
            }
        }
        return null;
    }

    private static void mark(
            int player,
            List<Integer> parts,
            boolean[][] owns,
            boolean[] mustPlay,
            boolean[][] allowedPart,
            boolean[] obligated,
            boolean strict) {
        obligated[player] = true;
        if (strict) {
            mustPlay[player] = true;
        }
        for (int part : parts) {
            if (owns[part][player]) {
                allowedPart[player][part] = true;
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

    static final class Claim {
        final long instrumentId;
        final int[] players;
        final int seats;

        Claim(long instrumentId, int[] players, int seats) {
            this.instrumentId = instrumentId;
            this.players = players;
            this.seats = seats;
        }
    }

    private record Competitor(int player, double priority) {
    }
}

package com.aevoreth.abcmm.optimizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Builds a feasible start from the baseline. A legal song is copied. Any other
 * song places pins, then players who must take an instrument, then fills the
 * remaining parts. Matching tries the baseline player first.
 */
final class AssignmentRepair {

    private AssignmentRepair() {
    }

    static Result repair(ProblemIndex index) {
        CandidateState state = CandidateState.create(index);
        boolean preserved = true;
        for (int song = 0; song < index.songs; song++) {
            if (index.baselineSongLegal[song]) {
                System.arraycopy(index.baselinePart[song], 0, state.partByPlayer[song], 0, index.players);
                state.rebuild(song);
            } else {
                preserved = false;
                if (!match(index, state, song)) {
                    return Result.failed();
                }
            }
        }
        state.rescoreAll();
        return Result.ready(state, preserved);
    }

    private static boolean match(ProblemIndex index, CandidateState state, int song) {
        int parts = index.partCounts[song];
        int players = index.players;
        int[] playerOfPart = new int[parts];
        int[] partOfPlayer = new int[players];
        Arrays.fill(playerOfPart, -1);
        Arrays.fill(partOfPlayer, -1);
        boolean[] fixed = new boolean[players];
        for (int part = 0; part < parts; part++) {
            int pinned = index.pinnedPlayer[song][part];
            if (pinned < 0) {
                continue;
            }
            if (!index.owns[song][part][pinned] || partOfPlayer[pinned] >= 0) {
                return false;
            }
            playerOfPart[part] = pinned;
            partOfPlayer[pinned] = part;
            fixed[pinned] = true;
        }
        int[] required = new int[players];
        int requiredCount = 0;
        for (int player = 0; player < players; player++) {
            if (!index.mustPlay[song][player]) {
                continue;
            }
            if (fixed[player]) {
                int held = partOfPlayer[player];
                if (held < 0 || !index.allowedPart[song][player][held]) {
                    return false;
                }
                continue;
            }
            required[requiredCount++] = player;
        }
        if (!placeRequired(index, song, required, requiredCount, 0, playerOfPart, partOfPlayer)) {
            return false;
        }
        for (int i = 0; i < requiredCount; i++) {
            fixed[required[i]] = true;
        }
        if (!placeClaims(index, song, playerOfPart, partOfPlayer, fixed)) {
            return false;
        }
        Integer[] order = new Integer[parts];
        for (int part = 0; part < parts; part++) {
            order[part] = part;
        }
        Arrays.sort(order, Comparator
                .comparingInt((Integer part) -> index.partNumbers[song][part])
                .thenComparingInt(part -> part));
        boolean[] seen = new boolean[players];
        for (int part : order) {
            if (playerOfPart[part] >= 0) {
                continue;
            }
            Arrays.fill(seen, false);
            if (!augment(index, song, part, playerOfPart, partOfPlayer, seen, fixed)) {
                return false;
            }
        }
        System.arraycopy(partOfPlayer, 0, state.partByPlayer[song], 0, players);
        state.rebuild(song);
        return true;
    }

    /**
     * Assigns each required player a distinct allowed part. The baseline part is tried
     * first so a player who already holds the instrument stays on that part.
     */
    private static boolean placeRequired(
            ProblemIndex index,
            int song,
            int[] required,
            int requiredCount,
            int cursor,
            int[] playerOfPart,
            int[] partOfPlayer) {
        if (cursor == requiredCount) {
            return true;
        }
        int player = required[cursor];
        for (int part : allowedParts(index, song, player)) {
            if (playerOfPart[part] >= 0) {
                continue;
            }
            playerOfPart[part] = player;
            partOfPlayer[player] = part;
            if (placeRequired(index, song, required, requiredCount, cursor + 1, playerOfPart, partOfPlayer)) {
                return true;
            }
            playerOfPart[part] = -1;
            partOfPlayer[player] = -1;
        }
        return false;
    }

    /**
     * Seats exactly the claimed number of tied players. Earlier players are tried
     * first, so a zero-budget repair is stable. Search may still swap them later.
     */
    private static boolean placeClaims(
            ProblemIndex index,
            int song,
            int[] playerOfPart,
            int[] partOfPlayer,
            boolean[] fixed) {
        for (RequiredInstrumentPlan.Claim claim : index.requiredClaims[song]) {
            if (!placeSeats(index, song, claim.players, claim.seats, 0, 0, playerOfPart, partOfPlayer, fixed)) {
                return false;
            }
        }
        return true;
    }

    private static boolean placeSeats(
            ProblemIndex index,
            int song,
            int[] players,
            int need,
            int cursor,
            int placed,
            int[] playerOfPart,
            int[] partOfPlayer,
            boolean[] fixed) {
        if (placed == need) {
            return true;
        }
        if (cursor >= players.length) {
            return false;
        }
        int player = players[cursor];
        if (!fixed[player]) {
            for (int part : allowedParts(index, song, player)) {
                if (playerOfPart[part] >= 0) {
                    continue;
                }
                playerOfPart[part] = player;
                partOfPlayer[player] = part;
                fixed[player] = true;
                if (placeSeats(
                        index, song, players, need, cursor + 1, placed + 1, playerOfPart, partOfPlayer, fixed)) {
                    return true;
                }
                fixed[player] = false;
                playerOfPart[part] = -1;
                partOfPlayer[player] = -1;
            }
        }
        return placeSeats(index, song, players, need, cursor + 1, placed, playerOfPart, partOfPlayer, fixed);
    }

    private static int[] allowedParts(ProblemIndex index, int song, int player) {
        int baseline = index.baselinePart[song][player];
        List<Integer> parts = new ArrayList<>();
        if (baseline >= 0 && index.allowedPart[song][player][baseline]) {
            parts.add(baseline);
        }
        Integer[] rest = new Integer[index.partCounts[song]];
        for (int part = 0; part < rest.length; part++) {
            rest[part] = part;
        }
        Arrays.sort(rest, Comparator.comparingInt(part -> index.partNumbers[song][part]));
        for (int part : rest) {
            if (part != baseline && index.allowedPart[song][player][part]) {
                parts.add(part);
            }
        }
        int[] order = new int[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            order[i] = parts.get(i);
        }
        return order;
    }

    private static boolean augment(
            ProblemIndex index,
            int song,
            int part,
            int[] playerOfPart,
            int[] partOfPlayer,
            boolean[] seen,
            boolean[] fixed) {
        int preferred = -1;
        for (int player = 0; player < index.players; player++) {
            if (index.baselinePart[song][player] == part) {
                preferred = player;
                break;
            }
        }
        if (preferred >= 0 && assign(index, song, part, preferred, playerOfPart, partOfPlayer, seen, fixed)) {
            return true;
        }
        for (int player = 0; player < index.players; player++) {
            if (player != preferred
                    && assign(index, song, part, player, playerOfPart, partOfPlayer, seen, fixed)) {
                return true;
            }
        }
        return false;
    }

    private static boolean assign(
            ProblemIndex index,
            int song,
            int part,
            int player,
            int[] playerOfPart,
            int[] partOfPlayer,
            boolean[] seen,
            boolean[] fixed) {
        if (seen[player]
                || fixed[player]
                || index.pinnedPart[song][player] >= 0
                || !index.owns[song][part][player]) {
            return false;
        }
        seen[player] = true;
        int holding = partOfPlayer[player];
        if (holding < 0 || augment(index, song, holding, playerOfPart, partOfPlayer, seen, fixed)) {
            partOfPlayer[player] = part;
            playerOfPart[part] = player;
            return true;
        }
        return false;
    }

    record Result(CandidateState state, boolean preservedBaseline, boolean feasible) {
        static Result ready(CandidateState state, boolean preservedBaseline) {
            return new Result(state, preservedBaseline, true);
        }

        static Result failed() {
            return new Result(null, false, false);
        }
    }
}

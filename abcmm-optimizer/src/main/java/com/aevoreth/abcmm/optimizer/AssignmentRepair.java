package com.aevoreth.abcmm.optimizer;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Builds a feasible start from the baseline. A legal song is copied. Any other
 * song is filled by deterministic matching that tries the baseline player first.
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
            if (!augment(index, song, part, playerOfPart, partOfPlayer, seen)) {
                return false;
            }
        }
        System.arraycopy(partOfPlayer, 0, state.partByPlayer[song], 0, players);
        state.rebuild(song);
        return true;
    }

    private static boolean augment(
            ProblemIndex index,
            int song,
            int part,
            int[] playerOfPart,
            int[] partOfPlayer,
            boolean[] seen) {
        int preferred = -1;
        for (int player = 0; player < index.players; player++) {
            if (index.baselinePart[song][player] == part) {
                preferred = player;
                break;
            }
        }
        if (preferred >= 0 && assign(index, song, part, preferred, playerOfPart, partOfPlayer, seen)) {
            return true;
        }
        for (int player = 0; player < index.players; player++) {
            if (player != preferred && assign(index, song, part, player, playerOfPart, partOfPlayer, seen)) {
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
            boolean[] seen) {
        if (seen[player] || index.pinnedPart[song][player] >= 0 || !index.owns[song][part][player]) {
            return false;
        }
        seen[player] = true;
        int holding = partOfPlayer[player];
        if (holding < 0 || augment(index, song, holding, playerOfPart, partOfPlayer, seen)) {
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

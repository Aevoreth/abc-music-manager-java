package com.aevoreth.abcmm.optimizer;

/**
 * Feasible neighborhood. A move that would break ownership or a pin is refused.
 * Pair swaps include active-to-idle rotations. A 3-cycle is the multi-part move.
 * Window moves apply one pair or one cycle to every song in a contiguous range.
 */
final class SearchMoves {

    private SearchMoves() {
    }

    static boolean commitPair(CandidateState state, int song, int first, int second) {
        if (!pairLegal(state, song, first, second)) {
            return false;
        }
        int[] saved = state.snapshot(song);
        double before = state.weightedScore;
        applyPair(state.partByPlayer[song], first, second);
        state.rebuild(song);
        state.rescoreSong(song);
        if (!(state.weightedScore < before)) {
            state.restore(song, saved);
            state.rescoreSong(song);
            return false;
        }
        return true;
    }

    static boolean commitCycle(CandidateState state, int song, int first, int second, int third, int orientation) {
        if (!cycleLegal(state, song, first, second, third, orientation)) {
            return false;
        }
        int[] saved = state.snapshot(song);
        double before = state.weightedScore;
        applyCycle(state.partByPlayer[song], first, second, third, orientation);
        state.rebuild(song);
        state.rescoreSong(song);
        if (!(state.weightedScore < before)) {
            state.restore(song, saved);
            state.rescoreSong(song);
            return false;
        }
        return true;
    }

    static boolean commitPairWindow(CandidateState state, int start, int length, int first, int second) {
        if (!windowChanges(state, start, length, first, second, -1, 0) || !pairWindowAllowed(state, start, length, first, second)) {
            return false;
        }
        return commitWindow(state, start, length, first, second, -1, 0);
    }

    static boolean commitCycleWindow(
            CandidateState state,
            int start,
            int length,
            int first,
            int second,
            int third,
            int orientation) {
        if (!cycleWindowAllowed(state, start, length, first, second, third, orientation)
                || !windowChanges(state, start, length, first, second, third, orientation)) {
            return false;
        }
        return commitWindow(state, start, length, first, second, third, orientation);
    }

    static boolean pairLegal(CandidateState state, int song, int first, int second) {
        return pairAllowed(state, song, first, second) && pairChanges(state.partByPlayer[song], first, second);
    }

    /** Applies a legal pair swap and rescores that song, ignoring whether the score improves. */
    static void applyForcedPair(CandidateState state, int song, int first, int second) {
        applyPair(state.partByPlayer[song], first, second);
        state.rebuild(song);
        state.rescoreSong(song);
    }

    static boolean pairAllowed(CandidateState state, int song, int first, int second) {
        if (first == second || pinned(state, song, first) || pinned(state, song, second)) {
            return false;
        }
        int[] parts = state.partByPlayer[song];
        return receiverOwns(state, song, second, parts[first]) && receiverOwns(state, song, first, parts[second]);
    }

    static boolean cycleLegal(CandidateState state, int song, int first, int second, int third, int orientation) {
        if (!cycleAllowed(state, song, first, second, third, orientation)) {
            return false;
        }
        int[] parts = state.partByPlayer[song];
        int[] next = ArraysCopy(parts);
        applyCycle(next, first, second, third, orientation);
        return changed(parts, next);
    }

    private static boolean commitWindow(
            CandidateState state,
            int start,
            int length,
            int first,
            int second,
            int third,
            int orientation) {
        int[][] saved = new int[length][];
        int[] songs = new int[length];
        for (int offset = 0; offset < length; offset++) {
            songs[offset] = start + offset;
            saved[offset] = state.snapshot(songs[offset]);
        }
        double before = state.weightedScore;
        for (int offset = 0; offset < length; offset++) {
            int[] parts = state.partByPlayer[songs[offset]];
            if (third < 0) {
                applyPair(parts, first, second);
            } else {
                applyCycle(parts, first, second, third, orientation);
            }
            state.rebuild(songs[offset]);
        }
        state.rescoreSongs(songs);
        if (!(state.weightedScore < before)) {
            for (int offset = 0; offset < length; offset++) {
                state.restore(songs[offset], saved[offset]);
            }
            state.rescoreSongs(songs);
            return false;
        }
        return true;
    }

    private static boolean pairWindowAllowed(CandidateState state, int start, int length, int first, int second) {
        for (int song = start; song < start + length; song++) {
            if (!pairAllowed(state, song, first, second)) {
                return false;
            }
        }
        return true;
    }

    private static boolean cycleWindowAllowed(
            CandidateState state,
            int start,
            int length,
            int first,
            int second,
            int third,
            int orientation) {
        for (int song = start; song < start + length; song++) {
            if (!cycleAllowed(state, song, first, second, third, orientation)) {
                return false;
            }
        }
        return true;
    }

    private static boolean windowChanges(
            CandidateState state,
            int start,
            int length,
            int first,
            int second,
            int third,
            int orientation) {
        for (int song = start; song < start + length; song++) {
            int[] parts = state.partByPlayer[song];
            if (third < 0) {
                if (pairChanges(parts, first, second)) {
                    return true;
                }
            } else if (cycleLegal(state, song, first, second, third, orientation)) {
                return true;
            }
        }
        return false;
    }

    private static boolean cycleAllowed(
            CandidateState state,
            int song,
            int first,
            int second,
            int third,
            int orientation) {
        if (first == second || first == third || second == third || orientation < 0 || orientation > 1) {
            return false;
        }
        if (pinned(state, song, first) || pinned(state, song, second) || pinned(state, song, third)) {
            return false;
        }
        int[] parts = state.partByPlayer[song];
        int toFirst = orientation == 0 ? parts[second] : parts[third];
        int toSecond = orientation == 0 ? parts[third] : parts[first];
        int toThird = orientation == 0 ? parts[first] : parts[second];
        return receiverOwns(state, song, first, toFirst)
                && receiverOwns(state, song, second, toSecond)
                && receiverOwns(state, song, third, toThird);
    }

    private static boolean pinned(CandidateState state, int song, int player) {
        return state.index.pinnedPart[song][player] >= 0;
    }

    private static boolean receiverOwns(CandidateState state, int song, int player, int part) {
        return part < 0 || state.index.owns[song][part][player];
    }

    private static boolean pairChanges(int[] parts, int first, int second) {
        return parts[first] != parts[second];
    }

    private static void applyPair(int[] parts, int first, int second) {
        int held = parts[first];
        parts[first] = parts[second];
        parts[second] = held;
    }

    /**
     * Orientation 0 gives each player the next player's part in {@code (first, second, third)}.
     * Orientation 1 gives each player the previous player's part.
     */
    private static void applyCycle(int[] parts, int first, int second, int third, int orientation) {
        int heldFirst = parts[first];
        int heldSecond = parts[second];
        int heldThird = parts[third];
        if (orientation == 0) {
            parts[first] = heldSecond;
            parts[second] = heldThird;
            parts[third] = heldFirst;
        } else {
            parts[first] = heldThird;
            parts[second] = heldFirst;
            parts[third] = heldSecond;
        }
    }

    private static boolean changed(int[] before, int[] after) {
        for (int player = 0; player < before.length; player++) {
            if (before[player] != after[player]) {
                return true;
            }
        }
        return false;
    }

    private static int[] ArraysCopy(int[] parts) {
        return java.util.Arrays.copyOf(parts, parts.length);
    }
}

package com.aevoreth.abcmm.domain.setplay;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure Set Play advance / skip / exclusivity rules. Mirrors Python {@code set_play_state}.
 */
public final class SetPlaySessionRules {

    private SetPlaySessionRules() {
    }

    /**
     * Item whose parts/instruments the band grid should show.
     * Prefers NEXT; if unset, CURRENT (end of set); otherwise the first unskipped row
     * (after Clear session or before the set has started).
     */
    public static Long layoutFocusItemId(SetPlaySessionState state) {
        Objects.requireNonNull(state, "state");
        if (state.nextItemId() != null) {
            return state.nextItemId();
        }
        if (state.currentItemId() != null) {
            return state.currentItemId();
        }
        return scanNextItemId(state.orderItemIds(), state.skippedItemIds(), -1);
    }

    /**
     * First item id after {@code afterIndex} that is not in {@code skipped}. No wrap.
     */
    public static Long scanNextItemId(List<Long> order, Set<Long> skipped, int afterIndex) {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(skipped, "skipped");
        for (int i = afterIndex + 1; i < order.size(); i++) {
            Long iid = order.get(i);
            if (!skipped.contains(iid)) {
                return iid;
            }
        }
        return null;
    }

    private static Long findNextAfterCurrent(SetPlaySessionState state) {
        List<Long> order = state.orderItemIds();
        Set<Long> skipped = state.skippedItemIds();
        Long cur = state.currentItemId();
        if (cur == null) {
            return scanNextItemId(order, skipped, -1);
        }
        int idx = order.indexOf(cur);
        if (idx < 0) {
            return scanNextItemId(order, skipped, -1);
        }
        return scanNextItemId(order, skipped, idx);
    }

    /**
     * If next is missing from order or skipped, set next by scanning after current.
     * Returns true if {@code nextItemId} changed. Does nothing when next is already null.
     */
    public static boolean recomputeNextIfInvalid(SetPlaySessionState state) {
        Long nxt = state.nextItemId();
        if (nxt == null) {
            return false;
        }
        if (!state.orderItemIds().contains(nxt) || state.skippedItemIds().contains(nxt)) {
            Long newN = findNextAfterCurrent(state);
            if (!Objects.equals(newN, nxt)) {
                state.setNextItemId(newN);
                return true;
            }
            return false;
        }
        return false;
    }

    /**
     * Advance one song. Precondition: next must be set; otherwise no-op (returns false).
     * Current (if any) goes to played; previous next becomes current; next becomes first
     * non-skipped row after new current, or null at end of set.
     */
    public static boolean advanceSong(SetPlaySessionState state) {
        if (state.nextItemId() == null) {
            return false;
        }
        List<Long> order = state.orderItemIds();
        Long nxt = state.nextItemId();
        if (!order.contains(nxt)) {
            return false;
        }

        if (state.currentItemId() != null) {
            state.playedItemIds().add(state.currentItemId());
        }

        int curIdx = order.indexOf(nxt);
        if (curIdx < 0) {
            return false;
        }

        state.setCurrentItemId(nxt);
        state.playedItemIds().remove(nxt);
        state.skippedItemIds().remove(nxt);
        state.setNextItemId(scanNextItemId(order, state.skippedItemIds(), curIdx));
        state.bumpRevision();
        return true;
    }

    /** Set current row; null clears. Mutually exclusive with next/skip/played on same row. */
    public static void applyExclusiveCurrent(SetPlaySessionState state, Long itemId) {
        if (Objects.equals(state.currentItemId(), itemId)) {
            return;
        }
        state.setCurrentItemId(itemId);
        if (itemId != null) {
            if (Objects.equals(state.nextItemId(), itemId)) {
                state.setNextItemId(null);
            }
            state.skippedItemIds().remove(itemId);
            state.playedItemIds().remove(itemId);
        }
        state.bumpRevision();
    }

    /** Set next row; null clears. Mutually exclusive with current/skip/played on same row. */
    public static void applyExclusiveNext(SetPlaySessionState state, Long itemId) {
        if (Objects.equals(state.nextItemId(), itemId)) {
            return;
        }
        state.setNextItemId(itemId);
        if (itemId != null) {
            if (Objects.equals(state.currentItemId(), itemId)) {
                state.setCurrentItemId(null);
            }
            state.skippedItemIds().remove(itemId);
            state.playedItemIds().remove(itemId);
        }
        state.bumpRevision();
    }

    /** Toggle session played. When marking played, clear current/next on that row. */
    public static void togglePlayed(SetPlaySessionState state, long itemId) {
        if (state.playedItemIds().contains(itemId)) {
            state.playedItemIds().remove(itemId);
        } else {
            state.playedItemIds().add(itemId);
            if (Objects.equals(state.currentItemId(), itemId)) {
                state.setCurrentItemId(null);
            }
            if (Objects.equals(state.nextItemId(), itemId)) {
                List<Long> order = state.orderItemIds();
                Set<Long> exclude = new HashSet<>(state.skippedItemIds());
                exclude.addAll(state.playedItemIds());
                Long cur = state.currentItemId();
                if (cur == null) {
                    state.setNextItemId(scanNextItemId(order, exclude, -1));
                } else {
                    int idx = order.indexOf(cur);
                    if (idx < 0) {
                        state.setNextItemId(scanNextItemId(order, exclude, -1));
                    } else {
                        state.setNextItemId(scanNextItemId(order, exclude, idx));
                    }
                }
            }
        }
        state.bumpRevision();
    }

    /** Toggle skip. When skipping, clear current/next on that row and rescan next. */
    public static void toggleSkip(SetPlaySessionState state, long itemId) {
        if (state.skippedItemIds().contains(itemId)) {
            state.skippedItemIds().remove(itemId);
        } else {
            state.skippedItemIds().add(itemId);
            boolean clearedNext = false;
            if (Objects.equals(state.currentItemId(), itemId)) {
                state.setCurrentItemId(null);
            }
            if (Objects.equals(state.nextItemId(), itemId)) {
                state.setNextItemId(null);
                clearedNext = true;
            }
            if (clearedNext) {
                state.setNextItemId(findNextAfterCurrent(state));
            } else {
                recomputeNextIfInvalid(state);
            }
        }
        state.bumpRevision();
    }

    /** Status badge text: SKIP &gt; NOW &gt; NEXT &gt; ✓ &gt; blank. */
    public static String statusBadgeText(SetPlaySessionState state, long itemId) {
        if (state.skippedItemIds().contains(itemId)) {
            return "SKIP";
        }
        if (Objects.equals(state.currentItemId(), itemId)) {
            return "NOW";
        }
        if (Objects.equals(state.nextItemId(), itemId)) {
            return "NEXT";
        }
        if (state.playedItemIds().contains(itemId)) {
            return "\u2713";
        }
        return "";
    }
}

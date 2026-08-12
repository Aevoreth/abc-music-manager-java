package com.aevoreth.abcmm.domain.setplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Ports Python {@code tests/test_set_play_state.py}. */
class SetPlaySessionStateTest {

    private static List<Long> ids(long... xs) {
        return java.util.Arrays.stream(xs).boxed().toList();
    }

    private static SetPlaySessionState state(List<Long> order) {
        return new SetPlaySessionState(order);
    }

    @Test
    void scanNextSkipsSkipped() {
        List<Long> order = ids(10, 11, 12, 13);
        Set<Long> skipped = Set.of(11L, 12L);
        assertEquals(13L, SetPlaySessionRules.scanNextItemId(order, skipped, 0));
        assertEquals(10L, SetPlaySessionRules.scanNextItemId(order, skipped, -1));
    }

    @Test
    void scanNextNoneAtEnd() {
        List<Long> order = ids(10, 11);
        assertNull(SetPlaySessionRules.scanNextItemId(order, Set.of(), 1));
    }

    @Test
    void advanceFirstNoPriorCurrent() {
        SetPlaySessionState st = state(ids(1, 2, 3));
        st.setNextItemId(1L);
        assertTrue(SetPlaySessionRules.advanceSong(st));
        assertEquals(1L, st.currentItemId());
        assertTrue(st.playedItemIds().isEmpty());
        assertEquals(2L, st.nextItemId());
    }

    @Test
    void advanceMovesCurrentToPlayed() {
        SetPlaySessionState st = state(ids(1, 2, 3));
        st.setCurrentItemId(1L);
        st.setNextItemId(2L);
        assertTrue(SetPlaySessionRules.advanceSong(st));
        assertTrue(st.playedItemIds().contains(1L));
        assertEquals(2L, st.currentItemId());
        assertEquals(3L, st.nextItemId());
    }

    @Test
    void advanceNoNextIsNoop() {
        SetPlaySessionState st = state(ids(1, 2));
        st.setCurrentItemId(1L);
        st.setNextItemId(null);
        assertFalse(SetPlaySessionRules.advanceSong(st));
        assertEquals(1L, st.currentItemId());
    }

    @Test
    void advanceLastSongClearsNext() {
        SetPlaySessionState st = state(ids(1, 2));
        st.setCurrentItemId(1L);
        st.setNextItemId(2L);
        assertTrue(SetPlaySessionRules.advanceSong(st));
        assertEquals(2L, st.currentItemId());
        assertNull(st.nextItemId());
    }

    @Test
    void advanceSkipsSkippedRowsForNext() {
        SetPlaySessionState st = state(ids(1, 2, 3, 4));
        st.setNextItemId(1L);
        st.skippedItemIds().add(2L);
        assertTrue(SetPlaySessionRules.advanceSong(st));
        assertEquals(1L, st.currentItemId());
        assertEquals(3L, st.nextItemId());
    }

    @Test
    void advanceClearsPlayedOnNewCurrent() {
        SetPlaySessionState st = state(ids(1, 2, 3));
        st.setCurrentItemId(1L);
        st.setNextItemId(2L);
        st.playedItemIds().add(2L);
        assertTrue(SetPlaySessionRules.advanceSong(st));
        assertEquals(2L, st.currentItemId());
        assertFalse(st.playedItemIds().contains(2L));
        assertTrue(st.playedItemIds().contains(1L));
    }

    @Test
    void skipNextTriggersRecompute() {
        SetPlaySessionState st = state(ids(1, 2, 3));
        st.setCurrentItemId(1L);
        st.setNextItemId(2L);
        SetPlaySessionRules.toggleSkip(st, 2L);
        assertEquals(3L, st.nextItemId());
        assertTrue(st.skippedItemIds().contains(2L));
    }

    @Test
    void toggleSkipOnNonNextDoesNotClearNext() {
        SetPlaySessionState st = state(ids(1, 2, 3));
        st.setCurrentItemId(1L);
        st.setNextItemId(2L);
        SetPlaySessionRules.toggleSkip(st, 3L);
        assertEquals(2L, st.nextItemId());
    }

    @Test
    void skipCurrentClearsCurrent() {
        SetPlaySessionState st = state(ids(1, 2, 3));
        st.setCurrentItemId(1L);
        st.setNextItemId(2L);
        SetPlaySessionRules.toggleSkip(st, 1L);
        assertNull(st.currentItemId());
        assertTrue(st.skippedItemIds().contains(1L));
        assertEquals(2L, st.nextItemId());
    }

    @Test
    void recomputeWhenNoForwardCandidate() {
        SetPlaySessionState st = state(ids(1, 2));
        st.setCurrentItemId(1L);
        st.setNextItemId(2L);
        SetPlaySessionRules.toggleSkip(st, 2L);
        assertNull(st.nextItemId());
    }

    @Test
    void applyExclusiveNextClearsConflictingCurrent() {
        SetPlaySessionState st = state(ids(1, 2, 3));
        st.setCurrentItemId(2L);
        SetPlaySessionRules.applyExclusiveNext(st, 2L);
        assertNull(st.currentItemId());
        assertEquals(2L, st.nextItemId());
    }

    @Test
    void applyExclusiveNextClearsSkipAndPlayed() {
        SetPlaySessionState st = state(ids(1, 2, 3));
        st.playedItemIds().add(2L);
        st.skippedItemIds().add(2L);
        SetPlaySessionRules.applyExclusiveNext(st, 2L);
        assertEquals(2L, st.nextItemId());
        assertFalse(st.playedItemIds().contains(2L));
        assertFalse(st.skippedItemIds().contains(2L));
    }

    @Test
    void applyExclusiveCurrentClearsSkipAndPlayed() {
        SetPlaySessionState st = state(ids(1, 2, 3));
        st.setNextItemId(2L);
        st.playedItemIds().add(2L);
        st.skippedItemIds().add(2L);
        SetPlaySessionRules.applyExclusiveCurrent(st, 2L);
        assertEquals(2L, st.currentItemId());
        assertNull(st.nextItemId());
        assertFalse(st.playedItemIds().contains(2L));
        assertFalse(st.skippedItemIds().contains(2L));
    }

    @Test
    void togglePlayedClearsCurrent() {
        SetPlaySessionState st = state(ids(1, 2, 3));
        st.setCurrentItemId(2L);
        st.setNextItemId(3L);
        SetPlaySessionRules.togglePlayed(st, 2L);
        assertTrue(st.playedItemIds().contains(2L));
        assertNull(st.currentItemId());
        assertEquals(3L, st.nextItemId());
    }

    @Test
    void togglePlayedOnNextRescans() {
        SetPlaySessionState st = state(ids(1, 2, 3));
        st.setCurrentItemId(1L);
        st.setNextItemId(2L);
        SetPlaySessionRules.togglePlayed(st, 2L);
        assertTrue(st.playedItemIds().contains(2L));
        assertEquals(3L, st.nextItemId());
    }

    @Test
    void togglePlayedOffLeavesPointers() {
        SetPlaySessionState st = state(ids(1, 2, 3));
        st.setCurrentItemId(1L);
        st.setNextItemId(2L);
        st.playedItemIds().add(3L);
        SetPlaySessionRules.togglePlayed(st, 3L);
        assertFalse(st.playedItemIds().contains(3L));
        assertEquals(1L, st.currentItemId());
        assertEquals(2L, st.nextItemId());
    }
}

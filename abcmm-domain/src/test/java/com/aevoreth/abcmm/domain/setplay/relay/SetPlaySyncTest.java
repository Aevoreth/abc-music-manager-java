package com.aevoreth.abcmm.domain.setplay.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aevoreth.abcmm.domain.setlist.SetlistInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistItemInfo;
import com.aevoreth.abcmm.domain.setplay.SetPlayLayoutCard;
import com.aevoreth.abcmm.domain.setplay.SetPlaySessionState;

class SetPlaySyncTest {

    @Test
    void snapshotRoundTrip() {
        SetlistInfo setlist = new SetlistInfo(
                42L, "Friday set", 7L, null, 0, false, 30, "notes", "2026-08-12", "20:00", 3600);
        SetlistItemInfo row1 = new SetlistItemInfo(
                101L, 42L, 1L, "Song A", "Composer", 120, 3, "[]", 0, null, null);
        SetlistItemInfo row2 = new SetlistItemInfo(
                102L, 42L, 2L, "Song B", "", 90, 2, "[]", 1, null, null);

        SetPlaySessionState state = new SetPlaySessionState(List.of(101L, 102L));
        state.setCurrentItemId(101L);
        state.setNextItemId(102L);
        state.playedItemIds().add(99L);
        state.skippedItemIds().add(50L);
        state.bumpRevision();
        state.bumpRevision();

        List<SetPlayLayoutCard> cards = List.of(new SetPlayLayoutCard(
                5L, "Alice", 1, 2, 1, 1, "1", "Melody", "Lute",
                false, false, true, "—", "2", true));

        Map<String, Object> payload = SetPlaySync.snapshotFromLeader(
                state, setlist, List.of(row1, row2), 240, cards);

        assertEquals(SetPlaySync.STATE_TYPE, payload.get("type"));
        assertEquals(2, payload.get("revision"));
        assertEquals(42L, ((Number) payload.get("setlist_id")).longValue());

        SetPlaySync.AppliedSnapshot applied = SetPlaySync.applySnapshot(payload);
        assertEquals(2, applied.session().revision());
        assertEquals(101L, applied.session().currentItemId());
        assertEquals(102L, applied.session().nextItemId());
        assertTrue(applied.session().playedItemIds().contains(99L));
        assertTrue(applied.session().skippedItemIds().contains(50L));
        assertEquals(2, applied.rows().size());
        assertEquals("Song A", applied.rows().get(0).get("title"));
        assertEquals("—", applied.rows().get(1).get("artist"));
        assertEquals("Friday set", applied.setMeta().get("name"));
        assertEquals(240, ((Number) applied.setMeta().get("computed_duration_seconds")).intValue());
        assertEquals(1, applied.layoutCards().size());
        assertEquals("Alice", applied.layoutCards().get(0).playerName());
        assertEquals("1", applied.layoutCards().get(0).partNumber());
        assertTrue(applied.layoutCards().get(0).instrumentChangedFromPriorInSet());
    }

    @Test
    void layoutPayloadOmitsSizeUnits() {
        SetPlayLayoutCard card = new SetPlayLayoutCard(
                1L, "Bob", 0, 0, 3, 2, "2", "Harmony", "Harp",
                true, true, false, "1", "3", false);
        Map<String, Object> m = SetPlaySync.layoutCardsToPayload(List.of(card)).get(0);
        assertNull(m.get("widthUnits"));
        assertNull(m.get("heightUnits"));
        assertEquals(true, m.get("instrument_warning"));
        assertEquals(true, m.get("part_duplicate"));

        SetPlayLayoutCard back = SetPlaySync.layoutCardsFromPayload(List.of(m)).get(0);
        assertEquals(1, back.widthUnits());
        assertEquals(1, back.heightUnits());
        assertTrue(back.instrumentWarning());
        assertTrue(back.partDuplicate());
    }
}

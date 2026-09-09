package com.aevoreth.abcmm.domain.setplay.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aevoreth.abcmm.domain.setlist.SetlistInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistItemInfo;
import com.aevoreth.abcmm.domain.setplay.SetPlayLayoutCard;
import com.aevoreth.abcmm.domain.setplay.SetPlayPartsSheet;
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
                5L, "Alice", 1, 2,
                SetPlaySync.DEFAULT_CARD_WIDTH_UNITS, SetPlaySync.DEFAULT_CARD_HEIGHT_UNITS,
                "1", "Melody", "Lute",
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
        assertEquals(SetPlaySync.DEFAULT_CARD_WIDTH_UNITS, applied.layoutCards().get(0).widthUnits());
        assertEquals(SetPlaySync.DEFAULT_CARD_HEIGHT_UNITS, applied.layoutCards().get(0).heightUnits());
        assertTrue(applied.layoutCards().get(0).instrumentChangedFromPriorInSet());
        assertEquals(false, applied.zipAvailable());
        assertEquals("", applied.sessionName());
        assertTrue(applied.partsSheet().columns().isEmpty());
        assertTrue(applied.layoutCardsByItemId().isEmpty());
    }

    @Test
    void partsSheetAndZipRoundTrip() {
        SetlistInfo setlist = new SetlistInfo(
                1L, "Set", null, null, 0, false, 0, null, null, null, null);
        SetlistItemInfo row = new SetlistItemInfo(
                10L, 1L, 2L, "Tune", "", 60, 1, "[]", 0, null, null);
        SetPlaySessionState state = new SetPlaySessionState(List.of(10L));
        SetPlayPartsSheet sheet = SetPlayPartsSheet.of(
                List.of(new SetPlayPartsSheet.Column("p1", "Alice", 5L)),
                List.of(new SetPlayPartsSheet.Row(10L, List.of("1: Lute"))),
                List.of(new SetPlayPartsSheet.InstrumentsNeeded(5L, "Alice", List.of("Lute"))));
        Map<String, Object> payload = SetPlaySync.snapshotFromLeader(
                state, setlist, List.of(row), 60, List.of(), sheet, true, "RAVE");
        assertEquals(SetPlaySync.STATE_TYPE, payload.get("type"));
        assertEquals(true, payload.get("zip_available"));
        assertEquals("RAVE", payload.get("session_name"));
        SetPlaySync.AppliedSnapshot applied = SetPlaySync.applySnapshot(payload);
        assertEquals(true, applied.zipAvailable());
        assertEquals("RAVE", applied.sessionName());
        assertEquals(1, applied.partsSheet().columns().size());
        assertEquals("Alice", applied.partsSheet().columns().get(0).title());
        assertEquals("1: Lute", applied.partsSheet().rows().get(0).cells().get(0));
    }

    @Test
    void layoutPayloadOmitsSizeUnitsDefaultsToStandardCard() {
        SetPlayLayoutCard card = new SetPlayLayoutCard(
                1L, "Bob", 0, 0, 3, 2, "2", "Harmony", "Harp",
                true, true, false, "1", "3", false);
        Map<String, Object> m = SetPlaySync.layoutCardsToPayload(List.of(card)).get(0);
        assertNull(m.get("widthUnits"));
        assertNull(m.get("heightUnits"));
        assertNull(m.get("width_units"));
        assertNull(m.get("height_units"));
        assertEquals(true, m.get("instrument_warning"));
        assertEquals(true, m.get("part_duplicate"));

        SetPlayLayoutCard back = SetPlaySync.layoutCardsFromPayload(List.of(m)).get(0);
        // Wire format matches Python: no size fields; assistants use fixed 9×7 cards.
        assertEquals(SetPlaySync.DEFAULT_CARD_WIDTH_UNITS, back.widthUnits());
        assertEquals(SetPlaySync.DEFAULT_CARD_HEIGHT_UNITS, back.heightUnits());
        assertTrue(back.instrumentWarning());
        assertTrue(back.partDuplicate());
    }

    @Test
    void layoutPayloadAcceptsOptionalSizeUnits() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("player_id", 9L);
        m.put("player_name", "Cara");
        m.put("x", -4);
        m.put("y", -3);
        m.put("width_units", 11);
        m.put("height_units", 8);
        m.put("part_number", "3");
        m.put("part_name", "Bass");
        m.put("instrument_name", "Theorbo");

        SetPlayLayoutCard back = SetPlaySync.layoutCardsFromPayload(List.of(m)).get(0);
        assertEquals(11, back.widthUnits());
        assertEquals(8, back.heightUnits());
        assertEquals("Cara", back.playerName());
    }

    @Test
    void layoutCardsByItemIdRoundTrip() {
        SetlistInfo setlist = new SetlistInfo(
                1L, "Set", 7L, null, 0, false, 0, null, null, null, null);
        SetlistItemInfo row1 = new SetlistItemInfo(
                10L, 1L, 2L, "Tune A", "", 60, 1, "[]", 0, null, null);
        SetlistItemInfo row2 = new SetlistItemInfo(
                11L, 1L, 3L, "Tune B", "", 90, 2, "[]", 1, null, null);
        SetPlaySessionState state = new SetPlaySessionState(List.of(10L, 11L));
        SetPlayLayoutCard cardA = new SetPlayLayoutCard(
                5L, "Alice", 0, 0,
                SetPlaySync.DEFAULT_CARD_WIDTH_UNITS, SetPlaySync.DEFAULT_CARD_HEIGHT_UNITS,
                "1", "Melody", "Lute",
                false, false, true, "", "2", false);
        SetPlayLayoutCard cardB = new SetPlayLayoutCard(
                5L, "Alice", 0, 0,
                SetPlaySync.DEFAULT_CARD_WIDTH_UNITS, SetPlaySync.DEFAULT_CARD_HEIGHT_UNITS,
                "2", "Harmony", "Harp",
                false, false, true, "1", "", false);
        Map<Long, List<SetPlayLayoutCard>> byItem = Map.of(
                10L, List.of(cardA),
                11L, List.of(cardB));
        Map<String, Object> payload = SetPlaySync.snapshotFromLeader(
                state, setlist, List.of(row1, row2), 150, List.of(cardA),
                SetPlayPartsSheet.empty(), false, "RAVE", byItem);
        SetPlaySync.AppliedSnapshot applied = SetPlaySync.applySnapshot(payload);
        assertEquals(2, applied.layoutCardsByItemId().size());
        assertEquals("Melody", applied.layoutCardsByItemId().get(10L).get(0).partName());
        assertEquals("Harmony", applied.layoutCardsByItemId().get(11L).get(0).partName());
    }

    @Test
    void stubRowsAreNotCompleteLocalDataEvenWhenIdsMatch() {
        SetlistItemInfo stub = new SetlistItemInfo(
                10L, 1L, 2L, "Tune", "", 60, 1, null, 0, null, null);
        SetlistItemInfo local = new SetlistItemInfo(
                10L, 1L, 2L, "Tune", "", 60, 1, "[]", 0, null, 99L);
        Map<String, Object> snapRow = new java.util.LinkedHashMap<>();
        snapRow.put("item_id", 10L);
        List<Map<String, Object>> snapshot = List.of(snapRow);

        assertTrue(SetPlaySync.songRowsMatchSnapshot(List.of(stub), snapshot));
        assertFalse(SetPlaySync.songRowsHavePartData(List.of(stub)));
        assertFalse(SetPlaySync.canHostFromLocal(List.of(stub), snapshot));
        assertTrue(SetPlaySync.canHostFromLocal(List.of(local), snapshot));
    }

    @Test
    void songRowsMatchSnapshotIgnoresOrder() {
        SetlistItemInfo a = new SetlistItemInfo(
                10L, 1L, 2L, "A", "", 60, 1, "[]", 0, null, null);
        SetlistItemInfo b = new SetlistItemInfo(
                11L, 1L, 3L, "B", "", 90, 2, "[]", 1, null, null);
        Map<String, Object> rowA = new java.util.LinkedHashMap<>();
        rowA.put("item_id", 10);
        Map<String, Object> rowB = new java.util.LinkedHashMap<>();
        rowB.put("item_id", 11);
        assertTrue(SetPlaySync.songRowsMatchSnapshot(List.of(b, a), List.of(rowA, rowB)));
    }

    @Test
    void layoutCardsForFocusUsesByItemMapAndPlaceholdersWhenNextUnset() {
        SetPlayLayoutCard songA = card(5L, "Alice", "1", "Melody", "Lute", "", "2", false);
        SetPlayLayoutCard songB = card(5L, "Alice", "2", "Harmony", "Harp", "1", "", true);
        Map<Long, List<SetPlayLayoutCard>> byItem = Map.of(
                10L, List.of(songA),
                11L, List.of(songB));

        List<SetPlayLayoutCard> focused = SetPlaySync.layoutCardsForFocus(11L, byItem, List.of(songA));
        assertEquals("Harmony", focused.get(0).partName());
        assertEquals("Harp", focused.get(0).instrumentName());
        // Baked previous-in-set flags are not the live grid (no CURRENT here).
        assertEquals("", focused.get(0).neighborPrevPartLabel());
        assertFalse(focused.get(0).instrumentChangedFromPriorInSet());

        List<SetPlayLayoutCard> cleared = SetPlaySync.layoutCardsForFocus(null, byItem, List.of(songA));
        assertEquals(1, cleared.size());
        assertEquals("---", cleared.get(0).partNumber());
        assertEquals("(Part Name)", cleared.get(0).partName());
        assertEquals("Alice", cleared.get(0).playerName());
        assertEquals("Melody", songA.partName());
    }

    @Test
    void liveGridCardsHighlightsVsCurrentNotPreviousInOrder() {
        SetPlayLayoutCard songA = card(5L, "Alice", "1", "Melody", "Lute", "", "", false);
        SetPlayLayoutCard songB = card(5L, "Alice", "2", "Harmony", "Harp", "1", "3", false);
        SetPlayLayoutCard songC = card(5L, "Alice", "3", "Bass", "Clarinet", "2", "", false);
        Map<Long, List<SetPlayLayoutCard>> byItem = Map.of(
                10L, List.of(songA),
                11L, List.of(songB),
                12L, List.of(songC));

        List<SetPlayLayoutCard> sequential = SetPlaySync.liveGridCards(
                11L, 10L, 12L, byItem, List.of(songA));
        assertEquals("Harmony", sequential.get(0).partName());
        assertEquals("1", sequential.get(0).neighborPrevPartLabel());
        assertEquals("3", sequential.get(0).neighborNextPartLabel());
        assertTrue(sequential.get(0).instrumentChangedFromPriorInSet());

        List<SetPlayLayoutCard> jumped = SetPlaySync.liveGridCards(
                12L, 10L, null, byItem, List.of(songA));
        assertEquals("Bass", jumped.get(0).partName());
        assertEquals("1", jumped.get(0).neighborPrevPartLabel());
        assertEquals("", jumped.get(0).neighborNextPartLabel());
        assertTrue(jumped.get(0).instrumentChangedFromPriorInSet());
        assertEquals("Clarinet", jumped.get(0).instrumentName());
    }

    @Test
    void liveGridCardsFromSessionUsesSkipAwareAfterNext() {
        SetPlayLayoutCard songA = card(5L, "Alice", "1", "Melody", "Lute", "", "", false);
        SetPlayLayoutCard songB = card(5L, "Alice", "2", "Harmony", "Harp", "", "", false);
        SetPlayLayoutCard songC = card(5L, "Alice", "3", "Bass", "Clarinet", "", "", false);
        Map<Long, List<SetPlayLayoutCard>> byItem = Map.of(
                10L, List.of(songA),
                11L, List.of(songB),
                12L, List.of(songC));

        SetPlaySessionState sequential = new SetPlaySessionState(List.of(10L, 11L, 12L));
        sequential.setCurrentItemId(10L);
        sequential.setNextItemId(11L);
        List<SetPlayLayoutCard> nextB = SetPlaySync.liveGridCards(sequential, byItem, List.of());
        assertEquals("2", nextB.get(0).partNumber());
        assertEquals("1", nextB.get(0).neighborPrevPartLabel());
        assertEquals("3", nextB.get(0).neighborNextPartLabel());
        assertTrue(nextB.get(0).instrumentChangedFromPriorInSet());

        SetPlaySessionState skipped = new SetPlaySessionState(List.of(10L, 11L, 12L));
        skipped.setCurrentItemId(10L);
        skipped.setNextItemId(12L);
        skipped.skippedItemIds().add(11L);
        List<SetPlayLayoutCard> nextC = SetPlaySync.liveGridCards(skipped, byItem, List.of());
        assertEquals("3", nextC.get(0).partNumber());
        assertEquals("1", nextC.get(0).neighborPrevPartLabel());
        assertEquals("", nextC.get(0).neighborNextPartLabel());
        assertTrue(nextC.get(0).instrumentChangedFromPriorInSet());
    }

    @Test
    void liveGridCardsClearsPartsWhenFocusUnset() {
        SetPlayLayoutCard songA = card(5L, "Alice", "1", "Melody", "Lute", "9", "8", true);
        Map<Long, List<SetPlayLayoutCard>> byItem = Map.of(10L, List.of(songA));
        List<SetPlayLayoutCard> cleared = SetPlaySync.liveGridCards(
                null, 10L, 11L, byItem, List.of(songA));
        assertEquals("---", cleared.get(0).partNumber());
        assertEquals("(Part Name)", cleared.get(0).partName());
        assertEquals("(Made for Instrument)", cleared.get(0).instrumentName());
        assertEquals("", cleared.get(0).neighborPrevPartLabel());
        assertFalse(cleared.get(0).instrumentChangedFromPriorInSet());
    }

    @Test
    void liveGridCardsShowsCurrentWhenNextUnsetAtEndOfSet() {
        SetPlayLayoutCard songA = card(5L, "Alice", "1", "Melody", "Lute", "", "", false);
        SetPlayLayoutCard songB = card(5L, "Alice", "2", "Harmony", "Harp", "", "", false);
        Map<Long, List<SetPlayLayoutCard>> byItem = Map.of(
                10L, List.of(songA),
                11L, List.of(songB));
        SetPlaySessionState end = new SetPlaySessionState(List.of(10L, 11L));
        end.setCurrentItemId(11L);
        end.setNextItemId(null);
        List<SetPlayLayoutCard> live = SetPlaySync.liveGridCards(end, byItem, List.of());
        assertEquals("Harmony", live.get(0).partName());
        assertEquals("Harp", live.get(0).instrumentName());
        assertEquals("1", live.get(0).neighborPrevPartLabel());
        assertTrue(live.get(0).instrumentChangedFromPriorInSet());
    }

    @Test
    void liveGridCardsTreatsTravelerSpellingsAsSameInstrument() {
        SetPlayLayoutCard current = card(
                5L, "Alice", "1", "Melody", "Traveler's Trusty Fiddle", "", "", false);
        SetPlayLayoutCard next = card(
                5L, "Alice", "2", "Harmony", "Traveller's Trusty Fiddle", "", "", false);
        Map<Long, List<SetPlayLayoutCard>> byItem = Map.of(
                10L, List.of(current),
                11L, List.of(next));
        List<SetPlayLayoutCard> live = SetPlaySync.liveGridCards(
                11L, 10L, null, byItem, List.of());
        assertFalse(live.get(0).instrumentChangedFromPriorInSet());
        assertEquals("1", live.get(0).neighborPrevPartLabel());
    }

    @Test
    void liveGridCardsSkipsHighlightWhenPartIsDuplicated() {
        SetPlayLayoutCard current = card(5L, "Alice", "1", "Melody", "Lute", "", "", false);
        SetPlayLayoutCard next = new SetPlayLayoutCard(
                5L, "Alice", 0, 0,
                SetPlaySync.DEFAULT_CARD_WIDTH_UNITS, SetPlaySync.DEFAULT_CARD_HEIGHT_UNITS,
                "2", "Harmony", "Harp",
                false, true, true, "", "", false);
        Map<Long, List<SetPlayLayoutCard>> byItem = Map.of(
                10L, List.of(current),
                11L, List.of(next));
        List<SetPlayLayoutCard> live = SetPlaySync.liveGridCards(
                11L, 10L, null, byItem, List.of());
        assertFalse(live.get(0).instrumentChangedFromPriorInSet());
        assertEquals("1", live.get(0).neighborPrevPartLabel());
    }

    @Test
    void liveGridCardsFallsBackToNextLayoutCardsWhenByItemMissingNext() {
        SetPlayLayoutCard liveFromLeader = card(5L, "Alice", "2", "Harmony", "Harp", "1", "3", true);
        List<SetPlayLayoutCard> fallback = SetPlaySync.liveGridCards(
                11L, 10L, 12L, Map.of(), List.of(liveFromLeader));
        assertEquals(1, fallback.size());
        assertEquals("Harmony", fallback.get(0).partName());
        assertTrue(fallback.get(0).instrumentChangedFromPriorInSet());
        assertEquals("1", fallback.get(0).neighborPrevPartLabel());
    }

    private static SetPlayLayoutCard card(
            long playerId,
            String playerName,
            String partNumber,
            String partName,
            String instrumentName,
            String prev,
            String next,
            boolean instChanged) {
        return new SetPlayLayoutCard(
                playerId,
                playerName,
                0,
                0,
                SetPlaySync.DEFAULT_CARD_WIDTH_UNITS,
                SetPlaySync.DEFAULT_CARD_HEIGHT_UNITS,
                partNumber,
                partName,
                instrumentName,
                false,
                false,
                true,
                prev,
                next,
                instChanged);
    }

    @Test
    void protocolTypeConstants() {
        assertEquals("set_play_state_v2", SetPlaySync.STATE_TYPE);
        assertEquals("set_play_hello", SetPlaySync.HELLO_TYPE);
        assertEquals("ping", SetPlaySync.PING_TYPE);
    }
}

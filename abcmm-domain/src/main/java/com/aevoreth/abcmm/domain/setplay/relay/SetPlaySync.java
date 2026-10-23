package com.aevoreth.abcmm.domain.setplay.relay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.aevoreth.abcmm.domain.setlist.SetlistInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistItemInfo;
import com.aevoreth.abcmm.domain.setplay.SetPlayLayoutCard;
import com.aevoreth.abcmm.domain.setplay.SetPlayPartsSheet;
import com.aevoreth.abcmm.domain.setplay.SetPlaySessionRules;
import com.aevoreth.abcmm.domain.setplay.SetPlaySessionState;

/**
 * JSON snapshot for Set Play relay ({@code set_play_state_v2}).
 */
public final class SetPlaySync {

    public static final String STATE_TYPE = "set_play_state_v2";
    public static final String HELLO_TYPE = "set_play_hello";
    public static final String PING_TYPE = "ping";

    /**
     * Wire payload omits card size (Python {@code LayoutCard} is always fixed). Assistants and
     * the browser client paint 9×7 @ 15px/unit — same as the Java Set Play grid.
     */
    public static final int DEFAULT_CARD_WIDTH_UNITS = 9;
    public static final int DEFAULT_CARD_HEIGHT_UNITS = 7;

    private SetPlaySync() {
    }

    public static Map<String, Object> snapshotFromLeader(
            SetPlaySessionState state,
            SetlistInfo setlist,
            List<SetlistItemInfo> songRows,
            Integer computedDurationSeconds,
            List<SetPlayLayoutCard> layoutCards) {
        return snapshotFromLeader(
                state, setlist, songRows, computedDurationSeconds, layoutCards,
                SetPlayPartsSheet.empty(), false, null, Map.of());
    }

    public static Map<String, Object> snapshotFromLeader(
            SetPlaySessionState state,
            SetlistInfo setlist,
            List<SetlistItemInfo> songRows,
            Integer computedDurationSeconds,
            List<SetPlayLayoutCard> layoutCards,
            SetPlayPartsSheet partsSheet,
            boolean zipAvailable,
            String sessionName) {
        return snapshotFromLeader(
                state, setlist, songRows, computedDurationSeconds, layoutCards,
                partsSheet, zipAvailable, sessionName, Map.of());
    }

    public static Map<String, Object> snapshotFromLeader(
            SetPlaySessionState state,
            SetlistInfo setlist,
            List<SetlistItemInfo> songRows,
            Integer computedDurationSeconds,
            List<SetPlayLayoutCard> layoutCards,
            SetPlayPartsSheet partsSheet,
            boolean zipAvailable,
            String sessionName,
            Map<Long, List<SetPlayLayoutCard>> layoutCardsByItemId) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(setlist, "setlist");
        Objects.requireNonNull(songRows, "songRows");

        Map<Long, SetlistItemInfo> byItem = new LinkedHashMap<>();
        for (SetlistItemInfo row : songRows) {
            byItem.put(row.id(), row);
        }

        List<Map<String, Object>> rowPayloads = new ArrayList<>();
        for (Long iid : state.orderItemIds()) {
            SetlistItemInfo r = byItem.get(iid);
            if (r == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("item_id", r.id());
            row.put("song_id", r.songId());
            row.put("position", r.position());
            row.put("title", r.songTitle() == null ? "" : r.songTitle());
            row.put("part_count", r.partCount());
            row.put("duration_seconds", r.songDurationSeconds());
            String artist = r.songComposers();
            row.put("artist", (artist == null || artist.isBlank()) ? "—" : artist);
            rowPayloads.add(row);
        }

        Map<String, Object> setMeta = new LinkedHashMap<>();
        setMeta.put("name", setlist.name());
        setMeta.put("notes", setlist.notes());
        setMeta.put("set_date", setlist.setDate());
        setMeta.put("set_time", setlist.setTime());
        setMeta.put("target_duration_seconds", setlist.targetDurationSeconds());
        setMeta.put("default_change_duration_seconds", setlist.defaultChangeDurationSeconds());
        setMeta.put("computed_duration_seconds", computedDurationSeconds);
        setMeta.put("band_layout_id", setlist.bandLayoutId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", STATE_TYPE);
        payload.put("revision", state.revision());
        payload.put("setlist_id", setlist.id());
        payload.put("set_meta", setMeta);
        payload.put("order_item_ids", new ArrayList<>(state.orderItemIds()));
        payload.put("rows", rowPayloads);
        payload.put("played_item_ids", new ArrayList<>(new TreeSet<>(state.playedItemIds())));
        payload.put("current_item_id", state.currentItemId());
        payload.put("next_item_id", state.nextItemId());
        payload.put("skipped_item_ids", new ArrayList<>(new TreeSet<>(state.skippedItemIds())));
        payload.put("next_layout_cards", layoutCardsToPayload(
                layoutCards == null ? List.of() : layoutCards));
        payload.put("layout_cards_by_item_id", layoutCardsByItemIdToPayload(
                layoutCardsByItemId == null ? Map.of() : layoutCardsByItemId));
        payload.put("parts_sheet", (partsSheet == null ? SetPlayPartsSheet.empty() : partsSheet).toPayload());
        payload.put("zip_available", zipAvailable);
        payload.put("session_name", sessionName == null ? "" : sessionName);
        return payload;
    }

    /**
     * Parse leader JSON into session + set_meta + rows + layout cards for assistant UI.
     */
    public static AppliedSnapshot applySnapshot(Map<String, Object> data) {
        Objects.requireNonNull(data, "data");
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = data.get("set_meta") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();

        List<Long> order = toLongList(data.get("order_item_ids"));
        SetPlaySessionState st = new SetPlaySessionState(order);
        st.playedItemIds().addAll(toLongSet(data.get("played_item_ids")));
        st.skippedItemIds().addAll(toLongSet(data.get("skipped_item_ids")));
        st.setCurrentItemId(toLongOrNull(data.get("current_item_id")));
        st.setNextItemId(toLongOrNull(data.get("next_item_id")));
        st.setRevision(toInt(data.get("revision"), 0));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = data.get("rows") instanceof List<?> list
                ? castMapList(list)
                : List.of();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cards = data.get("next_layout_cards") instanceof List<?> list
                ? castMapList(list)
                : List.of();

        return new AppliedSnapshot(st, meta, rows, layoutCardsFromPayload(cards),
                SetPlayPartsSheet.fromPayload(data.get("parts_sheet")),
                bool(data.get("zip_available"), false),
                str(data.get("session_name"), ""),
                layoutCardsByItemIdFromPayload(data.get("layout_cards_by_item_id")));
    }

    /**
     * True when local rows still have library part data (not snapshot stubs) and the
     * same setlist-item ids as the relay snapshot.
     */
    public static boolean canHostFromLocal(
            List<SetlistItemInfo> localRows, List<Map<String, Object>> snapshotRows) {
        return songRowsHavePartData(localRows) && songRowsMatchSnapshot(localRows, snapshotRows);
    }

    /**
     * Snapshot hydration writes {@code partsJson} and {@code songLayoutId} as null.
     * Real library rows keep at least one of those.
     */
    public static boolean songRowsHavePartData(List<SetlistItemInfo> rows) {
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        for (SetlistItemInfo row : rows) {
            if (row != null && (row.partsJson() != null || row.songLayoutId() != null)) {
                return true;
            }
        }
        return false;
    }

    /** Same item ids as the snapshot, ignoring table vs {@code order_item_ids} order. */
    public static boolean songRowsMatchSnapshot(
            List<SetlistItemInfo> localRows, List<Map<String, Object>> snapshotRows) {
        if (localRows == null || snapshotRows == null || localRows.size() != snapshotRows.size()) {
            return false;
        }
        Set<Long> localIds = new LinkedHashSet<>();
        for (SetlistItemInfo row : localRows) {
            if (row != null) {
                localIds.add(row.id());
            }
        }
        Set<Long> snapIds = new LinkedHashSet<>();
        for (Map<String, Object> row : snapshotRows) {
            if (row == null) {
                continue;
            }
            Long id = toLongOrNull(row.get("item_id"));
            if (id != null) {
                snapIds.add(id);
            }
        }
        return !localIds.isEmpty() && localIds.equals(snapIds);
    }

    /**
     * Band cards for one song, with gutters and instrument-change highlighting
     * applied the same way as the live grid. NEXT compares to CURRENT only.
     */
    public static List<SetPlayLayoutCard> previewGridCards(
            SetPlaySessionState session,
            long itemId,
            Map<Long, List<SetPlayLayoutCard>> byItemId) {
        Objects.requireNonNull(session, "session");
        Long referenceId = SetPlaySessionRules.instrumentReferenceItemId(session, itemId);
        int idx = session.orderItemIds().indexOf(itemId);
        Long afterId = idx >= 0
                ? SetPlaySessionRules.scanNextItemId(
                        session.orderItemIds(), session.skippedItemIds(), idx)
                : null;
        return liveGridCards(itemId, referenceId, afterId, byItemId, List.of());
    }

    /**
     * Band cards for the NEXT-focused live grid. Per-song cards are assignment
     * data only; orange flags and neighbor gutters are derived from CURRENT vs
     * NEXT at display time. When NEXT is unset, strips part labels so Clear
     * session does not keep the previous song's assignments.
     */
    public static List<SetPlayLayoutCard> layoutCardsForFocus(
            Long focusItemId,
            Map<Long, List<SetPlayLayoutCard>> byItemId,
            List<SetPlayLayoutCard> currentCards) {
        return liveGridCards(focusItemId, null, null, byItemId, currentCards);
    }

    public static List<SetPlayLayoutCard> liveGridCards(
            SetPlaySessionState session,
            Map<Long, List<SetPlayLayoutCard>> byItemId,
            List<SetPlayLayoutCard> fallback) {
        Objects.requireNonNull(session, "session");
        Long nextId = session.nextItemId();
        Long currentId = session.currentItemId();
        if (nextId != null) {
            Long afterNextId = null;
            int ni = session.orderItemIds().indexOf(nextId);
            if (ni >= 0) {
                afterNextId = SetPlaySessionRules.scanNextItemId(
                        session.orderItemIds(), session.skippedItemIds(), ni);
            }
            Long referenceId = SetPlaySessionRules.instrumentReferenceItemId(session, nextId);
            return liveGridCards(nextId, referenceId, afterNextId, byItemId, fallback);
        }
        if (currentId != null) {
            Long priorId = SetPlaySessionRules.instrumentReferenceItemId(session, currentId);
            return liveGridCards(currentId, priorId, null, byItemId, fallback);
        }
        return liveGridCards(null, null, null, byItemId, fallback);
    }

    /**
     * Live grid from per-song assignment cards. {@code fallback} is the leader's
     * {@code next_layout_cards} (used when the by-item map has no NEXT entry).
     */
    public static List<SetPlayLayoutCard> liveGridCards(
            Long nextItemId,
            Long currentItemId,
            Long afterNextItemId,
            Map<Long, List<SetPlayLayoutCard>> byItemId,
            List<SetPlayLayoutCard> fallback) {
        List<SetPlayLayoutCard> fallbackCards = fallback == null ? List.of() : fallback;
        List<SetPlayLayoutCard> nextFromMap = cardsForItem(byItemId, nextItemId);
        if (nextItemId == null) {
            List<SetPlayLayoutCard> source = !fallbackCards.isEmpty()
                    ? fallbackCards
                    : firstNonEmpty(byItemId);
            return positionOnlyCards(source);
        }
        if (nextFromMap.isEmpty()) {
            return fallbackCards.isEmpty() ? List.of() : List.copyOf(fallbackCards);
        }
        Map<Long, SetPlayLayoutCard> currentByPlayer = cardsByPlayer(cardsForItem(byItemId, currentItemId));
        Map<Long, SetPlayLayoutCard> afterByPlayer = cardsByPlayer(cardsForItem(byItemId, afterNextItemId));
        List<SetPlayLayoutCard> out = new ArrayList<>(nextFromMap.size());
        for (SetPlayLayoutCard card : nextFromMap) {
            SetPlayLayoutCard current = currentByPlayer.get(card.playerId());
            SetPlayLayoutCard after = afterByPlayer.get(card.playerId());
            boolean instChanged = !card.partDuplicate()
                    && hasRealPart(card)
                    && hasRealInstrument(card)
                    && current != null
                    && hasRealInstrument(current)
                    && !instrumentsEquivalent(card.instrumentName(), current.instrumentName());
            out.add(card.withLiveHints(partGutterLabel(current), partGutterLabel(after), instChanged));
        }
        return List.copyOf(out);
    }

    public static List<SetPlayLayoutCard> positionOnlyCards(List<SetPlayLayoutCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }
        List<SetPlayLayoutCard> out = new ArrayList<>(cards.size());
        for (SetPlayLayoutCard card : cards) {
            out.add(card.asPlaceholder());
        }
        return List.copyOf(out);
    }

    public static Map<String, Object> layoutCardsByItemIdToPayload(
            Map<Long, List<SetPlayLayoutCard>> byItem) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (byItem == null) {
            return out;
        }
        for (Map.Entry<Long, List<SetPlayLayoutCard>> entry : byItem.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            out.put(String.valueOf(entry.getKey()), layoutCardsToPayload(
                    entry.getValue() == null ? List.of() : entry.getValue()));
        }
        return out;
    }

    public static Map<Long, List<SetPlayLayoutCard>> layoutCardsByItemIdFromPayload(Object raw) {
        Map<Long, List<SetPlayLayoutCard>> out = new LinkedHashMap<>();
        if (!(raw instanceof Map<?, ?> map)) {
            return out;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Long itemId = toLongOrNull(entry.getKey());
            if (itemId == null) {
                continue;
            }
            List<Map<String, Object>> cards = entry.getValue() instanceof List<?> list
                    ? castMapList(list)
                    : List.of();
            out.put(itemId, layoutCardsFromPayload(cards));
        }
        return out;
    }

    public static List<Map<String, Object>> layoutCardsToPayload(List<SetPlayLayoutCard> cards) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SetPlayLayoutCard c : cards) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("player_id", c.playerId());
            m.put("player_name", c.playerName());
            m.put("x", c.x());
            m.put("y", c.y());
            m.put("part_number", c.partNumber());
            m.put("part_name", c.partName());
            m.put("instrument_name", c.instrumentName());
            m.put("instrument_warning", c.instrumentWarning());
            m.put("part_duplicate", c.partDuplicate());
            m.put("use_setlist_player_header", c.useSetlistPlayerHeader());
            m.put("neighbor_prev_part_label", c.neighborPrevPartLabel());
            m.put("neighbor_next_part_label", c.neighborNextPartLabel());
            m.put("instrument_changed_from_prior_in_set", c.instrumentChangedFromPriorInSet());
            out.add(m);
        }
        return out;
    }

    public static List<SetPlayLayoutCard> layoutCardsFromPayload(List<Map<String, Object>> data) {
        List<SetPlayLayoutCard> out = new ArrayList<>();
        if (data == null) {
            return out;
        }
        for (Map<String, Object> d : data) {
            if (d == null) {
                continue;
            }
            out.add(new SetPlayLayoutCard(
                    toLong(d.get("player_id"), 0L),
                    str(d.get("player_name"), ""),
                    toInt(d.get("x"), 0),
                    toInt(d.get("y"), 0),
                    Math.max(1, toInt(d.get("width_units"), DEFAULT_CARD_WIDTH_UNITS)),
                    Math.max(1, toInt(d.get("height_units"), DEFAULT_CARD_HEIGHT_UNITS)),
                    str(d.get("part_number"), "---"),
                    str(d.get("part_name"), ""),
                    str(d.get("instrument_name"), ""),
                    bool(d.get("instrument_warning"), false),
                    bool(d.get("part_duplicate"), false),
                    bool(d.get("use_setlist_player_header"), false),
                    str(d.get("neighbor_prev_part_label"), ""),
                    str(d.get("neighbor_next_part_label"), ""),
                    bool(d.get("instrument_changed_from_prior_in_set"), false)));
        }
        return out;
    }

    private static List<SetPlayLayoutCard> cardsForItem(
            Map<Long, List<SetPlayLayoutCard>> byItemId, Long itemId) {
        if (byItemId == null || itemId == null) {
            return List.of();
        }
        List<SetPlayLayoutCard> cards = byItemId.get(itemId);
        return cards == null || cards.isEmpty() ? List.of() : cards;
    }

    private static List<SetPlayLayoutCard> firstNonEmpty(Map<Long, List<SetPlayLayoutCard>> byItemId) {
        if (byItemId == null) {
            return List.of();
        }
        for (List<SetPlayLayoutCard> cards : byItemId.values()) {
            if (cards != null && !cards.isEmpty()) {
                return cards;
            }
        }
        return List.of();
    }

    private static Map<Long, SetPlayLayoutCard> cardsByPlayer(List<SetPlayLayoutCard> cards) {
        Map<Long, SetPlayLayoutCard> out = new HashMap<>();
        for (SetPlayLayoutCard card : cards) {
            out.put(card.playerId(), card);
        }
        return out;
    }

    private static String partGutterLabel(SetPlayLayoutCard card) {
        if (card == null) {
            return "";
        }
        String pn = card.partNumber();
        if (pn == null) {
            return "";
        }
        String trimmed = pn.strip();
        if (trimmed.isEmpty() || "---".equals(trimmed)) {
            return "";
        }
        return trimmed;
    }

    private static boolean hasRealPart(SetPlayLayoutCard card) {
        return !partGutterLabel(card).isEmpty();
    }

    private static boolean hasRealInstrument(SetPlayLayoutCard card) {
        if (card == null) {
            return false;
        }
        String name = card.instrumentName();
        if (name == null) {
            return false;
        }
        String trimmed = name.strip();
        return !trimmed.isEmpty()
                && !"\u2014".equals(trimmed)
                && !"-".equals(trimmed)
                && !"(Made for Instrument)".equals(trimmed);
    }

    private static boolean instrumentsEquivalent(String a, String b) {
        String left = normalizeInstrumentName(a);
        String right = normalizeInstrumentName(b);
        if (left.equals(right)) {
            return true;
        }
        return travelerAlternate(left).equals(right) || travelerAlternate(right).equals(left);
    }

    private static String travelerAlternate(String name) {
        if (name.contains("traveler")) {
            return name.replace("traveler", "traveller");
        }
        if (name.contains("traveller")) {
            return name.replace("traveller", "traveler");
        }
        return name;
    }

    private static String normalizeInstrumentName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    public record AppliedSnapshot(
            SetPlaySessionState session,
            Map<String, Object> setMeta,
            List<Map<String, Object>> rows,
            List<SetPlayLayoutCard> layoutCards,
            SetPlayPartsSheet partsSheet,
            boolean zipAvailable,
            String sessionName,
            Map<Long, List<SetPlayLayoutCard>> layoutCardsByItemId) {
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castMapList(List<?> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    private static List<Long> toLongList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        for (Object o : list) {
            Long n = toLongOrNull(o);
            if (n != null) {
                out.add(n);
            }
        }
        return out;
    }

    private static Set<Long> toLongSet(Object value) {
        return new HashSet<>(toLongList(value));
    }

    private static Long toLongOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static long toLong(Object value, long fallback) {
        Long n = toLongOrNull(value);
        return n == null ? fallback : n;
    }

    private static int toInt(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return fallback;
    }

    private static String str(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }
}

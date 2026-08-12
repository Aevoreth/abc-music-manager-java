package com.aevoreth.abcmm.domain.setplay.relay;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.aevoreth.abcmm.domain.setlist.SetlistInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistItemInfo;
import com.aevoreth.abcmm.domain.setplay.SetPlayLayoutCard;
import com.aevoreth.abcmm.domain.setplay.SetPlaySessionState;

/**
 * JSON snapshot for Set Play relay ({@code set_play_state_v1}). Mirrors Python {@code set_play_sync}.
 */
public final class SetPlaySync {

    public static final String STATE_TYPE = "set_play_state_v1";

    private SetPlaySync() {
    }

    public static Map<String, Object> snapshotFromLeader(
            SetPlaySessionState state,
            SetlistInfo setlist,
            List<SetlistItemInfo> songRows,
            Integer computedDurationSeconds,
            List<SetPlayLayoutCard> layoutCards) {
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

        return new AppliedSnapshot(st, meta, rows, layoutCardsFromPayload(cards));
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
                    1,
                    1,
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

    public record AppliedSnapshot(
            SetPlaySessionState session,
            Map<String, Object> setMeta,
            List<Map<String, Object>> rows,
            List<SetPlayLayoutCard> layoutCards) {
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

package com.aevoreth.abcmm.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.aevoreth.abcmm.domain.prefs.DefaultFilters;
import com.aevoreth.abcmm.domain.prefs.Preferences;
import com.aevoreth.abcmm.domain.prefs.PreferencesException;
import com.aevoreth.abcmm.domain.prefs.PreferencesStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * JSON preferences store compatible with the Python edition key names.
 */
public final class JsonPreferencesStore implements PreferencesStore {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final Path preferencesPath;

    public JsonPreferencesStore(Path preferencesPath) {
        this.preferencesPath = Objects.requireNonNull(preferencesPath, "preferencesPath").toAbsolutePath();
    }

    public static JsonPreferencesStore atDefaultLocation() {
        return new JsonPreferencesStore(DataPaths.preferencesPath());
    }

    public Path preferencesPath() {
        return preferencesPath;
    }

    @Override
    public Preferences load() {
        if (!Files.isRegularFile(preferencesPath)) {
            return new Preferences();
        }
        try {
            Map<String, Object> raw = MAPPER.readValue(preferencesPath.toFile(), MAP_TYPE);
            if (raw == null) {
                return new Preferences();
            }
            return fromMap(raw);
        } catch (IOException ex) {
            return new Preferences();
        }
    }

    @Override
    public void save(Preferences preferences) throws PreferencesException {
        Objects.requireNonNull(preferences, "preferences");
        try {
            Path parent = preferencesPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            MAPPER.writeValue(preferencesPath.toFile(), toMap(preferences));
        } catch (IOException ex) {
            throw new PreferencesException("Failed to save preferences: " + preferencesPath, ex);
        }
    }

    static Preferences fromMap(Map<String, Object> raw) {
        Preferences prefs = new Preferences();
        Map<String, Object> remaining = new LinkedHashMap<>(raw);

        prefs.setTheme(asString(remaining.remove("theme")));
        prefs.setBaseFontSize(asInteger(remaining.remove("base_font_size")));
        prefs.setDefaultStatusId(asLong(remaining.remove("default_status_id")));
        prefs.setLotroRoot(asString(remaining.remove("lotro_root")));
        prefs.setSetExportDir(asString(remaining.remove("set_export_dir")));
        prefs.setDefaultFilters(parseDefaultFilters(remaining.remove("default_filters")));

        Object geometry = remaining.remove("window_geometry");
        if (geometry instanceof Map<?, ?> map) {
            prefs.setWindowGeometry(castMap(map));
        }

        prefs.setSplitterState(asIntegerList(remaining.remove("splitter_state")));
        prefs.setPlaybackSoundfontPath(asString(remaining.remove("playback_soundfont_path")));
        prefs.setPlaybackVolume(asDouble(remaining.remove("playback_volume")));
        prefs.setPlaybackTempo(asDouble(remaining.remove("playback_tempo")));
        prefs.setPlaybackStereoMode(asString(remaining.remove("playback_stereo_mode")));
        prefs.setPlaybackStereoSlider(asInteger(remaining.remove("playback_stereo_slider")));
        prefs.setSetPlayRelays(asRelayList(remaining.remove("set_play_relays")));
        prefs.setSetPlaySelectedRelayId(asString(remaining.remove("set_play_selected_relay_id")));

        prefs.extras().putAll(remaining);
        return prefs;
    }

    static Map<String, Object> toMap(Preferences preferences) {
        Map<String, Object> map = new LinkedHashMap<>(preferences.extras());
        map.put("theme", preferences.theme());
        map.put("base_font_size", preferences.baseFontSize());
        if (preferences.defaultStatusId() != null) {
            map.put("default_status_id", preferences.defaultStatusId());
        } else {
            map.remove("default_status_id");
        }
        map.put("lotro_root", preferences.lotroRoot());
        map.put("set_export_dir", preferences.setExportDir());
        map.put("default_filters", defaultFiltersToMap(preferences.defaultFilters()));
        if (preferences.windowGeometry() != null) {
            map.put("window_geometry", preferences.windowGeometry());
        } else {
            map.remove("window_geometry");
        }
        if (preferences.splitterState() != null) {
            map.put("splitter_state", preferences.splitterState());
        } else {
            map.remove("splitter_state");
        }
        map.put("playback_soundfont_path", preferences.playbackSoundfontPath());
        map.put("playback_volume", preferences.playbackVolume());
        map.put("playback_tempo", preferences.playbackTempo());
        map.put("playback_stereo_mode", preferences.playbackStereoMode());
        map.put("playback_stereo_slider", preferences.playbackStereoSlider());
        map.put("set_play_relays", preferences.setPlayRelays());
        if (preferences.setPlaySelectedRelayId() != null) {
            map.put("set_play_selected_relay_id", preferences.setPlaySelectedRelayId());
        } else {
            map.remove("set_play_selected_relay_id");
        }
        return map;
    }

    private static DefaultFilters parseDefaultFilters(Object raw) {
        DefaultFilters filters = DefaultFilters.builtins();
        if (!(raw instanceof Map<?, ?> map)) {
            return filters;
        }
        filters.setInSet(asString(map.get("in_set")));
        Integer ratingFrom = asInteger(map.get("rating_from"));
        if (ratingFrom != null) {
            filters.setRatingFrom(ratingFrom);
        }
        Integer ratingTo = asInteger(map.get("rating_to"));
        if (ratingTo != null) {
            filters.setRatingTo(ratingTo);
        }
        Boolean durationMinNone = asBoolean(map.get("duration_min_none"));
        if (durationMinNone != null) {
            filters.setDurationMinNone(durationMinNone);
        }
        Boolean durationMaxNone = asBoolean(map.get("duration_max_none"));
        if (durationMaxNone != null) {
            filters.setDurationMaxNone(durationMaxNone);
        }
        Integer durationMinSec = asInteger(map.get("duration_min_sec"));
        if (durationMinSec != null) {
            filters.setDurationMinSec(durationMinSec);
        }
        Integer durationMaxSec = asInteger(map.get("duration_max_sec"));
        if (durationMaxSec != null) {
            filters.setDurationMaxSec(durationMaxSec);
        }
        filters.setLastPlayedMode(asString(map.get("last_played_mode")));
        filters.setLastPlayedFromSecondsAgo(asInteger(map.get("last_played_from_seconds_ago")));
        filters.setLastPlayedToSecondsAgo(asInteger(map.get("last_played_to_seconds_ago")));
        filters.setLastPlayedFromIso(asString(map.get("last_played_from_iso")));
        filters.setLastPlayedToIso(asString(map.get("last_played_to_iso")));
        Integer partsMin = asInteger(map.get("parts_min"));
        if (partsMin != null) {
            filters.setPartsMin(partsMin);
        }
        Integer partsMax = asInteger(map.get("parts_max"));
        if (partsMax != null) {
            filters.setPartsMax(partsMax);
        }
        filters.setStatusIds(asLongList(map.get("status_ids")));
        return filters;
    }

    private static Map<String, Object> defaultFiltersToMap(DefaultFilters filters) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("in_set", filters.inSet());
        map.put("rating_from", filters.ratingFrom());
        map.put("rating_to", filters.ratingTo());
        map.put("duration_min_none", filters.durationMinNone());
        map.put("duration_max_none", filters.durationMaxNone());
        map.put("duration_min_sec", filters.durationMinSec());
        map.put("duration_max_sec", filters.durationMaxSec());
        map.put("last_played_mode", filters.lastPlayedMode());
        map.put("last_played_from_seconds_ago", filters.lastPlayedFromSecondsAgo());
        map.put("last_played_to_seconds_ago", filters.lastPlayedToSecondsAgo());
        map.put("last_played_from_iso", filters.lastPlayedFromIso());
        map.put("last_played_to_iso", filters.lastPlayedToIso());
        map.put("parts_min", filters.partsMin());
        map.put("parts_max", filters.partsMax());
        map.put("status_ids", filters.statusIds());
        return map;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Double asDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return new LinkedHashMap<>((Map<String, Object>) map);
    }

    private static List<Integer> asIntegerList(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        List<Integer> out = new ArrayList<>();
        for (Object item : list) {
            Integer number = asInteger(item);
            if (number != null) {
                out.add(number);
            }
        }
        return out;
    }

    private static List<Long> asLongList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        for (Object item : list) {
            Long number = asLong(item);
            if (number != null) {
                out.add(number);
            }
        }
        return out;
    }

    private static List<Map<String, Object>> asRelayList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add(castMap(map));
            }
        }
        return out;
    }
}

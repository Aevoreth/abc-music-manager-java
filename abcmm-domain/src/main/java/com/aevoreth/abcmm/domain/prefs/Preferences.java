package com.aevoreth.abcmm.domain.prefs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * User preferences shared with the Python edition ({@code preferences.json}).
 * Only keys used by this milestone are modeled; unknown keys are preserved via {@link #extras()}.
 */
public final class Preferences {

    public static final int DEFAULT_WINDOW_WIDTH = 1100;
    public static final int DEFAULT_WINDOW_HEIGHT = 720;

    private String theme = AppearanceOptions.FLAT_LIGHT_THEME;
    private Integer baseFontSize = AppearanceOptions.DEFAULT_FONT_SIZE;
    private Long defaultStatusId;
    private String lotroRoot = "";
    private String setExportDir = "";
    private DefaultFilters defaultFilters = DefaultFilters.builtins();
    private Map<String, Object> windowGeometry;
    private List<Integer> splitterState;
    private String playbackSoundfontPath = "";
    private Double playbackVolume = 100.0;
    private Double playbackTempo = 1.0;
    private String playbackStereoMode = "maestro";
    private Integer playbackStereoSlider = 0;
    private List<Map<String, Object>> setPlayRelays = List.of();
    private String setPlaySelectedRelayId;
    private final Map<String, Object> extras = new LinkedHashMap<>();

    public Preferences copy() {
        Preferences copy = new Preferences();
        copy.theme = theme;
        copy.baseFontSize = baseFontSize;
        copy.defaultStatusId = defaultStatusId;
        copy.lotroRoot = lotroRoot;
        copy.setExportDir = setExportDir;
        copy.defaultFilters = defaultFilters.copy();
        copy.windowGeometry = windowGeometry == null ? null : new LinkedHashMap<>(windowGeometry);
        copy.splitterState = splitterState == null ? null : List.copyOf(splitterState);
        copy.playbackSoundfontPath = playbackSoundfontPath;
        copy.playbackVolume = playbackVolume;
        copy.playbackTempo = playbackTempo;
        copy.playbackStereoMode = playbackStereoMode;
        copy.playbackStereoSlider = playbackStereoSlider;
        copy.setPlayRelays = copyRelays(setPlayRelays);
        copy.setPlaySelectedRelayId = setPlaySelectedRelayId;
        copy.extras.putAll(extras);
        return copy;
    }

    public String theme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = AppearanceOptions.normalizeTheme(theme);
    }

    public Integer baseFontSize() {
        return baseFontSize;
    }

    public void setBaseFontSize(Integer baseFontSize) {
        this.baseFontSize = AppearanceOptions.normalizeFontSize(baseFontSize);
    }

    public Long defaultStatusId() {
        return defaultStatusId;
    }

    public void setDefaultStatusId(Long defaultStatusId) {
        this.defaultStatusId = defaultStatusId;
    }

    public String lotroRoot() {
        return lotroRoot;
    }

    public void setLotroRoot(String lotroRoot) {
        this.lotroRoot = lotroRoot == null ? "" : lotroRoot.trim();
    }

    public String setExportDir() {
        return setExportDir;
    }

    public void setSetExportDir(String setExportDir) {
        this.setExportDir = setExportDir == null ? "" : setExportDir.trim();
    }

    public DefaultFilters defaultFilters() {
        return defaultFilters;
    }

    public void setDefaultFilters(DefaultFilters defaultFilters) {
        this.defaultFilters = Objects.requireNonNullElseGet(defaultFilters, DefaultFilters::builtins);
    }

    public Map<String, Object> windowGeometry() {
        return windowGeometry;
    }

    public void setWindowGeometry(Map<String, Object> windowGeometry) {
        this.windowGeometry = windowGeometry;
    }

    public void clearWindowGeometry() {
        this.windowGeometry = null;
    }

    public List<Integer> splitterState() {
        return splitterState;
    }

    public void setSplitterState(List<Integer> splitterState) {
        this.splitterState = splitterState == null ? null : List.copyOf(splitterState);
    }

    public String playbackSoundfontPath() {
        return playbackSoundfontPath;
    }

    public void setPlaybackSoundfontPath(String playbackSoundfontPath) {
        this.playbackSoundfontPath = playbackSoundfontPath == null ? "" : playbackSoundfontPath.trim();
    }

    public Double playbackVolume() {
        return playbackVolume;
    }

    public void setPlaybackVolume(Double playbackVolume) {
        if (playbackVolume == null) {
            this.playbackVolume = 100.0;
        } else {
            this.playbackVolume = Math.max(0.0, Math.min(100.0, playbackVolume));
        }
    }

    public Double playbackTempo() {
        return playbackTempo;
    }

    public void setPlaybackTempo(Double playbackTempo) {
        if (playbackTempo == null) {
            this.playbackTempo = 1.0;
        } else {
            this.playbackTempo = Math.max(0.5, Math.min(2.0, playbackTempo));
        }
    }

    public String playbackStereoMode() {
        return playbackStereoMode;
    }

    public void setPlaybackStereoMode(String playbackStereoMode) {
        if ("band_layout".equals(playbackStereoMode)
                || "maestro_user_pan".equals(playbackStereoMode)
                || "maestro".equals(playbackStereoMode)) {
            this.playbackStereoMode = playbackStereoMode;
        } else {
            this.playbackStereoMode = "maestro";
        }
    }

    public Integer playbackStereoSlider() {
        return playbackStereoSlider;
    }

    public void setPlaybackStereoSlider(Integer playbackStereoSlider) {
        if (playbackStereoSlider == null) {
            this.playbackStereoSlider = 0;
        } else {
            this.playbackStereoSlider = Math.max(0, Math.min(100, playbackStereoSlider));
        }
    }

    public List<Map<String, Object>> setPlayRelays() {
        return setPlayRelays;
    }

    public void setSetPlayRelays(List<Map<String, Object>> setPlayRelays) {
        this.setPlayRelays = copyRelays(setPlayRelays);
    }

    public String setPlaySelectedRelayId() {
        return setPlaySelectedRelayId;
    }

    public void setSetPlaySelectedRelayId(String setPlaySelectedRelayId) {
        this.setPlaySelectedRelayId = setPlaySelectedRelayId;
    }

    public Map<String, Object> extras() {
        return extras;
    }

    private static List<Map<String, Object>> copyRelays(List<Map<String, Object>> relays) {
        if (relays == null || relays.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> relay : relays) {
            copy.add(relay == null ? Map.of() : new LinkedHashMap<>(relay));
        }
        return List.copyOf(copy);
    }
}

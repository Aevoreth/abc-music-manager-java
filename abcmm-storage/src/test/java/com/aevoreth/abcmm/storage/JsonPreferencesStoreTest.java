package com.aevoreth.abcmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.domain.prefs.AppearanceOptions;
import com.aevoreth.abcmm.domain.prefs.DefaultFilters;
import com.aevoreth.abcmm.domain.prefs.Preferences;

class JsonPreferencesStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void missingFileReturnsDefaults() {
        JsonPreferencesStore store = new JsonPreferencesStore(tempDir.resolve("preferences.json"));
        Preferences prefs = store.load();
        assertEquals(AppearanceOptions.DEFAULT_FONT_SIZE, prefs.baseFontSize());
        assertEquals(AppearanceOptions.FLAT_LIGHT_THEME, prefs.theme());
        assertEquals(1, prefs.defaultFilters().partsMin());
        assertEquals(24, prefs.defaultFilters().partsMax());
    }

    @Test
    void roundTripsKnownKeysAndPreservesExtras() throws Exception {
        Path path = tempDir.resolve("preferences.json");
        JsonPreferencesStore store = new JsonPreferencesStore(path);

        Preferences prefs = new Preferences();
        prefs.setTheme(AppearanceOptions.FLAT_DARK_THEME);
        prefs.setBaseFontSize(14);
        prefs.setLotroRoot("C:/LOTRO");
        prefs.setSetExportDir("Exports");
        prefs.setDefaultStatusId(2L);
        DefaultFilters filters = DefaultFilters.builtins();
        filters.setInSet("yes");
        filters.setRatingFrom(2);
        filters.setStatusIds(List.of(1L, 2L));
        prefs.setDefaultFilters(filters);
        prefs.setPlaybackVolume(80.0);
        prefs.setPlaybackStereoMode("band_layout");
        prefs.extras().put("library_table_header_state", Map.of("sort_column", 1));

        store.save(prefs);

        assertTrue(Files.isRegularFile(path));
        Preferences loaded = store.load();
        assertEquals(AppearanceOptions.FLAT_DARK_THEME, loaded.theme());
        assertEquals(14, loaded.baseFontSize());
        assertEquals("C:/LOTRO", loaded.lotroRoot());
        assertEquals("Exports", loaded.setExportDir());
        assertEquals(2L, loaded.defaultStatusId());
        assertEquals("yes", loaded.defaultFilters().inSet());
        assertEquals(2, loaded.defaultFilters().ratingFrom());
        assertEquals(List.of(1L, 2L), loaded.defaultFilters().statusIds());
        assertEquals(80.0, loaded.playbackVolume());
        assertEquals("band_layout", loaded.playbackStereoMode());
        assertTrue(loaded.extras().containsKey("library_table_header_state"));
    }

    @Test
    void invalidJsonYieldsDefaults() throws Exception {
        Path path = tempDir.resolve("preferences.json");
        Files.writeString(path, "{not-json");
        JsonPreferencesStore store = new JsonPreferencesStore(path);
        Preferences prefs = store.load();
        assertNull(prefs.defaultStatusId());
        assertEquals("", prefs.lotroRoot());
    }
}

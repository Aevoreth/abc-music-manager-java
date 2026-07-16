package com.aevoreth.abcmm.storage;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Data-folder conventions shared with the Python ABC Music Manager edition.
 *
 * <p>Default directory is {@code ~/.abc_music_manager} (underscores, not hyphens).
 */
public final class DataPaths {

    public static final String DEFAULT_DIR_NAME = ".abc_music_manager";
    public static final String DATABASE_FILE_NAME = "abc_music_manager.sqlite";
    public static final String PREFERENCES_FILE_NAME = "preferences.json";
    public static final String DATA_ENV_VAR = "ABC_MUSIC_MANAGER_DATA";

    private DataPaths() {
    }

    public static Path dataDirectory() {
        String override = System.getenv(DATA_ENV_VAR);
        if (override != null && !override.isBlank()) {
            return Paths.get(override.trim());
        }
        return Paths.get(System.getProperty("user.home"), DEFAULT_DIR_NAME);
    }

    public static Path databasePath() {
        return dataDirectory().resolve(DATABASE_FILE_NAME);
    }

    public static Path preferencesPath() {
        return dataDirectory().resolve(PREFERENCES_FILE_NAME);
    }
}

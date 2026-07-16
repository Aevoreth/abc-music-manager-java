package com.aevoreth.abcmm.domain.prefs;

/**
 * Load/save {@link Preferences} from {@code preferences.json}.
 */
public interface PreferencesStore {

    Preferences load();

    void save(Preferences preferences) throws PreferencesException;
}

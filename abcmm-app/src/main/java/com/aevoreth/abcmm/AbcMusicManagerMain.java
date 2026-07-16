package com.aevoreth.abcmm;

import javax.swing.SwingUtilities;

import com.aevoreth.abcmm.domain.prefs.Preferences;
import com.aevoreth.abcmm.storage.JsonPreferencesStore;
import com.aevoreth.abcmm.ui.AbcmmThemer;

/**
 * Entry point for the standalone Java ABC Music Manager application.
 */
public final class AbcMusicManagerMain {

    private AbcMusicManagerMain() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JsonPreferencesStore store = JsonPreferencesStore.atDefaultLocation();
            Preferences preferences = store.load();
            installLookAndFeel(preferences);
            MainFrame frame = new MainFrame(store);
            frame.setVisible(true);
        });
    }

    static void installLookAndFeel() {
        installLookAndFeel(new Preferences());
    }

    static void installLookAndFeel(Preferences preferences) {
        Preferences prefs = preferences == null ? new Preferences() : preferences;
        AbcmmThemer.setLookAndFeelQuietly(prefs.theme(), prefs.baseFontSize());
    }
}

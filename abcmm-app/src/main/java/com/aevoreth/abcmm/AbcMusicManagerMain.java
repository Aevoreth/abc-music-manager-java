package com.aevoreth.abcmm;

import java.util.Arrays;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import com.aevoreth.abcmm.domain.prefs.Preferences;
import com.aevoreth.abcmm.storage.JsonPreferencesStore;
import com.aevoreth.abcmm.ui.AbcmmThemer;
import com.aevoreth.abcmm.ui.AppIcons;
import com.aevoreth.abcmm.ui.SetPlayPanel;

/**
 * Entry point for the standalone Java ABC Music Manager application.
 */
public final class AbcMusicManagerMain {

    private AbcMusicManagerMain() {
    }

    public static void main(String[] args) {
        boolean assistantOnly = Arrays.asList(args).contains("--assistant");
        SwingUtilities.invokeLater(() -> {
            JsonPreferencesStore store = JsonPreferencesStore.atDefaultLocation();
            Preferences preferences = store.load();
            installLookAndFeel(preferences);
            if (assistantOnly) {
                openAssistantWindow(store, preferences);
            } else {
                MainFrame frame = new MainFrame(store);
                frame.setVisible(true);
            }
        });
    }

    private static void openAssistantWindow(JsonPreferencesStore store, Preferences preferences) {
        JFrame frame = new JFrame(MainFrame.APP_TITLE + " — Band Assistant");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        AppIcons.applyTo(frame);
        frame.setMinimumSize(new java.awt.Dimension(900, 600));
        SetPlayPanel assistant = new SetPlayPanel(true);
        assistant.setPreferences(preferences);
        assistant.setPreferencesSaver(() -> {
            try {
                store.save(preferences);
            } catch (Exception ignored) {
                // best-effort
            }
        });
        frame.setContentPane(assistant);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                assistant.persistUiState(preferences);
                try {
                    store.save(preferences);
                } catch (Exception ignored) {
                    // best-effort
                }
                assistant.shutdown();
            }

            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                System.exit(0);
            }
        });
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        assistant.onShown();
    }

    static void installLookAndFeel() {
        installLookAndFeel(new Preferences());
    }

    static void installLookAndFeel(Preferences preferences) {
        Preferences prefs = preferences == null ? new Preferences() : preferences;
        AbcmmThemer.setLookAndFeelQuietly(prefs.theme(), prefs.baseFontSize());
    }
}

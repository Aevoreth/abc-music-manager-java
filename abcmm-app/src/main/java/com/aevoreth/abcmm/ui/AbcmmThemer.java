package com.aevoreth.abcmm.ui;

import java.awt.Font;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.text.StyleContext;

import com.aevoreth.abcmm.domain.prefs.AppearanceOptions;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;

/**
 * Look-and-feel installer matching Maestro / ABC Player {@code Themer}:
 * Flat Mac Dark / Light themes and discrete font sizes.
 */
public final class AbcmmThemer {

    private static boolean darkMode;

    private AbcmmThemer() {
    }

    public static boolean isDarkMode() {
        return darkMode;
    }

    public static void setLookAndFeel(String theme, int fontSize)
            throws UnsupportedLookAndFeelException {
        String normalizedTheme = AppearanceOptions.normalizeTheme(theme);
        int normalizedFont = AppearanceOptions.normalizeFontSize(fontSize);

        if (AppearanceOptions.isDarkTheme(normalizedTheme)) {
            FlatMacDarkLaf.setup();
            UIManager.setLookAndFeel(new FlatMacDarkLaf());
            darkMode = true;
        } else {
            FlatMacLightLaf.setup();
            UIManager.setLookAndFeel(new FlatMacLightLaf());
            darkMode = false;
        }

        Font base = UIManager.getFont("defaultFont");
        if (base == null) {
            base = UIManager.getFont("Label.font");
        }
        if (base != null) {
            Font sized = StyleContext.getDefaultStyleContext()
                    .getFont(base.getFamily(), base.getStyle(), normalizedFont);
            UIManager.put("defaultFont", sized);
        }
        FlatLaf.updateUI();
    }

    public static void setLookAndFeelQuietly(String theme, int fontSize) {
        try {
            setLookAndFeel(theme, fontSize);
        } catch (Exception ex) {
            System.err.println("Failed to install FlatLaf theme: " + ex.getMessage());
        }
    }
}

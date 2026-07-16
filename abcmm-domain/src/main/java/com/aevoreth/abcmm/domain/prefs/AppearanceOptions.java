package com.aevoreth.abcmm.domain.prefs;

/**
 * Appearance choices aligned with Maestro / ABC Player ({@code Themer}):
 * Flat Dark / Flat Light themes and discrete font sizes.
 */
public final class AppearanceOptions {

    public static final String FLAT_DARK_THEME = "Flat Dark";
    public static final String FLAT_LIGHT_THEME = "Flat Light";

    public static final String[] THEMES = {FLAT_DARK_THEME, FLAT_LIGHT_THEME};

    public static final int DEFAULT_FONT_SIZE = 12;
    public static final int[] FONT_SIZES = {10, 11, 12, 13, 14, 15, 16, 17, 18, 20, 22, 24, 26, 28, 36};

    private AppearanceOptions() {
    }

    public static String normalizeTheme(String theme) {
        if (FLAT_DARK_THEME.equals(theme) || FLAT_LIGHT_THEME.equals(theme)) {
            return theme;
        }
        // Maestro migrated legacy "Default" to Flat Light.
        return FLAT_LIGHT_THEME;
    }

    public static int normalizeFontSize(Integer fontSize) {
        if (fontSize == null || fontSize <= 0) {
            return DEFAULT_FONT_SIZE;
        }
        int nearest = FONT_SIZES[0];
        int bestDistance = Math.abs(fontSize - nearest);
        for (int i = 1; i < FONT_SIZES.length; i++) {
            int candidate = FONT_SIZES[i];
            int distance = Math.abs(fontSize - candidate);
            if (distance < bestDistance) {
                nearest = candidate;
                bestDistance = distance;
            }
        }
        return nearest;
    }

    public static boolean isDarkTheme(String theme) {
        return FLAT_DARK_THEME.equals(normalizeTheme(theme));
    }
}

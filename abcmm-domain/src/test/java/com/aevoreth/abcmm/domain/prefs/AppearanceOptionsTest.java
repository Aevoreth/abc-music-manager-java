package com.aevoreth.abcmm.domain.prefs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppearanceOptionsTest {

    @Test
    void normalizesThemesLikeMaestro() {
        assertEquals(AppearanceOptions.FLAT_DARK_THEME, AppearanceOptions.normalizeTheme("Flat Dark"));
        assertEquals(AppearanceOptions.FLAT_LIGHT_THEME, AppearanceOptions.normalizeTheme("Flat Light"));
        assertEquals(AppearanceOptions.FLAT_LIGHT_THEME, AppearanceOptions.normalizeTheme("Default"));
        assertEquals(AppearanceOptions.FLAT_LIGHT_THEME, AppearanceOptions.normalizeTheme(null));
        assertTrue(AppearanceOptions.isDarkTheme("Flat Dark"));
        assertFalse(AppearanceOptions.isDarkTheme("Flat Light"));
    }

    @Test
    void normalizesFontSizesToMaestroDiscreteSet() {
        assertEquals(12, AppearanceOptions.normalizeFontSize(null));
        assertEquals(12, AppearanceOptions.normalizeFontSize(0));
        assertEquals(10, AppearanceOptions.normalizeFontSize(8));
        assertEquals(12, AppearanceOptions.normalizeFontSize(12));
        assertEquals(36, AppearanceOptions.normalizeFontSize(40));
        assertEquals(18, AppearanceOptions.normalizeFontSize(19));
    }
}

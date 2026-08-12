package com.aevoreth.abcmm.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AppInfoTest {

    @Test
    void displayVersionIsNonBlank() {
        assertFalse(AppInfo.displayVersion().isBlank());
    }

    @Test
    void aboutMessageIncludesVersionLicenseAndCredits() {
        String message = AppInfo.aboutMessage();
        assertTrue(message.contains(AppInfo.APP_NAME));
        assertTrue(message.contains("Version " + AppInfo.displayVersion()));
        assertTrue(message.contains("MIT License"));
        assertTrue(message.contains(AppInfo.COPYRIGHT));
        assertTrue(message.contains("FlatLaf"));
        assertTrue(message.contains("Maestro"));
        assertTrue(message.contains("LotroInstruments.sf2"));
        assertTrue(message.contains("THIRD_PARTY_NOTICES.md"));
    }
}

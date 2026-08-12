package com.aevoreth.abcmm.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class UserGuideDialogTest {

    @Test
    void resolveRelative_fromIndex() {
        assertEquals("library.md", UserGuideDialog.resolveRelative("index.md", "library.md"));
        assertEquals("settings/appearance.md",
                UserGuideDialog.resolveRelative("index.md", "settings/appearance.md"));
    }

    @Test
    void resolveRelative_fromSettingsPage() {
        assertEquals("settings/folder-rules.md",
                UserGuideDialog.resolveRelative("settings/appearance.md", "folder-rules.md"));
        assertEquals("index.md",
                UserGuideDialog.resolveRelative("settings/appearance.md", "../index.md"));
    }

    @Test
    void resolveRelative_rejectsEscape() {
        assertNull(UserGuideDialog.resolveRelative("index.md", "../../secret.md"));
        assertFalse(UserGuideDialog.isAllowed("../secret.md"));
    }

    @Test
    void guideHomeIsReadable() {
        assertTrue(UserGuideDialog.guideAvailable());
        assertTrue(UserGuideDialog.readPage("index.md").contains("User Guide"));
        assertTrue(UserGuideDialog.readPage("settings/appearance.md").contains("Appearance"));
    }
}

package com.aevoreth.abcmm.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class UserGuideDialogTest {

    @Test
    void optimizerHelpBelongsToTheSettingsWindowAndRemainsModelessAndUnblocked() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            var optimizer = new javax.swing.JDialog((java.awt.Frame) null);
            var settings = new javax.swing.JDialog(optimizer, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
            UserGuideDialog guide = null;
            try {
                assertEquals(settings, OptimizerHelpDialog.ownerOf(settings));
                assertEquals(settings, OptimizerHelpDialog.ownerOf(settings.getRootPane()));
                guide = new UserGuideDialog(OptimizerHelpDialog.ownerOf(settings), "optimizer.md");
                assertEquals(settings, guide.getOwner());
                assertEquals(java.awt.Dialog.ModalityType.MODELESS, guide.getModalityType());
                assertEquals(java.awt.Dialog.ModalExclusionType.APPLICATION_EXCLUDE, guide.getModalExclusionType());
            } finally {
                if (guide != null) guide.dispose();
                settings.dispose(); optimizer.dispose();
            }
        });
    }

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

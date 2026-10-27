package com.aevoreth.abcmm.ui;

import java.awt.Component;
import java.awt.Window;

import javax.swing.SwingUtilities;

/** Shared entry point for the bundled optimizer guide. */
final class OptimizerHelpDialog {
    private OptimizerHelpDialog() {}

    static void show(Component parent) {
        UserGuideDialog.open(ownerOf(parent), "optimizer.md");
    }

    static Window ownerOf(Component parent) {
        if (parent == null) return null;
        return parent instanceof Window window ? window : SwingUtilities.getWindowAncestor(parent);
    }
}

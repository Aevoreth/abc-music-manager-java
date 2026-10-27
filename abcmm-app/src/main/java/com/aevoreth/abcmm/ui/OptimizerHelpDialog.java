package com.aevoreth.abcmm.ui;

import java.awt.Component;

import javax.swing.SwingUtilities;

/** Shared entry point for the bundled optimizer guide. */
final class OptimizerHelpDialog {
    private OptimizerHelpDialog() {}

    static void show(Component parent) {
        UserGuideDialog.open(SwingUtilities.getWindowAncestor(parent), "optimizer.md");
    }
}

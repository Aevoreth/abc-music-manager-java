package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Application status bar.
 */
public final class StatusBar extends JPanel {

    private final JLabel messageLabel = new JLabel(" ");

    public StatusBar() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(messageLabel, BorderLayout.CENTER);
    }

    public void setMessage(String message) {
        messageLabel.setText(message == null ? " " : message);
    }
}

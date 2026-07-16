package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

/**
 * Placeholder Playback panel (controls not wired to audio yet).
 */
public final class PlaybackPanel extends JPanel {

    public PlaybackPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Playback"),
                BorderFactory.createEmptyBorder(4, 8, 8, 8)));

        JLabel nowPlaying = new JLabel("No song loaded");
        add(nowPlaying, BorderLayout.NORTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.add(new JButton("Play"));
        controls.add(new JButton("Pause"));
        controls.add(new JButton("Stop"));
        controls.add(new JLabel("Volume"));
        JSlider volume = new JSlider(0, 100, 100);
        volume.setPreferredSize(new java.awt.Dimension(120, volume.getPreferredSize().height));
        controls.add(volume);
        add(controls, BorderLayout.CENTER);

        JPanel parts = new JPanel(new GridLayout(1, 1));
        parts.add(new JLabel("Parts: (Maestro-backed mute/solo will appear here)"));
        add(parts, BorderLayout.SOUTH);
    }
}

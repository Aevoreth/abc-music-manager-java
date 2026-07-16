package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;

import com.aevoreth.abcmm.domain.playback.AbcPlaybackEngine;
import com.aevoreth.abcmm.domain.playback.LoadedSong;
import com.aevoreth.abcmm.domain.playback.PartInfo;
import com.aevoreth.abcmm.domain.playback.PlayQueueItem;
import com.aevoreth.abcmm.domain.playback.PlaybackEventType;
import com.aevoreth.abcmm.domain.playback.PlaybackException;
import com.aevoreth.abcmm.domain.playback.PlaybackPosition;
import com.aevoreth.abcmm.domain.playback.PlaybackSession;
import com.aevoreth.abcmm.domain.playback.PlaybackState;
import com.aevoreth.abcmm.domain.prefs.Preferences;

/**
 * Bottom playback bar mimicking ABC Player: scrubber, tempo, transport, parts/playlist, volume.
 */
public final class PlaybackPanel extends JPanel {

    private static final int ICON_SIZE = 16;
    private static final int SCRUB_MAX = 1000;

    private PlaybackSession session;
    private Preferences preferences;
    private Consumer<String> errorReporter = msg -> {
    };
    private Runnable prefsPersister = () -> {
    };

    private final JLabel nowPlayingLabel = new JLabel("No song loaded");
    private final JSlider scrubber = new JSlider(0, SCRUB_MAX, 0);
    private final JLabel timeLabel = new JLabel("0:00 / 0:00");
    private final JSlider tempoSlider = new JSlider(50, 200, 100);
    private final JLabel tempoLabel = new JLabel("100%");
    private final JButton prevButton = new JButton(PlaybackIcons.previous(ICON_SIZE));
    private final JButton playPauseButton = new JButton(PlaybackIcons.play(ICON_SIZE));
    private final JButton stopButton = new JButton(PlaybackIcons.stop(ICON_SIZE));
    private final JButton nextButton = new JButton(PlaybackIcons.next(ICON_SIZE));
    private final JButton listButton = new JButton(PlaybackIcons.list(ICON_SIZE));
    private final JSlider volumeSlider = new JSlider(0, 100, 100);

    private final JPanel partsPanel = new JPanel();
    private final JList<String> playlistList = new JList<>();
    private final JPopupMenu listPopup = new JPopupMenu();

    private boolean scrubbing;
    private boolean suppressTempo;
    private boolean suppressVolume;
    private final Timer positionTimer;

    public PlaybackPanel() {
        super(new BorderLayout(6, 4));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(120, 120, 120, 80)),
                new EmptyBorder(6, 10, 8, 10)));

        nowPlayingLabel.setFont(nowPlayingLabel.getFont().deriveFont(Font.BOLD));

        scrubber.setPaintTicks(false);
        scrubber.setFocusable(false);
        timeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        timeLabel.setPreferredSize(new Dimension(110, timeLabel.getPreferredSize().height));

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(scrubber, BorderLayout.CENTER);
        top.add(timeLabel, BorderLayout.EAST);

        styleTransportButton(prevButton, "Previous");
        styleTransportButton(playPauseButton, "Play / Pause");
        styleTransportButton(stopButton, "Stop");
        styleTransportButton(nextButton, "Next");
        styleTransportButton(listButton, "Parts / Playlist");

        tempoSlider.setPreferredSize(new Dimension(110, tempoSlider.getPreferredSize().height));
        tempoSlider.setToolTipText("Tempo");
        tempoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel tempoPanel = new JPanel(new BorderLayout());
        tempoPanel.add(tempoLabel, BorderLayout.NORTH);
        tempoPanel.add(tempoSlider, BorderLayout.CENTER);
        tempoPanel.setPreferredSize(new Dimension(120, 42));

        volumeSlider.setPreferredSize(new Dimension(110, volumeSlider.getPreferredSize().height));
        volumeSlider.setToolTipText("Volume");
        JLabel volumeCaption = new JLabel("Volume", SwingConstants.CENTER);
        JPanel volumePanel = new JPanel(new BorderLayout());
        volumePanel.add(volumeCaption, BorderLayout.NORTH);
        volumePanel.add(volumeSlider, BorderLayout.CENTER);
        volumePanel.setPreferredSize(new Dimension(120, 42));

        JPanel transport = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        transport.add(prevButton);
        transport.add(playPauseButton);
        transport.add(stopButton);
        transport.add(nextButton);
        transport.add(Box.createHorizontalStrut(12));
        transport.add(listButton);

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.add(tempoPanel, BorderLayout.WEST);
        bottom.add(transport, BorderLayout.CENTER);
        bottom.add(volumePanel, BorderLayout.EAST);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        nowPlayingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottom.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(nowPlayingLabel);
        center.add(Box.createVerticalStrut(2));
        center.add(top);
        center.add(Box.createVerticalStrut(4));
        center.add(bottom);
        add(center, BorderLayout.CENTER);

        buildListPopup();
        wireControls();

        positionTimer = new Timer(100, e -> refreshPosition());
        positionTimer.setRepeats(true);
        setTransportEnabled(false);
    }

    public void bind(PlaybackSession session, Preferences preferences,
            Consumer<String> errorReporter, Runnable prefsPersister) {
        this.session = Objects.requireNonNull(session, "session");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.errorReporter = errorReporter == null ? msg -> {
        } : errorReporter;
        this.prefsPersister = prefsPersister == null ? () -> {
        } : prefsPersister;

        AbcPlaybackEngine engine = session.engine();
        applyPrefsToControls();
        try {
            engine.setVolume(volumeSlider.getValue() / 100.0);
            engine.setTempoFactor(tempoSlider.getValue() / 100.0f);
        } catch (PlaybackException ignored) {
            // engine may not be ready
        }

        engine.addPlaybackListener(event -> SwingUtilities.invokeLater(() -> {
            if (event.type() == PlaybackEventType.SONG_LOADED
                    || event.type() == PlaybackEventType.STATE_CHANGED) {
                refreshFromEngine();
            } else if (event.type() == PlaybackEventType.POSITION_CHANGED) {
                refreshPosition();
            } else if (event.type() == PlaybackEventType.TEMPO_CHANGED) {
                syncTempoFromEngine();
            }
        }));
        session.addSessionListener(() -> SwingUtilities.invokeLater(this::refreshPlaylistUi));
        refreshFromEngine();
        refreshPlaylistUi();
        positionTimer.start();
    }

    /** Stop UI polling so AWT can shut down after the frame is disposed. */
    public void stopTimers() {
        positionTimer.stop();
    }

    public void updatePreferences(Preferences preferences) {
        this.preferences = preferences;
        applyPrefsToControls();
    }

    private void buildListPopup() {
        partsPanel.setLayout(new BoxLayout(partsPanel, BoxLayout.Y_AXIS));
        partsPanel.setBorder(BorderFactory.createTitledBorder("Parts"));

        playlistList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        playlistList.setVisibleRowCount(8);
        playlistList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && session != null) {
                    int index = playlistList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        runSafe(() -> session.playAt(index));
                        listPopup.setVisible(false);
                    }
                }
            }
        });
        JScrollPane playlistScroll = new JScrollPane(playlistList);
        playlistScroll.setBorder(BorderFactory.createTitledBorder("Playlist"));
        playlistScroll.setPreferredSize(new Dimension(240, 220));

        JScrollPane partsScroll = new JScrollPane(partsPanel);
        partsScroll.setPreferredSize(new Dimension(260, 220));

        JPanel content = new JPanel(new BorderLayout(8, 0));
        content.setBorder(new EmptyBorder(8, 8, 8, 8));
        content.add(partsScroll, BorderLayout.WEST);
        content.add(playlistScroll, BorderLayout.CENTER);

        listPopup.setLayout(new BorderLayout());
        listPopup.add(content, BorderLayout.CENTER);
    }

    private void wireControls() {
        scrubber.addChangeListener(e -> {
            if (!scrubber.getValueIsAdjusting()) {
                if (scrubbing && session != null) {
                    PlaybackPosition pos = session.engine().getPosition();
                    long totalMs = pos.duration().toMillis();
                    if (totalMs > 0) {
                        long ms = scrubber.getValue() * totalMs / SCRUB_MAX;
                        runSafe(() -> session.engine().seek(Duration.ofMillis(ms)));
                    }
                }
                scrubbing = false;
            } else {
                scrubbing = true;
            }
        });

        prevButton.addActionListener(e -> runSafe(() -> session.previous()));
        nextButton.addActionListener(e -> runSafe(() -> session.next()));
        stopButton.addActionListener(e -> runSafe(() -> session.engine().stop()));
        playPauseButton.addActionListener(e -> runSafe(this::togglePlayPause));
        listButton.addActionListener(e -> toggleListPopup());

        tempoSlider.addChangeListener(tempoListener());
        volumeSlider.addChangeListener(volumeListener());
    }

    private ChangeListener tempoListener() {
        return e -> {
            if (suppressTempo || session == null) {
                return;
            }
            int pct = tempoSlider.getValue();
            tempoLabel.setText(pct + "%");
            if (!tempoSlider.getValueIsAdjusting()) {
                runSafe(() -> {
                    session.engine().setTempoFactor(pct / 100.0f);
                    if (preferences != null) {
                        preferences.setPlaybackTempo(pct / 100.0);
                        prefsPersister.run();
                    }
                });
            }
        };
    }

    private ChangeListener volumeListener() {
        return e -> {
            if (suppressVolume || session == null) {
                return;
            }
            int pct = volumeSlider.getValue();
            if (!volumeSlider.getValueIsAdjusting()) {
                runSafe(() -> {
                    session.engine().setVolume(pct / 100.0);
                    if (preferences != null) {
                        preferences.setPlaybackVolume((double) pct);
                        prefsPersister.run();
                    }
                });
            }
        };
    }

    private void togglePlayPause() throws PlaybackException {
        if (session == null) {
            return;
        }
        AbcPlaybackEngine engine = session.engine();
        if (engine.getState() == PlaybackState.PLAYING) {
            engine.pause();
        } else if (engine.getLoadedSong() != null) {
            engine.play();
        }
    }

    private void toggleListPopup() {
        if (listPopup.isVisible()) {
            listPopup.setVisible(false);
            return;
        }
        rebuildPartsPanel();
        refreshPlaylistUi();
        listPopup.pack();
        Point loc = listButton.getLocationOnScreen();
        int x = loc.x + listButton.getWidth() / 2 - listPopup.getPreferredSize().width / 2;
        int y = loc.y - listPopup.getPreferredSize().height - 4;
        listPopup.show(listButton, listButton.getWidth() / 2 - listPopup.getPreferredSize().width / 2,
                -listPopup.getPreferredSize().height - 4);
        // Keep popup on screen vertically when possible
        if (y > 0) {
            listPopup.setLocation(Math.max(0, x), y);
        }
    }

    private void rebuildPartsPanel() {
        partsPanel.removeAll();
        if (session == null) {
            partsPanel.revalidate();
            return;
        }
        LoadedSong song = session.engine().getLoadedSong();
        if (song == null || song.parts().isEmpty()) {
            partsPanel.add(new JLabel("No parts"));
            partsPanel.revalidate();
            return;
        }
        for (PartInfo part : song.parts()) {
            JPanel row = new JPanel(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(1, 2, 1, 2);
            c.gridy = 0;

            JCheckBox mute = new JCheckBox(part.name() + (part.instrument().isBlank()
                    ? ""
                    : " (" + part.instrument() + ")"));
            mute.setSelected(!session.engine().isPartMuted(part.index()));
            mute.setToolTipText("Mute / unmute part");
            mute.addActionListener(e -> runSafe(() ->
                    session.engine().setPartMuted(part.index(), !mute.isSelected())));

            JToggleButton solo = new JToggleButton("S");
            solo.setMargin(new Insets(2, 6, 2, 6));
            solo.setToolTipText("Solo");
            solo.setSelected(session.engine().isPartSolo(part.index()));
            solo.addActionListener(e -> runSafe(() ->
                    session.engine().setPartSolo(part.index(), solo.isSelected())));

            c.gridx = 0;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.WEST;
            row.add(mute, c);
            c.gridx = 1;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            row.add(solo, c);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            partsPanel.add(row);
        }
        partsPanel.revalidate();
        partsPanel.repaint();
    }

    private void refreshFromEngine() {
        if (session == null) {
            return;
        }
        AbcPlaybackEngine engine = session.engine();
        LoadedSong song = engine.getLoadedSong();
        if (song == null) {
            nowPlayingLabel.setText("No song loaded");
            setTransportEnabled(false);
            timeLabel.setText("0:00 / 0:00");
            scrubber.setValue(0);
            return;
        }
        nowPlayingLabel.setText(song.title() + (song.composer().isBlank() ? "" : " — " + song.composer()));
        setTransportEnabled(true);
        boolean playing = engine.getState() == PlaybackState.PLAYING;
        playPauseButton.setIcon(playing ? PlaybackIcons.pause(ICON_SIZE) : PlaybackIcons.play(ICON_SIZE));
        playPauseButton.setToolTipText(playing ? "Pause" : "Play");
        prevButton.setEnabled(session.hasPrevious());
        nextButton.setEnabled(session.hasNext());
        refreshPosition();
        if (listPopup.isVisible()) {
            rebuildPartsPanel();
        }
    }

    private void refreshPosition() {
        if (session == null || scrubbing) {
            return;
        }
        PlaybackPosition pos = session.engine().getPosition();
        long curMs = pos.position().toMillis();
        long totalMs = pos.duration().toMillis();
        timeLabel.setText(formatTime(curMs) + " / " + formatTime(totalMs));
        if (totalMs > 0) {
            int value = (int) Math.min(SCRUB_MAX, curMs * SCRUB_MAX / totalMs);
            if (Math.abs(scrubber.getValue() - value) > 1) {
                scrubber.setValue(value);
            }
        } else {
            scrubber.setValue(0);
        }
    }

    private void refreshPlaylistUi() {
        if (session == null) {
            return;
        }
        List<PlayQueueItem> queue = session.queue();
        String[] labels = new String[queue.size()];
        for (int i = 0; i < queue.size(); i++) {
            String mark = i == session.currentIndex() ? "▶ " : "   ";
            labels[i] = mark + queue.get(i).title();
        }
        playlistList.setListData(labels);
        if (session.currentIndex() >= 0 && session.currentIndex() < labels.length) {
            playlistList.setSelectedIndex(session.currentIndex());
            playlistList.ensureIndexIsVisible(session.currentIndex());
        }
        prevButton.setEnabled(session.hasPrevious());
        nextButton.setEnabled(session.hasNext());
    }

    private void syncTempoFromEngine() {
        if (session == null) {
            return;
        }
        int pct = Math.round(session.engine().getTempoFactor() * 100);
        suppressTempo = true;
        try {
            tempoSlider.setValue(pct);
            tempoLabel.setText(pct + "%");
        } finally {
            suppressTempo = false;
        }
    }

    private void applyPrefsToControls() {
        if (preferences == null) {
            return;
        }
        suppressVolume = true;
        suppressTempo = true;
        try {
            int vol = preferences.playbackVolume() == null
                    ? 100
                    : (int) Math.round(preferences.playbackVolume());
            volumeSlider.setValue(Math.max(0, Math.min(100, vol)));
            int tempo = preferences.playbackTempo() == null
                    ? 100
                    : (int) Math.round(preferences.playbackTempo() * 100);
            tempoSlider.setValue(Math.max(50, Math.min(200, tempo)));
            tempoLabel.setText(tempoSlider.getValue() + "%");
        } finally {
            suppressVolume = false;
            suppressTempo = false;
        }
    }

    private void setTransportEnabled(boolean enabled) {
        scrubber.setEnabled(enabled);
        playPauseButton.setEnabled(enabled);
        stopButton.setEnabled(enabled);
        listButton.setEnabled(true);
        if (!enabled) {
            prevButton.setEnabled(false);
            nextButton.setEnabled(false);
        }
    }

    private void styleTransportButton(JButton button, String tip) {
        button.setToolTipText(tip);
        button.setFocusable(false);
        button.setMargin(new Insets(6, 14, 6, 14));
    }

    private void runSafe(ThrowingAction action) {
        if (session == null) {
            return;
        }
        try {
            action.run();
            refreshFromEngine();
            refreshPlaylistUi();
        } catch (PlaybackException ex) {
            errorReporter.accept(ex.getMessage());
        }
    }

    private static String formatTime(long millis) {
        long totalSec = Math.max(0, millis / 1000);
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return min + ":" + (sec < 10 ? "0" : "") + sec;
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws PlaybackException;
    }
}

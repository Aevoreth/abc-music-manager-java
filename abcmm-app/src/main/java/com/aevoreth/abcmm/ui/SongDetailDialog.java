package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;

import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.library.PlayLogRepository;
import com.aevoreth.abcmm.domain.library.SongAppMetadataUpdate;
import com.aevoreth.abcmm.domain.library.SongDetailInfo;
import com.aevoreth.abcmm.domain.library.SongRepository;
import com.aevoreth.abcmm.domain.library.StatusInfo;
import com.aevoreth.abcmm.domain.scan.AbcFileMetadata;
import com.aevoreth.abcmm.storage.AbcMetadataParser;

/**
 * Song detail: Basic Info, Notes/Lyrics, Raw ABC (no layouts tab).
 */
public final class SongDetailDialog extends JDialog {

    private final SongRepository songRepository;
    private final PlayLogRepository playLogRepository;
    private final long songId;
    private final List<StatusInfo> statuses;

    private final JLabel titleLabel = new JLabel();
    private final JLabel composersLabel = new JLabel();
    private final JLabel transcriberLabel = new JLabel();
    private final JLabel durationLabel = new JLabel();
    private final JLabel exportLabel = new JLabel();
    private final JLabel partsLabel = new JLabel();
    private final JComboBox<String> ratingCombo = new JComboBox<>(new String[] {
            "\u2606\u2606\u2606\u2606\u2606",
            "\u2605\u2606\u2606\u2606\u2606",
            "\u2605\u2605\u2606\u2606\u2606",
            "\u2605\u2605\u2605\u2606\u2606",
            "\u2605\u2605\u2605\u2605\u2606",
            "\u2605\u2605\u2605\u2605\u2605"
    });
    private final JComboBox<StatusInfo> statusCombo = new JComboBox<>();
    private final JLabel playHistoryLabel = new JLabel();
    private final JTextArea notesArea = new JTextArea(8, 24);
    private final JTextArea lyricsArea = new JTextArea(8, 24);
    private final JTextArea abcArea = new JTextArea();
    private final JLabel abcWarning = new JLabel(
            "<html><b>Warning:</b> Editing raw ABC can make a song unplayable. "
                    + "Only edit if you know what you are doing.</html>");

    private Path filePath;
    private String fileMtimeWhenLoaded;
    private boolean saved;

    public SongDetailDialog(
            Window owner,
            SongRepository songRepository,
            PlayLogRepository playLogRepository,
            long songId,
            List<StatusInfo> statuses) {
        super(owner, "Song detail", ModalityType.APPLICATION_MODAL);
        this.songRepository = songRepository;
        this.playLogRepository = playLogRepository;
        this.songId = songId;
        this.statuses = statuses == null ? List.of() : List.copyOf(statuses);

        statusCombo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value == null ? "" : value.name());
            if (isSelected) {
                label.setOpaque(true);
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            }
            return label;
        });
        for (StatusInfo status : this.statuses) {
            statusCombo.addItem(status);
        }

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Basic Info", buildBasicInfoTab());
        tabs.addTab("Notes and Lyrics", buildNotesTab());
        tabs.addTab("Raw ABC", buildAbcTab());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveMetadataToAbc = new JButton("Save Metadata to ABC");
        saveMetadataToAbc.setEnabled(false);
        saveMetadataToAbc.setToolTipText("Not yet implemented");
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JButton save = new JButton("Save");
        save.addActionListener(e -> saveAppMetadata());
        buttons.add(saveMetadataToAbc);
        buttons.add(cancel);
        buttons.add(save);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(tabs, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        setMinimumSize(new java.awt.Dimension(640, 520));
        setSize(700, 560);
        setLocationRelativeTo(owner);
        loadSong();
    }

    public boolean showDialog() {
        setVisible(true);
        return saved;
    }

    private JPanel buildBasicInfoTab() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        int y = 0;
        y = addRow(form, c, y, "Title:", titleLabel);
        y = addRow(form, c, y, "Composer(s):", composersLabel);
        y = addRow(form, c, y, "Transcriber:", transcriberLabel);
        y = addRow(form, c, y, "Duration:", durationLabel);
        y = addRow(form, c, y, "Export timestamp:", exportLabel);
        y = addRow(form, c, y, "Part count:", partsLabel);
        y = addRow(form, c, y, "Rating:", ratingCombo);
        y = addRow(form, c, y, "Status:", statusCombo);
        playHistoryLabel.setFont(playHistoryLabel.getFont().deriveFont(Font.PLAIN));
        y = addRow(form, c, y, "Play history:", playHistoryLabel);

        JPanel historyButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton markPlayed = new JButton("Mark as played now");
        markPlayed.addActionListener(e -> markPlayedNow());
        JButton editHistory = new JButton("Edit play history...");
        editHistory.addActionListener(e -> openPlayHistory());
        historyButtons.add(markPlayed);
        historyButtons.add(editHistory);
        c.gridx = 1;
        c.gridy = y;
        form.add(historyButtons, c);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        wrap.add(form, BorderLayout.NORTH);
        return wrap;
    }

    private JPanel buildNotesTab() {
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        lyricsArea.setLineWrap(true);
        lyricsArea.setWrapStyleWord(true);
        JPanel notesCol = new JPanel(new BorderLayout(0, 4));
        notesCol.add(new JLabel("Notes:"), BorderLayout.NORTH);
        notesCol.add(new JScrollPane(notesArea), BorderLayout.CENTER);
        JPanel lyricsCol = new JPanel(new BorderLayout(0, 4));
        lyricsCol.add(new JLabel("Lyrics:"), BorderLayout.NORTH);
        lyricsCol.add(new JScrollPane(lyricsArea), BorderLayout.CENTER);
        JPanel row = new JPanel(new java.awt.GridLayout(1, 2, 12, 0));
        row.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        row.add(notesCol);
        row.add(lyricsCol);
        return row;
    }

    private JPanel buildAbcTab() {
        abcWarning.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        abcArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton saveAbc = new JButton("Save to file");
        saveAbc.addActionListener(e -> saveAbc());
        JButton reload = new JButton("Reload from file");
        reload.addActionListener(e -> loadAbcContent());
        buttons.add(saveAbc);
        buttons.add(reload);
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        root.add(abcWarning, BorderLayout.NORTH);
        root.add(new JScrollPane(abcArea), BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        return root;
    }

    private static int addRow(JPanel form, GridBagConstraints c, int y, String label, java.awt.Component field) {
        c.gridx = 0;
        c.gridy = y;
        c.weightx = 0;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(field, c);
        return y + 1;
    }

    private void loadSong() {
        try {
            Optional<SongDetailInfo> detail = songRepository.getSongForDetail(songId);
            if (detail.isEmpty()) {
                titleLabel.setText("(not found)");
                setTitle("Song detail — (not found)");
                return;
            }
            SongDetailInfo data = detail.get();
            titleLabel.setText(data.title());
            composersLabel.setText(data.composers());
            transcriberLabel.setText(blankDash(data.transcriber()));
            durationLabel.setText(LibraryDisplayFormats.formatDuration(data.durationSeconds()));
            exportLabel.setText(blankDash(data.exportTimestamp()));
            partsLabel.setText(String.valueOf(data.partCount()));
            int rating = data.rating() == null ? 0 : Math.max(0, Math.min(5, data.rating()));
            ratingCombo.setSelectedIndex(rating);
            selectStatus(data.statusId());
            notesArea.setText(data.notes() == null ? "" : data.notes());
            lyricsArea.setText(data.lyrics() == null ? "" : data.lyrics());
            setTitle("Song detail — " + data.title() + " — "
                    + (data.composers().isBlank() ? "\u2014" : data.composers())
                    + " — " + data.partCount() + " parts");
            refreshPlayHistorySummary();
            Optional<Path> path = songRepository.resolvePrimaryAbcPath(songId);
            filePath = path.orElse(null);
            loadAbcContent();
        } catch (LibraryException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Song detail", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void selectStatus(Long statusId) {
        if (statusId == null || statusCombo.getItemCount() == 0) {
            return;
        }
        for (int i = 0; i < statusCombo.getItemCount(); i++) {
            StatusInfo status = statusCombo.getItemAt(i);
            if (status != null && status.id() == statusId) {
                statusCombo.setSelectedIndex(i);
                return;
            }
        }
    }

    private void refreshPlayHistorySummary() {
        if (playLogRepository == null) {
            playHistoryLabel.setText("\u2014");
            return;
        }
        try {
            var history = playLogRepository.getPlayHistory(songId, 3);
            if (history.isEmpty()) {
                playHistoryLabel.setText("Never played");
                return;
            }
            StringBuilder text = new StringBuilder("<html>");
            for (var entry : history) {
                text.append(LibraryDisplayFormats.formatLastPlayed(entry.playedAt()));
                if (entry.setlistName() != null) {
                    text.append(" (").append(LibraryDisplayFormats.escapeHtml(entry.setlistName())).append(')');
                }
                text.append("<br>");
            }
            text.append("</html>");
            playHistoryLabel.setText(text.toString());
        } catch (LibraryException ex) {
            playHistoryLabel.setText("\u2014");
        }
    }

    private void markPlayedNow() {
        if (playLogRepository == null) {
            return;
        }
        try {
            playLogRepository.logPlay(songId, null, null);
            refreshPlayHistorySummary();
            saved = true;
        } catch (LibraryException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Song detail", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openPlayHistory() {
        if (playLogRepository == null) {
            return;
        }
        PlayHistoryDialog dialog = new PlayHistoryDialog(this, playLogRepository, songId, titleLabel.getText());
        if (dialog.showDialog()) {
            refreshPlayHistorySummary();
            saved = true;
        }
    }

    private void saveAppMetadata() {
        try {
            int rating = ratingCombo.getSelectedIndex();
            Integer ratingValue = rating <= 0 ? 0 : rating;
            StatusInfo status = (StatusInfo) statusCombo.getSelectedItem();
            Long statusId = status == null ? null : status.id();
            songRepository.updateSongAppMetadata(
                    songId,
                    SongAppMetadataUpdate.full(
                            ratingValue,
                            statusId,
                            notesArea.getText(),
                            lyricsArea.getText()));
            saved = true;
            dispose();
        } catch (LibraryException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Song detail", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadAbcContent() {
        if (filePath == null || !Files.isRegularFile(filePath)) {
            abcArea.setText("");
            abcArea.setEnabled(false);
            fileMtimeWhenLoaded = null;
            return;
        }
        abcArea.setEnabled(true);
        try {
            abcArea.setText(Files.readString(filePath, StandardCharsets.UTF_8));
            abcArea.setCaretPosition(0);
            fileMtimeWhenLoaded = fileMtime(filePath);
        } catch (IOException ex) {
            abcArea.setText("# Error reading file: " + ex.getMessage());
            fileMtimeWhenLoaded = null;
        }
    }

    private void saveAbc() {
        if (filePath == null) {
            JOptionPane.showMessageDialog(
                    this, "No primary file path for this song.", "Raw ABC", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String currentMtime = fileMtime(filePath);
        if (fileMtimeWhenLoaded != null && currentMtime != null && !currentMtime.equals(fileMtimeWhenLoaded)) {
            int reply = JOptionPane.showOptionDialog(
                    this,
                    "The file was modified on disk. Overwrite anyway?",
                    "File changed",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    new Object[] {"Yes", "No", "Cancel"},
                    "No");
            if (reply == JOptionPane.CANCEL_OPTION || reply == JOptionPane.CLOSED_OPTION) {
                return;
            }
            if (reply == JOptionPane.NO_OPTION) {
                loadAbcContent();
                return;
            }
        }
        try {
            String content = abcArea.getText();
            Files.writeString(filePath, content, StandardCharsets.UTF_8);
            AbcFileMetadata metadata = new AbcMetadataParser().parse(content, filePath.getFileName().toString());
            String mtime = fileMtime(filePath);
            String hash = fileHash(filePath);
            songRepository.updateSongFromParsedFile(songId, filePath, metadata, mtime, hash);
            fileMtimeWhenLoaded = mtime;
            loadSong();
            saved = true;
            JOptionPane.showMessageDialog(
                    this, "File saved and re-parsed.", "Raw ABC", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException | LibraryException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Raw ABC", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String blankDash(String value) {
        return value == null || value.isBlank() ? "\u2014" : value;
    }

    private static String fileMtime(Path path) {
        try {
            double seconds = Files.getLastModifiedTime(path).toMillis() / 1000.0;
            return Double.toString(seconds);
        } catch (IOException ex) {
            return null;
        }
    }

    private static String fileHash(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(path);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (IOException | NoSuchAlgorithmException ex) {
            return null;
        }
    }
}

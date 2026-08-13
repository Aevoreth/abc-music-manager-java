package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.aevoreth.abcmm.domain.library.SongRepository;
import com.aevoreth.abcmm.domain.prefs.LotroPaths;
import com.aevoreth.abcmm.domain.prefs.Preferences;
import com.aevoreth.abcmm.domain.setplay.SetPlayZipExtract;
import com.aevoreth.abcmm.domain.setplay.SetPlayZipNames;
import com.aevoreth.abcmm.domain.setplay.relay.SetPlayRelayHttp;

/**
 * Save or extract a session zip. PluginData is offered only when a song repository is bound.
 */
public final class SetPlayDownloadDialog extends JDialog {

    private final SetPlayRelayHttp http;
    private final String relayUrl;
    private final String sessionCode;
    private final String passphrase;
    private final String setName;
    private final Preferences preferences;
    private final SongRepository songRepository;
    private final JTextField pathField = new JTextField();
    private final JTextField folderNameField = new JTextField();

    public SetPlayDownloadDialog(
            Window owner,
            SetPlayRelayHttp http,
            String relayUrl,
            String sessionCode,
            String passphrase,
            String setName,
            String sessionName,
            Preferences preferences,
            SongRepository songRepository) {
        super(owner, "Download ZIP", ModalityType.APPLICATION_MODAL);
        this.http = http;
        this.relayUrl = relayUrl;
        this.sessionCode = sessionCode;
        this.passphrase = passphrase;
        this.setName = setName;
        this.preferences = preferences;
        this.songRepository = songRepository;

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JButton saveAs = new JButton("Save ZIP as…");
        saveAs.setAlignmentX(LEFT_ALIGNMENT);
        saveAs.addActionListener(e -> saveZipAs());
        root.add(saveAs);
        root.add(Box.createVerticalStrut(12));

        root.add(label("Extract path"));
        JPanel pathRow = new JPanel(new BorderLayout(6, 0));
        pathRow.setAlignmentX(LEFT_ALIGNMENT);
        pathField.setColumns(36);
        pathField.setText(defaultExtractDir());
        pathRow.add(pathField, BorderLayout.CENTER);
        JButton locate = new JButton("Locate");
        locate.addActionListener(e -> locateDir());
        pathRow.add(locate, BorderLayout.EAST);
        root.add(pathRow);
        root.add(Box.createVerticalStrut(8));

        root.add(label("Extracted folder name"));
        JPanel folderRow = new JPanel(new BorderLayout(6, 0));
        folderRow.setAlignmentX(LEFT_ALIGNMENT);
        folderNameField.setColumns(36);
        folderNameField.setText(SetPlayZipNames.extractFolderName(sessionName, sessionCode));
        folderNameField.setToolTipText("Folder created under the extract path. Defaults to the session name.");
        folderRow.add(folderNameField, BorderLayout.CENTER);
        root.add(folderRow);
        root.add(Box.createVerticalStrut(6));
        JButton extract = new JButton("Download & Extract");
        extract.setAlignmentX(LEFT_ALIGNMENT);
        extract.addActionListener(e -> downloadAndExtract());
        root.add(extract);
        root.add(Box.createVerticalStrut(12));

        if (songRepository != null) {
            JButton plugin = new JButton("Write PluginData…");
            plugin.setAlignmentX(LEFT_ALIGNMENT);
            plugin.setToolTipText("Updates all songs in Songbook for enabled account targets.");
            plugin.addActionListener(e -> writePluginData());
            root.add(plugin);
            JLabel note = new JLabel("<html><i>PluginData rewrites the full Songbook, not only this zip.</i></html>");
            note.setAlignmentX(LEFT_ALIGNMENT);
            root.add(note);
            root.add(Box.createVerticalStrut(12));
        }

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        south.add(close);
        south.setAlignmentX(LEFT_ALIGNMENT);
        root.add(south);

        setContentPane(root);
        pack();
        setMinimumSize(getPreferredSize());
        setLocationRelativeTo(owner);
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private String defaultExtractDir() {
        return LotroPaths.resolveSetExportDirectory(preferences)
                .map(Path::toString)
                .orElseGet(() -> LotroPaths.effectiveLotroRoot(preferences).map(Path::toString).orElse(""));
    }

    private void saveZipAs() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save ZIP as");
        chooser.setFileFilter(new FileNameExtensionFilter("Zip files", "zip"));
        LotroPaths.resolveSetExportDirectory(preferences).ifPresent(dir -> chooser.setCurrentDirectory(dir.toFile()));
        chooser.setSelectedFile(new java.io.File(SetPlayZipNames.downloadFileName(setName, sessionCode)));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path dest = chooser.getSelectedFile().toPath();
        try {
            byte[] bytes = http.downloadZip(relayUrl, sessionCode, passphrase);
            Files.write(dest, bytes);
            JOptionPane.showMessageDialog(this, "Saved " + dest);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Download", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void locateDir() {
        JFileChooser chooser = new JFileChooser(pathField.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            pathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void downloadAndExtract() {
        String base = pathField.getText() == null ? "" : pathField.getText().strip();
        if (base.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Choose an extract path.", "Extract", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String typed = folderNameField.getText() == null ? "" : folderNameField.getText().strip();
        String folder = SetPlayZipNames.sanitizeBaseName(typed);
        if (folder.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this, "Enter an extracted folder name.", "Extract", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            byte[] bytes = http.downloadZip(relayUrl, sessionCode, passphrase);
            SetPlayZipExtract.validate(bytes);
            Path dest = Path.of(base).resolve(folder);
            if (Files.exists(dest)) {
                int ok = JOptionPane.showConfirmDialog(
                        this,
                        "Folder already exists:\n" + dest + "\n\nDelete it and extract?",
                        "Replace folder",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (ok != JOptionPane.YES_OPTION) {
                    return;
                }
                deleteRecursive(dest);
            }
            Files.createDirectories(dest);
            SetPlayZipExtract.extractTo(bytes, dest);
            JOptionPane.showMessageDialog(this, "Extracted to " + dest);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Extract", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void writePluginData() {
        if (songRepository == null || preferences == null) {
            return;
        }
        PluginDataExportDialog dialog = new PluginDataExportDialog(
                (Window) getOwner(), preferences, songRepository);
        dialog.setVisible(true);
    }

    private static void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort
                }
            });
        }
    }
}

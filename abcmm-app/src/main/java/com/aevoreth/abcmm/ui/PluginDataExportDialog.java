package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.Objects;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.aevoreth.abcmm.domain.library.SongRepository;
import com.aevoreth.abcmm.domain.prefs.Preferences;
import com.aevoreth.abcmm.storage.PluginDataExportService;

/**
 * Modal dialog that runs PluginData export and shows verbose output with errors in red
 * (Python {@code PlugindataExportDialog}).
 */
public final class PluginDataExportDialog extends JDialog {

    private static final Color ERROR_COLOR = new Color(0xc0, 0x39, 0x2b);

    private final Preferences preferences;
    private final SongRepository songRepository;
    private final JTextPane logPane = new JTextPane();
    private final JButton closeButton = new JButton("Close");
    private final Style normalStyle;
    private final Style errorStyle;
    private boolean exportStarted;

    public PluginDataExportDialog(Window owner, Preferences preferences, SongRepository songRepository) {
        super(owner, "PluginData Export", ModalityType.APPLICATION_MODAL);
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.songRepository = Objects.requireNonNull(songRepository, "songRepository");

        setMinimumSize(new Dimension(520, 360));
        setSize(600, 420);
        setLocationRelativeTo(owner);

        logPane.setEditable(false);
        logPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        StyledDocument doc = logPane.getStyledDocument();
        normalStyle = doc.addStyle("normal", null);
        errorStyle = doc.addStyle("error", null);
        StyleConstants.setForeground(errorStyle, ERROR_COLOR);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(new JScrollPane(logPane), BorderLayout.CENTER);

        closeButton.setEnabled(false);
        closeButton.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(closeButton);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible && !exportStarted) {
            exportStarted = true;
            SwingUtilities.invokeLater(this::runExport);
        }
        super.setVisible(visible);
    }

    private void runExport() {
        appendLog("PluginData Export", false);
        appendLog("=".repeat(40), false);

        SwingWorker<Void, LogLine> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                PluginDataExportService service = new PluginDataExportService(songRepository);
                try {
                    service.writeAllTargets(preferences, (msg, isError) ->
                            publish(new LogLine(msg, isError)));
                } catch (Exception ex) {
                    publish(new LogLine(ex.getMessage() == null ? ex.toString() : ex.getMessage(), true));
                }
                return null;
            }

            @Override
            protected void process(java.util.List<LogLine> chunks) {
                for (LogLine line : chunks) {
                    appendLog(line.text(), line.error());
                }
            }

            @Override
            protected void done() {
                closeButton.setEnabled(true);
            }
        };
        worker.execute();
    }

    private void appendLog(String text, boolean isError) {
        StyledDocument doc = logPane.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), text + "\n", isError ? errorStyle : normalStyle);
            logPane.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {
            // ignore
        }
    }

    private record LogLine(String text, boolean error) {
    }
}

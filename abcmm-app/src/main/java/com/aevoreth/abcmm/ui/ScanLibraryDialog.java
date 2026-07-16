package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.scan.DuplicateCandidate;
import com.aevoreth.abcmm.domain.scan.DuplicateDecision;
import com.aevoreth.abcmm.domain.scan.DuplicateResolver;
import com.aevoreth.abcmm.domain.scan.LibraryScanService;
import com.aevoreth.abcmm.domain.scan.ScanProgress;
import com.aevoreth.abcmm.domain.scan.ScanRequest;

/**
 * Modal dialog that runs a library scan on a background thread and prompts for
 * duplicate resolution on the EDT.
 */
public final class ScanLibraryDialog extends JDialog {

    private final LibraryScanService scanService;
    private final ScanRequest request;
    private final Runnable onFinished;
    private final JLabel progressLabel = new JLabel("Starting scan…");
    private final JProgressBar progressBar = new JProgressBar();
    private final AtomicBoolean started = new AtomicBoolean(false);

    public ScanLibraryDialog(
            JFrame owner,
            LibraryScanService scanService,
            ScanRequest request,
            Runnable onFinished) {
        super(owner, "Scan library", true);
        this.scanService = Objects.requireNonNull(scanService, "scanService");
        this.request = Objects.requireNonNull(request, "request");
        this.onFinished = Objects.requireNonNullElse(onFinished, () -> {
        });

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(420, 140));
        setPreferredSize(new Dimension(480, 160));

        progressBar.setIndeterminate(true);

        JPanel body = new JPanel(new BorderLayout(8, 8));
        body.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        body.add(progressLabel, BorderLayout.NORTH);
        body.add(progressBar, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        south.add(new JLabel("Duplicate prompts appear when needed."));
        body.add(south, BorderLayout.SOUTH);
        setContentPane(body);
        pack();
        setLocationRelativeTo(owner);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                startScanIfNeeded();
            }
        });
    }

    private void startScanIfNeeded() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        Thread worker = new Thread(this::runScan, "library-scan");
        worker.setDaemon(true);
        worker.start();
    }

    private void runScan() {
        DuplicateResolver resolver = this::resolveDuplicateOnEdt;
        try {
            ScanProgress result = scanService.scan(request, resolver, this::reportProgress);
            SwingUtilities.invokeLater(() -> finishOk(result));
        } catch (LibraryException ex) {
            SwingUtilities.invokeLater(() -> finishError(ex.getMessage()));
        } catch (RuntimeException ex) {
            SwingUtilities.invokeLater(() -> finishError(
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
        }
    }

    private void reportProgress(ScanProgress progress) {
        if (progress == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            String message = progress.message();
            if (message == null || message.isBlank()) {
                message = String.format(
                        "Scanned %d · added %d · updated %d · removed %d",
                        progress.filesScanned(),
                        progress.songsAdded(),
                        progress.songsUpdated(),
                        progress.songsRemoved());
            }
            progressLabel.setText(message);
        });
    }

    private DuplicateDecision resolveDuplicateOnEdt(DuplicateCandidate candidate) {
        DuplicateDecision[] choice = {DuplicateDecision.SEPARATE};
        try {
            SwingUtilities.invokeAndWait(() -> {
                Object[] options = {"Keep existing", "Keep new", "Separate"};
                String message = String.format(
                        "<html>Duplicate song detected.<br><br>"
                                + "<b>Existing:</b> %s (id %d)<br>"
                                + "<b>New title:</b> %s<br>"
                                + "<b>Path:</b> %s<br>"
                                + "<b>Composers:</b> %s<br>"
                                + "<b>Parts:</b> %d</html>",
                        escape(candidate.existingTitle()),
                        candidate.existingSongId(),
                        escape(candidate.newTitle()),
                        escape(candidate.newPath()),
                        escape(candidate.composers()),
                        candidate.partCount());
                int selected = JOptionPane.showOptionDialog(
                        this,
                        message,
                        "Duplicate song",
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        options,
                        options[2]);
                choice[0] = switch (selected) {
                    case 0 -> DuplicateDecision.KEEP_EXISTING;
                    case 1 -> DuplicateDecision.KEEP_NEW;
                    default -> DuplicateDecision.SEPARATE;
                };
            });
        } catch (Exception ex) {
            return DuplicateDecision.SEPARATE;
        }
        return choice[0];
    }

    private void finishOk(ScanProgress result) {
        progressBar.setIndeterminate(false);
        progressBar.setValue(100);
        String summary = result == null
                ? "Scan complete."
                : String.format(
                        "Scan complete.%nFiles scanned: %d%nAdded: %d%nUpdated: %d%nRemoved: %d",
                        result.filesScanned(),
                        result.songsAdded(),
                        result.songsUpdated(),
                        result.songsRemoved());
        JOptionPane.showMessageDialog(this, summary, "Scan library", JOptionPane.INFORMATION_MESSAGE);
        disposeAndNotify();
    }

    private void finishError(String message) {
        progressBar.setIndeterminate(false);
        JOptionPane.showMessageDialog(
                this,
                message == null || message.isBlank() ? "Library scan failed." : message,
                "Scan library",
                JOptionPane.ERROR_MESSAGE);
        disposeAndNotify();
    }

    private void disposeAndNotify() {
        dispose();
        onFinished.run();
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

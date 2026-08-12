package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.scan.CleanupApplyResult;
import com.aevoreth.abcmm.domain.scan.DuplicateAnalysis;
import com.aevoreth.abcmm.domain.scan.DuplicateCleanupPlan;
import com.aevoreth.abcmm.domain.scan.DuplicateReviewResult;
import com.aevoreth.abcmm.domain.scan.LibraryScanService;
import com.aevoreth.abcmm.domain.scan.ScanProgress;
import com.aevoreth.abcmm.domain.scan.ScanRequest;

/**
 * Modal dialog that inventories the library, opens batch duplicate review when needed,
 * applies cleanup plans (including iterative apply-and-rescan), then reconciles.
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
        south.add(new JLabel("Review folders first; Apply rules and rescan narrows leftover files."));
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
        try {
            DuplicateAnalysis analysis = scanService.analyze(request, this::reportProgress);
            boolean cancelled = false;

            while (analysis.hasDuplicates()) {
                AtomicReference<DuplicateReviewResult> holder = new AtomicReference<>();
                DuplicateAnalysis current = analysis;
                SwingUtilities.invokeAndWait(() -> {
                    DuplicateReviewDialog review = new DuplicateReviewDialog(getOwner(), current);
                    holder.set(review.showAndWait());
                });
                DuplicateReviewResult reviewResult = holder.get();
                if (reviewResult == null
                        || reviewResult.action() == DuplicateReviewResult.Action.CANCELLED) {
                    cancelled = true;
                    SwingUtilities.invokeLater(() -> progressLabel.setText("Review cancelled — reconciling…"));
                    break;
                }

                DuplicateCleanupPlan plan = reviewResult.plan();
                if (plan != null && !plan.isEmpty()) {
                    CleanupApplyResult applyResult = scanService.apply(plan, this::reportProgress);
                    if (applyResult.hasErrors()) {
                        String errors = String.join("\n", applyResult.errors());
                        SwingUtilities.invokeAndWait(() -> JOptionPane.showMessageDialog(
                                this,
                                "Some cleanup actions failed:\n" + errors,
                                "Cleanup warnings",
                                JOptionPane.WARNING_MESSAGE));
                    }
                }

                if (reviewResult.action() == DuplicateReviewResult.Action.FINISHED) {
                    break;
                }

                // APPLY_AND_RESCAN — re-inventory and continue reviewing leftovers
                SwingUtilities.invokeLater(() -> progressLabel.setText("Re-scanning after cleanup rules…"));
                analysis = scanService.analyze(request, this::reportProgress);
            }

            if (cancelled) {
                // still reconcile uniques; do not invent preferred duplicates
            }
            ScanProgress result = scanService.reconcile(request, this::reportProgress);
            SwingUtilities.invokeLater(() -> finishOk(result));
        } catch (LibraryException ex) {
            SwingUtilities.invokeLater(() -> finishError(ex.getMessage()));
        } catch (Exception ex) {
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

    private void finishOk(ScanProgress result) {
        progressBar.setIndeterminate(false);
        progressBar.setValue(100);
        String summary = result == null
                ? "Scan complete."
                : String.format(
                        "Scan complete.\n\nFiles: %d\nAdded: %d\nUpdated: %d\nRemoved: %d",
                        result.filesScanned(),
                        result.songsAdded(),
                        result.songsUpdated(),
                        result.songsRemoved());
        JOptionPane.showMessageDialog(this, summary, "Scan library", JOptionPane.INFORMATION_MESSAGE);
        onFinished.run();
        dispose();
    }

    private void finishError(String message) {
        progressBar.setIndeterminate(false);
        JOptionPane.showMessageDialog(
                this,
                message == null || message.isBlank() ? "Library scan failed." : message,
                "Scan library",
                JOptionPane.ERROR_MESSAGE);
        onFinished.run();
        dispose();
    }
}

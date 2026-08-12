package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import com.aevoreth.abcmm.domain.setplay.relay.SetPlayShareUrls;
import com.aevoreth.abcmm.domain.setplay.relay.SetPlayWorkerPaths;

/**
 * Semi-automatic wizard to deploy the Cloudflare Set Play relay worker
 * (Node, npm, wrangler login/deploy). Port of Python {@code set_play_deploy_wizard.py}.
 */
public final class SetPlayRelayDeployWizard extends JDialog {

    private static final Pattern WORKERS_DEV_RE =
            Pattern.compile("https://[a-zA-Z0-9][-a-zA-Z0-9.]*\\.workers\\.dev");

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private final Consumer<String> onDeployUrl;
    private final boolean deleteWorkerFirst;
    private final Path deployDir;
    private final Path bundle;

    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);
    private final List<String> pageKeys = new ArrayList<>();

    private final JButton backBtn = new JButton("Back");
    private final JButton nextBtn = new JButton("Confirm");
    private final JButton cancelBtn = new JButton("Cancel");

    private final JTextArea logNode = createLogArea();
    private final JTextArea logNpm = createLogArea();
    private final JTextArea logLogin = createLogArea();
    private JTextArea logDelete;
    private final JTextArea logDeploy = createLogArea();
    private final JTextArea urlOut = createLogArea();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "set-play-deploy-wizard");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean processRunning = new AtomicBoolean(false);

    private int pageIdx;
    private boolean syncDone;
    private String deployedWssUrl;
    private String resultUrl;
    private int idxDelete = -1;
    private int idxDeploy;

    /**
     * @param owner             parent window (may be null)
     * @param onDeployUrl       optional callback when a wss URL is obtained / OK pressed
     * @param deleteWorkerFirst if true, redeploy mode (extra wrangler delete step)
     */
    public SetPlayRelayDeployWizard(Window owner, Consumer<String> onDeployUrl, boolean deleteWorkerFirst) {
        super(owner, deleteWorkerFirst ? "Redeploy Set Play relay worker" : "Create your own Set Play relay",
                ModalityType.APPLICATION_MODAL);
        this.onDeployUrl = onDeployUrl;
        this.deleteWorkerFirst = deleteWorkerFirst;

        Path resolvedDeploy = null;
        Path resolvedBundle = null;
        String pathError = null;
        try {
            resolvedDeploy = SetPlayWorkerPaths.resolveSetPlayDeployDirectory();
            resolvedBundle = SetPlayWorkerPaths.workerTemplateBundlePath().orElse(null);
        } catch (IOException ex) {
            pathError = ex.getMessage() == null ? ex.toString() : ex.getMessage();
        }
        this.deployDir = resolvedDeploy;
        this.bundle = resolvedBundle;

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setPreferredSize(new Dimension(560, 420));
        setMinimumSize(new Dimension(480, 360));

        buildPages();
        buildChrome();

        if (pathError != null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not resolve the Set Play deploy folder:\n" + pathError,
                    "Deploy paths",
                    JOptionPane.ERROR_MESSAGE);
        }

        pack();
        setLocationRelativeTo(owner);
        pageIdx = 0;
        showPage(0);
        updateNav();
    }

    /** Modal show; returns the deployed wss URL, or {@code null} if cancelled / no URL. */
    public String showAndGetUrl() {
        setVisible(true);
        return resultUrl;
    }

    /** Last wss URL after close (may be null). */
    public String deployedWssUrl() {
        return deployedWssUrl;
    }

    private void buildPages() {
        cardPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));

        // 0 — Overview
        JPanel overview = new JPanel();
        overview.setLayout(new BoxLayout(overview, BoxLayout.Y_AXIS));
        JLabel intro = new JLabel(overviewHtml());
        intro.setAlignmentX(LEFT_ALIGNMENT);
        overview.add(intro);
        overview.add(Box.createVerticalStrut(12));
        JLabel pathLbl = new JLabel(deployPathHtml());
        pathLbl.setAlignmentX(LEFT_ALIGNMENT);
        overview.add(pathLbl);
        overview.add(Box.createVerticalGlue());
        addPage("overview", overview);

        // 1 — Node.js
        JPanel nodePage = stepPage(
                "Step 1 — Node.js",
                "Verify node and npm, or on Windows install Node LTS with winget (may prompt UAC). "
                        + "After installing, restart this app or use a new terminal.",
                logNode,
                true);
        addPage("node", nodePage);

        // 2 — npm install
        addPage("npm", stepPage(
                "Step 2 — Dependencies",
                "Runs npm install in the deploy folder (installs Wrangler for this project).",
                logNpm,
                false));

        // 3 — wrangler login
        addPage("login", stepPage(
                "Step 3 — Cloudflare login",
                "Runs wrangler login. Complete sign-in in the browser; this window will continue when finished.",
                logLogin,
                false));

        // 4 — optional delete
        if (deleteWorkerFirst) {
            logDelete = createLogArea();
            idxDelete = pageKeys.size();
            addPage("delete", stepPage(
                    "Step 4 — Remove previous worker",
                    "Runs wrangler delete --force in the deploy folder. Complete login first, review the log, "
                            + "then go on to Deploy.",
                    logDelete,
                    false));
            idxDeploy = pageKeys.size();
            addPage("deploy", stepPage(
                    "Step 5 — Deploy",
                    "Runs wrangler deploy. When redeploying, run the Remove previous worker step before this one. "
                            + "When deploy succeeds, click Next to finish.",
                    logDeploy,
                    false));
        } else {
            logDelete = null;
            idxDeploy = pageKeys.size();
            addPage("deploy", stepPage(
                    "Step 4 — Deploy",
                    "Runs wrangler deploy. When it succeeds, click Next to finish and copy your URL.",
                    logDeploy,
                    false));
        }

        // Done
        addPage("done", buildDonePage());
    }

    private JPanel buildDonePage() {
        JPanel done = new JPanel(new BorderLayout(8, 8));
        String hint = onDeployUrl != null
                ? "<html>Click <b>OK</b> to return to the relay editor. The new URL is already available "
                + "(use Copy if you need it elsewhere).</html>"
                : "<html>Copy the relay URL (wss) into Set Playback. Click OK to close.</html>";
        done.add(new JLabel(hint), BorderLayout.NORTH);

        urlOut.setRows(3);
        urlOut.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        done.add(new JScrollPane(urlOut), BorderLayout.CENTER);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton copyBtn = new JButton("Copy relay URL");
        copyBtn.addActionListener(e -> copyUrl());
        row.add(copyBtn);
        if (onDeployUrl != null) {
            JButton addBtn = new JButton("Add to Set Playback…");
            addBtn.addActionListener(e -> {
                String u = urlOut.getText().strip();
                if (u.isEmpty()) {
                    JOptionPane.showMessageDialog(
                            this, "Deploy first to get a URL.", "Set Playback", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                onDeployUrl.accept(u);
                finishWithUrl(u);
            });
            row.add(addBtn);
        }
        JButton okDone = new JButton("OK");
        okDone.addActionListener(e -> doneOk());
        row.add(Box.createHorizontalStrut(16));
        row.add(okDone);
        done.add(row, BorderLayout.SOUTH);
        return done;
    }

    private void buildChrome() {
        JPanel root = new JPanel(new BorderLayout());
        root.add(cardPanel, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        backBtn.addActionListener(e -> goBack());
        nextBtn.addActionListener(e -> goForward());
        cancelBtn.addActionListener(e -> {
            if (pageIdx >= pageKeys.size() - 1) {
                // Close on done — keep any deployed URL
                String t = urlOut.getText().strip();
                finishWithUrl(t.isEmpty() ? deployedWssUrl : t);
            } else {
                resultUrl = null;
                dispose();
            }
        });
        south.add(backBtn);
        south.add(nextBtn);
        south.add(cancelBtn);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                executor.shutdownNow();
            }
        });
    }

    private void addPage(String key, JPanel page) {
        pageKeys.add(key);
        cardPanel.add(page, key);
    }

    private JPanel stepPage(String title, String help, JTextArea log, boolean wingetButton) {
        JPanel p = new JPanel(new BorderLayout(6, 6));
        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        JLabel titleLbl = new JLabel("<html><b>" + escape(title) + "</b></html>");
        titleLbl.setAlignmentX(LEFT_ALIGNMENT);
        north.add(titleLbl);
        JLabel helpLbl = new JLabel("<html>" + escape(help) + "</html>");
        helpLbl.setAlignmentX(LEFT_ALIGNMENT);
        north.add(Box.createVerticalStrut(4));
        north.add(helpLbl);
        p.add(north, BorderLayout.NORTH);
        p.add(new JScrollPane(log), BorderLayout.CENTER);

        JPanel runRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton runBtn = new JButton("Run this step");
        runBtn.addActionListener(e -> runCurrentStep(log));
        runRow.add(runBtn);
        if (wingetButton && IS_WINDOWS) {
            JButton winget = new JButton("Install Node LTS (winget)");
            winget.addActionListener(e -> installNodeWinget(log));
            runRow.add(winget);
        }
        p.add(runRow, BorderLayout.SOUTH);
        return p;
    }

    private static JTextArea createLogArea() {
        JTextArea a = new JTextArea();
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        return a;
    }

    private String overviewHtml() {
        if (deleteWorkerFirst) {
            return "<html><b>Redeploy removes the worker from Cloudflare and deploys a fresh copy.</b><br><br>"
                    + "• You must complete <b>wrangler login</b> with <b>the same Cloudflare account</b> "
                    + "that currently hosts this relay. Otherwise delete/deploy will target the wrong account "
                    + "or fail.<br>"
                    + "• <b>This should almost never be needed.</b> Only use it after extensive testing or "
                    + "when you are genuinely hitting Cloudflare limits—not for routine troubleshooting.<br>"
                    + "• If several Set Playback relay entries share the same *.workers.dev host (same Wrangler "
                    + "worker name), deleting the worker affects <b>all</b> of them until each URL is fixed.<br>"
                    + "• After redeploy, the URL string may match the old one if the worker name is unchanged, "
                    + "but cloud-side state is reset—update and save the relay URL here if it changed.<br><br>"
                    + "This assistant will:<br><br>"
                    + "• Copy the relay worker template to a folder on your computer<br>"
                    + "• Check for Node.js (and on Windows you can try installing Node LTS with winget)<br>"
                    + "• Run npm install (includes Wrangler)<br>"
                    + "• Open a browser so you can sign in to Cloudflare (wrangler login)<br>"
                    + "• On a dedicated step, run <code>wrangler delete --force</code> for that worker (after login), "
                    + "then deploy a fresh copy<br>"
                    + "• Deploy the worker to your Cloudflare account (wrangler deploy)<br><br>"
                    + "You will need a Cloudflare account. The browser step cannot be skipped.</html>";
        }
        return "<html>This assistant will:<br><br>"
                + "• Copy the relay worker template to a folder on your computer<br>"
                + "• Check for Node.js (and on Windows you can try installing Node LTS with winget)<br>"
                + "• Run npm install (includes Wrangler)<br>"
                + "• Open a browser so you can sign in to Cloudflare (wrangler login)<br>"
                + "• Deploy the worker to your Cloudflare account (wrangler deploy)<br><br>"
                + "You will need a Cloudflare account. The browser step cannot be skipped.</html>";
    }

    private String deployPathHtml() {
        String path = deployDir == null ? "(unavailable)" : deployDir.toAbsolutePath().toString();
        return "<html><b>Deploy folder</b> (all commands run here):<br><code>"
                + escape(path) + "</code></html>";
    }

    private void showPage(int idx) {
        if (idx < 0 || idx >= pageKeys.size()) {
            return;
        }
        pageIdx = idx;
        cards.show(cardPanel, pageKeys.get(idx));
    }

    private void updateNav() {
        int n = pageKeys.size();
        boolean onDone = pageIdx >= n - 1;
        backBtn.setEnabled(pageIdx > 0 && !onDone);
        nextBtn.setVisible(!onDone);
        if (pageIdx == 0) {
            nextBtn.setText("Confirm");
        } else if (!onDone) {
            nextBtn.setText("Next");
        }
        cancelBtn.setText(onDone ? "Close" : "Cancel");
    }

    private void goBack() {
        if (pageIdx > 0) {
            showPage(pageIdx - 1);
            updateNav();
        }
    }

    private void goForward() {
        int n = pageKeys.size();
        if (pageIdx == 0) {
            if (bundle == null || !Files.isDirectory(bundle)) {
                JOptionPane.showMessageDialog(
                        this,
                        "The relay worker template was not found in the application bundle. "
                                + "Use a full install or run from source.",
                        "Missing worker template",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (deployDir == null) {
                JOptionPane.showMessageDialog(
                        this,
                        "Deploy folder is not available.",
                        "Extract failed",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                SetPlayWorkerPaths.syncTemplateToDeploy(bundle, deployDir, null);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(
                        this,
                        "Could not copy worker files:\n" + ex.getMessage(),
                        "Extract failed",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            syncDone = true;
            showPage(1);
            updateNav();
            return;
        }

        if (pageIdx < n - 1) {
            showPage(pageIdx + 1);
            updateNav();
        }
    }

    private void runCurrentStep(JTextArea log) {
        if (!syncDone) {
            appendLog(log, "Use Confirm on the first screen to extract the worker.");
            return;
        }
        if (deployDir == null) {
            appendLog(log, "Deploy folder is not available.");
            return;
        }
        if (pageIdx == 1) {
            runNodeCheck(log);
            return;
        }
        if (pageIdx == 2) {
            runNpmInstall(log);
            return;
        }
        if (pageIdx == 3) {
            runWranglerLogin(log);
            return;
        }
        if (idxDelete >= 0 && pageIdx == idxDelete) {
            runWranglerDelete(log);
            return;
        }
        if (pageIdx == idxDeploy) {
            runWranglerDeploy(log);
        }
    }

    private void appendLog(JTextArea log, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            log.append(text.replace("\r\n", "\n").replace('\r', '\n'));
            if (!text.endsWith("\n")) {
                log.append("\n");
            }
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    private void runCmd(JTextArea log, List<String> command, Path cwd, boolean parseDeployUrl) {
        if (!processRunning.compareAndSet(false, true)) {
            appendLog(log, "(A command is already running in this dialog.)");
            return;
        }
        appendLog(log, "$ " + String.join(" ", command));
        executor.execute(() -> {
            int code = -1;
            try {
                ProcessBuilder pb = new ProcessBuilder(command);
                if (cwd != null) {
                    pb.directory(cwd.toFile());
                }
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                Charset cs = Charset.defaultCharset();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), cs))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        appendLog(log, line);
                    }
                }
                code = proc.waitFor();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                appendLog(log, "(interrupted)");
            } catch (IOException ioe) {
                appendLog(log, "Error: " + ioe.getMessage());
            } finally {
                appendLog(log, "-- exit code " + code + " --");
                final int exit = code;
                SwingUtilities.invokeLater(() -> {
                    processRunning.set(false);
                    if (parseDeployUrl && exit == 0) {
                        captureDeployUrl(log);
                    }
                });
            }
        });
    }

    private void captureDeployUrl(JTextArea log) {
        String text = log.getText();
        Matcher m = WORKERS_DEV_RE.matcher(text);
        if (m.find()) {
            String https = m.group(0).replaceAll("/+$", "");
            String wss = SetPlayShareUrls.httpsToWssWorkerUrl(https);
            deployedWssUrl = wss;
            urlOut.setText(wss);
            if (onDeployUrl != null) {
                onDeployUrl.accept(wss);
            }
        }
    }

    private void runNodeCheck(JTextArea log) {
        if (IS_WINDOWS) {
            runCmd(log, List.of("cmd.exe", "/c", "where", "node", "&", "node", "-v", "&", "npm", "-v"),
                    null, false);
        } else {
            runCmd(log, List.of("sh", "-lc", "command -v node && node -v && npm -v"), null, false);
        }
    }

    private void installNodeWinget(JTextArea log) {
        if (!IS_WINDOWS) {
            return;
        }
        runCmd(log, List.of(
                "winget.exe",
                "install",
                "OpenJS.NodeJS.LTS",
                "--accept-package-agreements",
                "--accept-source-agreements"), null, false);
    }

    private void runNpmInstall(JTextArea log) {
        if (IS_WINDOWS) {
            runCmd(log, List.of("cmd.exe", "/c", "npm", "install"), deployDir, false);
        } else {
            runCmd(log, List.of("npm", "install"), deployDir, false);
        }
    }

    private void runWranglerLogin(JTextArea log) {
        if (IS_WINDOWS) {
            runCmd(log, List.of("cmd.exe", "/c", "npx", "wrangler", "login"), deployDir, false);
        } else {
            runCmd(log, List.of("npx", "wrangler", "login"), deployDir, false);
        }
    }

    private void runWranglerDelete(JTextArea log) {
        if (IS_WINDOWS) {
            runCmd(log, List.of("cmd.exe", "/c", "npx", "wrangler", "delete", "--force"), deployDir, false);
        } else {
            runCmd(log, List.of("npx", "wrangler", "delete", "--force"), deployDir, false);
        }
    }

    private void runWranglerDeploy(JTextArea log) {
        if (IS_WINDOWS) {
            runCmd(log, List.of("cmd.exe", "/c", "npx", "wrangler", "deploy"), deployDir, true);
        } else {
            runCmd(log, List.of("npx", "wrangler", "deploy"), deployDir, true);
        }
    }

    private void copyUrl() {
        String t = urlOut.getText().strip();
        if (!t.isEmpty()) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(t), null);
        }
    }

    private void doneOk() {
        String t = urlOut.getText().strip();
        if (!t.isEmpty() && onDeployUrl != null) {
            onDeployUrl.accept(t);
        }
        finishWithUrl(t.isEmpty() ? deployedWssUrl : t);
    }

    private void finishWithUrl(String url) {
        resultUrl = (url == null || url.isBlank()) ? null : url.strip();
        if (resultUrl != null) {
            deployedWssUrl = resultUrl;
        }
        dispose();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

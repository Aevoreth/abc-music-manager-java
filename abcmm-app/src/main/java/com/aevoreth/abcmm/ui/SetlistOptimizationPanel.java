package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsConfiguration;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.ParseException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.ListCellRenderer;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;

import com.aevoreth.abcmm.domain.band.PlayerInfo;
import com.aevoreth.abcmm.domain.band.PlayerRepository;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.optimizer.OptimizationApplyResult;
import com.aevoreth.abcmm.domain.optimizer.OptimizationJournal;
import com.aevoreth.abcmm.domain.optimizer.SearchBudgetPreset;
import com.aevoreth.abcmm.domain.setlist.SetlistInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistItemInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistRepository;
import com.aevoreth.abcmm.optimizer.Assignment;
import com.aevoreth.abcmm.optimizer.AssignmentComparison;
import com.aevoreth.abcmm.optimizer.AssignmentScorer;
import com.aevoreth.abcmm.optimizer.Diagnostic;
import com.aevoreth.abcmm.optimizer.DiagnosticSeverity;
import com.aevoreth.abcmm.optimizer.LoadOutcome;
import com.aevoreth.abcmm.optimizer.OptimizationProblem;
import com.aevoreth.abcmm.optimizer.OptimizationRules;
import com.aevoreth.abcmm.optimizer.Performer;
import com.aevoreth.abcmm.optimizer.ScoreBreakdown;
import com.aevoreth.abcmm.optimizer.ScoreOutcome;
import com.aevoreth.abcmm.optimizer.SearchOutcome;

/**
 * Preview of a setlist instrument optimization. Search reads a separate snapshot
 * and does not write. Apply is the confirmation that stores the result.
 */
final class SetlistOptimizationPanel extends JPanel {

    private static final String DASH = "\u2014";
    private static final String NOT_PROVEN = "This assignment is the best one found. It is not proven optimal.";
    private static final String BEST_SO_FAR = "Best so far. Search is still running.";
    private static final int CUSTOM_SECONDS_DEFAULT = 300;
    private static final int CUSTOM_SECONDS_MAX = 86_400;

    private final Path database;
    private final SetlistInfo setlist;
    private final OptimizationJournal journal;
    private final PlayerRepository players;
    private final SetlistRepository setlists;
    private final Runnable onApplied;

    private final JPanel ruleWarning = new JPanel(new BorderLayout(10, 4));
    private final JTextArea ruleWarningText = new JTextArea(2, 40);
    private final JButton fixRulesButton = new JButton("Review and fix…");
    private Long conflictItem;
    private Integer conflictPart;
    private final JLabel rulesLabel = new JLabel(" ");
    private final JLabel lockLabel = new JLabel(
            "This setlist is locked. You can preview it. Apply stays off until it is unlocked.");
    private final JComboBox<RunBudget> budget = new JComboBox<>(RunBudget.values());
    private final JSpinner customSeconds = new JSpinner(
            new SpinnerNumberModel(CUSTOM_SECONDS_DEFAULT, 1, CUSTOM_SECONDS_MAX, 1));
    private final JTextArea statusArea = new JTextArea(4, 40);
    private final JProgressBar progress = new JProgressBar();
    private final JLabel proven = new JLabel(NOT_PROVEN);
    private final DefaultTableModel metrics = readOnlyModel("Metric", "Current", "Proposed");
    private final DefaultTableModel changes = readOnlyModel("Song", "Player", "Current part", "Proposed part");
    private final DefaultTableModel burdens = readOnlyModel("Player", "Current switches", "Proposed switches");
    private final JTextArea diagnostics = new JTextArea(5, 40);
    private boolean durationInitialized;
    private java.util.function.IntSupplier readDuration = () -> 0;
    private java.util.function.IntConsumer writeDuration = seconds -> { };
    private final JButton settingsButton = new JButton("Optimizer settings…");
    private final JButton searchButton = new JButton("Search");
    private final JButton cancelButton = new JButton("Cancel");
    private final JButton applyButton = new JButton("Apply");

    private final AtomicBoolean cancelSearch = new AtomicBoolean();
    private final Timer elapsedTimer;

    private LoadOutcome loaded;
    private ScoreBreakdown baselineScore;
    private int rememberedCustomSeconds = CUSTOM_SECONDS_DEFAULT;
    private Assignment candidate;
    private boolean searching;
    private boolean closed;
    private long searchStarted;
    private String bestLine = "";
    private JDialog dialog;

    SetlistOptimizationPanel(
            Path database,
            SetlistInfo setlist,
            String layoutLabel,
            OptimizationJournal journal,
            PlayerRepository players,
            SetlistRepository setlists,
            Runnable onApplied) {
        super(new BorderLayout(8, 8));
        this.database = database;
        this.setlist = setlist;
        this.journal = journal;
        this.players = players;
        this.setlists = setlists;
        this.onApplied = onApplied == null ? () -> {
        } : onApplied;

        ruleWarning.setVisible(false);
        ruleWarning.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new java.awt.Color(190, 130, 25)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        JLabel warningIcon = new JLabel(javax.swing.UIManager.getIcon("OptionPane.warningIcon"));
        warningIcon.setVerticalAlignment(JLabel.TOP);
        ruleWarning.add(warningIcon, BorderLayout.WEST);
        JPanel warningMessage = new JPanel(new BorderLayout(0, 4));
        JLabel warningTitle = new JLabel("Optimizer rules need attention");
        warningTitle.setFont(warningTitle.getFont().deriveFont(java.awt.Font.BOLD));
        ruleWarningText.setEditable(false); ruleWarningText.setOpaque(false);
        ruleWarningText.setLineWrap(true); ruleWarningText.setWrapStyleWord(true);
        ruleWarningText.getAccessibleContext().setAccessibleName("Optimizer rule warning");
        warningMessage.add(warningTitle, BorderLayout.NORTH);
        warningMessage.add(ruleWarningText, BorderLayout.CENTER);
        ruleWarning.add(warningMessage, BorderLayout.CENTER);
        ruleWarning.add(fixRulesButton, BorderLayout.EAST);
        fixRulesButton.setToolTipText("Open the affected song and part to change or clear rules for characters outside the selected band.");
        fixRulesButton.addActionListener(e -> {
            if (searching || loaded == null || conflictItem == null) return;
            try {
                if (OptimizerSettingsDialog.show(this, database, null, setlist.id(), conflictItem, conflictPart)) {
                    onApplied.run();
                    searchButton.setEnabled(false); applyButton.setEnabled(false); startLoad();
                }
            } catch (LibraryException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Optimizer settings", JOptionPane.WARNING_MESSAGE);
            }
        });
        add(ruleWarning, BorderLayout.NORTH);
        lockLabel.setVisible(setlist.locked());
        budget.setRenderer(budgetRenderer());
        budget.setSelectedItem(RunBudget.NORMAL);
        budget.setToolTipText("Maximum search time. Longer searches may improve the result. The last duration used is remembered for this band layout.");
        JLabel budgetCaption = new JLabel("Search duration");
        budgetCaption.setLabelFor(budget); budgetCaption.setToolTipText(budget.getToolTipText());
        JLabel secondsCaption = new JLabel("Seconds");
        secondsCaption.setLabelFor(customSeconds);
        OptimizerTooltips.describe(customSeconds, "Maximum search time in seconds when Custom is selected. Cancel keeps the best valid assignment found so far.");
        secondsCaption.setToolTipText(customSeconds.getToolTipText());
        customSeconds.getAccessibleContext().setAccessibleName("Custom search seconds");
        SpinnerMouseWheel.install(customSeconds);
        Dimension spinnerSize = customSeconds.getPreferredSize();
        customSeconds.setPreferredSize(new Dimension(88, spinnerSize.height));
        budget.addActionListener(e -> syncCustomBudget(secondsCaption));
        syncCustomBudget(secondsCaption);

        statusArea.setEditable(false);
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        statusArea.setOpaque(false);
        statusArea.setRows(2);
        statusArea.getAccessibleContext().setAccessibleName("Optimization status");
        statusArea.setText("Loading the setlist…");

        progress.setIndeterminate(false);
        progress.getAccessibleContext().setAccessibleName("Search progress");
        proven.setVisible(false);

        diagnostics.setEditable(false);
        diagnostics.setLineWrap(true);
        diagnostics.setWrapStyleWord(true);
        diagnostics.setRows(4);
        diagnostics.setText("No search yet.");
        JScrollPane diagnosticScroll = scrolling(diagnostics, 480, 96);
        diagnosticScroll.setVisible(false);
        JCheckBox showDiagnostics = new JCheckBox("Show diagnostics");
        showDiagnostics.setToolTipText("Show detailed search information and diagnostics.");
        showDiagnostics.addActionListener(e -> {
            diagnosticScroll.setVisible(showDiagnostics.isSelected());
            revalidate();
        });

        JPanel header = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.WEST;
        gc.insets = new Insets(2, 0, 2, 8);
        gc.gridx = 0;
        gc.gridy = 0;
        header.add(new JLabel("Setlist"), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        header.add(new JLabel(display(setlist.name())), gc);
        gc.gridx = 2;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        gc.gridx = 3;
        gc.gridx = 4;
        gc.gridx = 5;
        gc.gridx = 0;
        gc.gridy = 1;
        gc.gridwidth = 1;
        header.add(new JLabel("Band layout"), gc);
        gc.gridx = 1;
        gc.gridwidth = 5;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        header.add(new JLabel(display(layoutLabel)), gc);
        gc.gridx = 0;
        gc.gridy = 2;
        gc.gridwidth = 1;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        header.add(new JLabel("Rules"), gc);
        gc.gridx = 1;
        gc.gridwidth = 5;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        header.add(rulesLabel, gc);

        JPanel statusBlock = new JPanel(new BorderLayout(4, 4));
        statusBlock.add(statusArea, BorderLayout.NORTH);
        statusBlock.add(progress, BorderLayout.CENTER);
        statusBlock.add(proven, BorderLayout.SOUTH);
        JPanel heading = new JPanel(new BorderLayout(4, 4));
        heading.add(header, BorderLayout.NORTH);
        heading.add(lockLabel, BorderLayout.CENTER);
        heading.add(statusBlock, BorderLayout.SOUTH);

        JPanel scoreBlock = new JPanel(new BorderLayout(0, 4));
        scoreBlock.setBorder(BorderFactory.createTitledBorder("Score"));
        JTable scoreTable = new JTable(metrics);
        scoreTable.getAccessibleContext().setAccessibleName("Score comparison");
        scoreBlock.add(scrolling(scoreTable, 300, 180), BorderLayout.CENTER);
        scoreBlock.add(new JLabel(
                "<html>Raw counts on the first rows. Costs include the saved weights.</html>"),
                BorderLayout.SOUTH);

        JPanel changeBlock = new JPanel(new BorderLayout());
        changeBlock.setBorder(BorderFactory.createTitledBorder("Changed assignments"));
        JTable changeTable = new JTable(changes);
        changeTable.getAccessibleContext().setAccessibleName("Changed assignments");
        changeBlock.add(scrolling(changeTable, 360, 180), BorderLayout.CENTER);

        JPanel burdenBlock = new JPanel(new BorderLayout());
        burdenBlock.setBorder(BorderFactory.createTitledBorder("Switches by player"));
        JTable burdenTable = new JTable(burdens);
        burdenTable.getAccessibleContext().setAccessibleName("Switches by player");
        burdenBlock.add(scrolling(burdenTable, 280, 180), BorderLayout.CENTER);

        JPanel columns = new JPanel(new GridLayout(1, 3, 8, 0));
        columns.add(scoreBlock);
        columns.add(changeBlock);
        columns.add(burdenBlock);
        columns.setPreferredSize(new Dimension(980, 240));

        JPanel diagnosticBlock = new JPanel(new BorderLayout());
        diagnosticBlock.add(showDiagnostics, BorderLayout.NORTH);
        diagnosticBlock.add(diagnosticScroll, BorderLayout.CENTER);

        FittingBody body = new FittingBody();
        body.add(heading, BorderLayout.NORTH);
        body.add(columns, BorderLayout.CENTER);
        body.add(diagnosticBlock, BorderLayout.SOUTH);
        JScrollPane bodyScroll = new JScrollPane(body);
        bodyScroll.setBorder(BorderFactory.createEmptyBorder());
        bodyScroll.getVerticalScrollBar().setUnitIncrement(16);
        bodyScroll.getHorizontalScrollBar().setUnitIncrement(16);
        add(bodyScroll, BorderLayout.CENTER);

        searchButton.setToolTipText("Find assignments for every part and improve instrument transitions. Preview only; Apply saves the result.");
        cancelButton.setToolTipText("Stop searching and keep the best valid assignment found so far.");
        settingsButton.setToolTipText("Edit shared band goals, instrument rules, and song-specific exceptions.");
        searchButton.setEnabled(false);
        cancelButton.setEnabled(false);
        applyButton.setEnabled(false);
        searchButton.addActionListener(e -> startSearch());
        cancelButton.addActionListener(e -> cancelSearch.set(true));
        applyButton.addActionListener(e -> applyCandidate());
        elapsedTimer = new Timer(200, e -> {
            if (!searching) {
                return;
            }
            statusArea.setText("Searching for " + formatElapsed(System.nanoTime() - searchStarted) + ". " + bestLine);
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        settingsButton.addActionListener(e -> {
            if (searching) return;
            try {
                if (OptimizerSettingsDialog.show(this, database, null, setlist.id())) {
                    onApplied.run();
                    searchButton.setEnabled(false); applyButton.setEnabled(false); startLoad();
                }
            } catch (LibraryException ex) { JOptionPane.showMessageDialog(this, ex.getMessage(), "Optimizer settings", JOptionPane.WARNING_MESSAGE); }
        });
        buttons.add(settingsButton);
        buttons.add(budgetCaption); buttons.add(budget); buttons.add(secondsCaption); buttons.add(customSeconds);
        buttons.add(searchButton);
        JButton help = new JButton("Optimizer help…");
        help.addActionListener(event -> OptimizerHelpDialog.show(this));
        buttons.add(help);
        buttons.add(cancelButton);
        buttons.add(applyButton);
        add(buttons, BorderLayout.SOUTH);
        fillMetrics(null, null);
    }

    static void show(
            Component parent,
            Path database,
            SetlistInfo setlist,
            String layoutLabel,
            OptimizationJournal journal,
            PlayerRepository players,
            SetlistRepository setlists,
            Runnable onApplied,
            com.aevoreth.abcmm.domain.prefs.Preferences preferences,
            com.aevoreth.abcmm.domain.prefs.PreferencesStore preferencesStore) {
        SetlistOptimizationPanel panel = new SetlistOptimizationPanel(
                database, setlist, layoutLabel, journal, players, setlists, onApplied);
        panel.bindDurationPreferences(preferences, preferencesStore);
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Optimize instruments", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        panel.bindDialog(dialog);
        JButton close = new JButton("Close");
        close.addActionListener(e -> {
            panel.close();
            dialog.dispose();
        });
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(panel, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.add(close, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                panel.close();
            }
        });
        fitOnScreen(dialog, parent);
        panel.startLoad();
        dialog.setVisible(true);
    }

    JButton applyButton() {
        return applyButton;
    }

    void loadOnCallerThread() {
        showLoad(SetlistOptimizationActions.load(database, setlist.id(), null));
    }

    SearchOutcome searchOnCallerThread(Duration budget, BooleanSupplier cancelled) {
        if (!canSearch()) {
            return null;
        }
        long seed = newSeed();
        searching = true;
        long started = System.nanoTime();
        SearchOutcome outcome = solve(budget, seed, cancelled);
        searching = false;
        finishSearch(outcome, seed, System.nanoTime() - started);
        return outcome;
    }

    private void bindDialog(JDialog dialog) {
        this.dialog = dialog;
    }

    private void close() {
        closed = true;
        cancelSearch.set(true);
        elapsedTimer.stop();
    }

    private void startLoad() {
        progress.setIndeterminate(true);
        SwingWorker<LoadOutcome, Void> worker = new SwingWorker<>() {
            @Override
            protected LoadOutcome doInBackground() {
                return SetlistOptimizationActions.load(database, setlist.id(), null);
            }

            @Override
            protected void done() {
                progress.setIndeterminate(false);
                if (closed) {
                    return;
                }
                try {
                    showLoad(get());
                } catch (Exception ex) {
                    statusArea.setText(message(ex));
                }
            }
        };
        worker.execute();
    }

    private void showLoad(LoadOutcome outcome) {
        loaded = outcome;
        updateRuleWarning(outcome);
        candidate = null;
        baselineScore = null;
        proven.setVisible(false);
        OptimizationProblem problem = outcome.problem();
        boolean locked = problem != null && problem.locked();
        lockLabel.setVisible(locked || setlist.locked());
        if (problem == null || !outcome.report().integrityOk()) {
            rulesLabel.setText(" ");
            searchButton.setEnabled(false);
            StringBuilder text = new StringBuilder("Search is unavailable.");
            for (Diagnostic diagnostic : outcome.report().diagnostics()) {
                text.append('\n').append(explain(diagnostic));
            }
            statusArea.setText(text.toString());
            fillMetrics(null, null);
            updateApplyEnabled();
            return;
        }
        OptimizationRules rules = outcome.rules();
        rulesLabel.setText("Target " + rules.targetChanges()
                + " or fewer changes. Weights: smooth " + weight(rules.smoothWeight())
                + ", total " + weight(rules.totalWeight())
                + ", instrument preferences " + weight(rules.roleWeight())
                + ", keep " + weight(rules.keepWeight())
                + ". Pins: " + problem.pins().size() + ".");
        if (!durationInitialized && outcome.searchBudget() != null) {
            budget.setSelectedItem(RunBudget.from(outcome.searchBudget()));
            int remembered = readDuration.getAsInt();
            if (remembered > 0) {
                boolean preset = false;
                for (RunBudget value : RunBudget.values()) if (value.preset != null && value.preset.budget().toSeconds() == remembered) {
                    budget.setSelectedItem(value); preset = true; break;
                }
                if (!preset) selectCustomSeconds(Math.min(CUSTOM_SECONDS_MAX, remembered));
            }
            durationInitialized = true;
        }
        StringBuilder text = new StringBuilder();
        if (locked) {
            text.append("This setlist is locked. Search can run, and Apply stays off.\n");
        }
        if (!outcome.report().baselineFeasible()) {
            long unassigned = outcome.report().diagnostics().stream().filter(d -> "uncovered_part".equals(d.code())).count();
            text.append(unassigned > 0
                    ? unassigned + " parts need assignments. Search will assign them to characters who own the instruments."
                    : "The current assignments break a rule. Search can still look for a repair.");
            for (Diagnostic diagnostic : outcome.report().diagnostics()) {
                if (diagnostic.severity() == DiagnosticSeverity.BASELINE && !"uncovered_part".equals(diagnostic.code())) {
                    text.append('\n').append(explain(diagnostic));
                }
            }
        } else {
            text.append("Ready to search.");
        }
        statusArea.setText(text.toString());
        searchButton.setEnabled(true);
        if (outcome.report().baselineFeasible()) {
            Assignment baseline = Assignment.fromBaseline(problem);
            ScoreOutcome scored = AssignmentScorer.score(problem, baseline, rules);
            if (scored instanceof ScoreOutcome.Accepted accepted) {
                baselineScore = accepted.breakdown();
                fillMetrics(baselineScore, null);
                fillBurdens(AssignmentScorer.switchesByPlayer(problem, baseline), null);
            }
        }
        updateApplyEnabled();
    }

    private void updateRuleWarning(LoadOutcome outcome) {
        conflictItem = null; conflictPart = null;
        if (outcome.problem() == null) { ruleWarning.setVisible(false); return; }
        java.util.Set<Long> layoutPlayers = new java.util.HashSet<>();
        outcome.problem().performers().forEach(p -> layoutPlayers.add(p.playerId()));
        int conflicts = 0;
        for (var preference : outcome.rules().partPreferences()) {
            if (!layoutPlayers.contains(preference.playerId())) {
                conflicts++; conflictItem = preference.setlistItemId(); conflictPart = preference.partNumber();
            }
        }
        for (var pin : outcome.problem().pins()) {
            if (!layoutPlayers.contains(pin.playerId())) {
                conflicts++; conflictItem = pin.setlistItemId(); conflictPart = pin.partNumber();
            }
        }
        ruleWarning.setVisible(conflicts > 0);
        if (conflicts > 0) {
            ruleWarningText.setText(conflicts + (conflicts == 1 ? " saved rule refers" : " saved rules refer")
                    + " to characters outside this band layout and prevent auto-assignment. "
                    + (outcome.problem().locked() ? "Unlock the set to change these rules." : "Review them to change the character or clear incompatible rules."));
            fixRulesButton.setText(outcome.problem().locked() ? "Review rules…" : "Review and fix…");
        }
        revalidate(); repaint();
    }

    boolean ruleWarningVisible() { return ruleWarning.isVisible(); }

    private void startSearch() {
        if (!canSearch() || searching) {
            return;
        }
        cancelSearch.set(false);
        candidate = null;
        searching = true;
        bestLine = "Looking for a better assignment.";
        searchStarted = System.nanoTime();
        long seed = newSeed();
        Duration selected = selectedDuration();
        rememberDuration();
        settingsButton.setEnabled(false);
        fixRulesButton.setEnabled(false);
        searchButton.setEnabled(false);
        cancelButton.setEnabled(true);
        applyButton.setEnabled(false);
        proven.setText(BEST_SO_FAR);
        proven.setVisible(false);
        progress.setIndeterminate(true);
        elapsedTimer.start();
        SwingWorker<SearchOutcome, Void> worker = new SwingWorker<>() {
            @Override
            protected SearchOutcome doInBackground() {
                return solve(selected, seed, cancelSearch::get);
            }

            @Override
            protected void done() {
                elapsedTimer.stop();
                searching = false;
                settingsButton.setEnabled(true);
                fixRulesButton.setEnabled(true);
                progress.setIndeterminate(false);
                cancelButton.setEnabled(false);
                if (closed) {
                    return;
                }
                try {
                    finishSearch(get(), seed, System.nanoTime() - searchStarted);
                } catch (Exception ex) {
                    searchButton.setEnabled(canSearch());
                    statusArea.setText(message(ex));
                    updateApplyEnabled();
                }
            }
        };
        worker.execute();
    }

    private SearchOutcome solve(Duration budget, long seed, BooleanSupplier cancelled) {
        return SetlistOptimizationActions.search(
                loaded,
                budget,
                seed,
                cancelled,
                (breakdown, moves) -> SwingUtilities.invokeLater(() -> {
                    if (closed || !searching) {
                        return;
                    }
                    bestLine = "Best so far: weighted score " + weight(breakdown.weightedScore())
                            + ", " + moves + " improving moves.";
                    fillMetrics(baselineScore, breakdown);
                    proven.setText(BEST_SO_FAR);
                    proven.setVisible(true);
                }));
    }

    private void finishSearch(SearchOutcome outcome, long seed, long elapsedNanos) {
        searchButton.setEnabled(canSearch());
        if (outcome instanceof SearchOutcome.Solved solved) {
            candidate = solved.assignment();
            AssignmentComparison comparison = AssignmentComparison.compare(
                    loaded.problem(),
                    Assignment.fromBaseline(loaded.problem()),
                    solved.assignment(),
                    loaded.rules());
            fillMetrics(comparison.before(), comparison.after());
            fillChanges(comparison.changedSlots());
            fillBurdens(comparison.beforeSwitches(), comparison.afterSwitches());
            proven.setText(NOT_PROVEN);
            proven.setVisible(true);
            statusArea.setText(stopText(solved) + " " + NOT_PROVEN);
            diagnostics.setText("Seed: " + seed
                    + "\nStop: " + solved.reason().reportName()
                    + "\nImproving moves: " + solved.acceptedMoves()
                    + "\nAttempts: " + solved.evaluatedMoves()
                    + "\nElapsed: " + formatElapsed(elapsedNanos)
                    + "\n" + NOT_PROVEN);
        } else if (outcome instanceof SearchOutcome.Infeasible infeasible) {
            candidate = null;
            proven.setVisible(false);
            StringBuilder text = new StringBuilder("No valid assignment was found.");
            for (Diagnostic diagnostic : infeasible.diagnostics()) {
                text.append('\n').append(explain(diagnostic));
            }
            statusArea.setText(text.toString());
        }
        updateApplyEnabled();
    }

    private void applyCandidate() {
        if (candidate == null || loaded == null || loaded.problem() == null || loaded.problem().locked()) {
            return;
        }
        try {
            OptimizationApplyResult result = SetlistOptimizationActions.apply(journal, loaded, candidate);
            if (result.applied()) {
                onApplied.run();
                JOptionPane.showMessageDialog(
                        this,
                        "Applied the optimized assignments.",
                        "Optimize instruments",
                        JOptionPane.INFORMATION_MESSAGE);
                if (dialog != null) {
                    dialog.dispose();
                }
            } else {
                statusArea.setText(result.message());
            }
        } catch (LibraryException ex) {
            statusArea.setText(ex.getMessage());
        }
    }

    private boolean canSearch() {
        return loaded != null && loaded.problem() != null && loaded.report().integrityOk();
    }

    private void updateApplyEnabled() {
        boolean allowed = !searching
                && candidate != null
                && loaded != null
                && loaded.problem() != null
                && !loaded.problem().locked()
                && loaded.revision() != null;
        applyButton.setEnabled(allowed);
        applyButton.setToolTipText(allowed
                ? "Save this preview to the setlist"
                : "Apply stays off until a search finds an assignment and the setlist is unlocked");
    }

    private void fillMetrics(ScoreBreakdown before, ScoreBreakdown after) {
        metrics.setRowCount(0);
        metrics.addRow(new Object[] {"Changes", count(before, ScoreBreakdown::totalChanges), count(after, ScoreBreakdown::totalChanges)});
        metrics.addRow(new Object[] {"Worst transition", count(before, ScoreBreakdown::worstTransition), count(after, ScoreBreakdown::worstTransition)});
        metrics.addRow(new Object[] {
                "Above target",
                before == null ? DASH : Integer.toString(AssignmentComparison.aboveTarget(before)),
                after == null ? DASH : Integer.toString(AssignmentComparison.aboveTarget(after))
        });
        metrics.addRow(new Object[] {"Distribution", histogram(before), histogram(after)});
        metrics.addRow(new Object[] {"Weighted score", score(before), score(after)});
        metrics.addRow(new Object[] {"Smooth cost", contribution(before, ScoreBreakdown::smoothContribution), contribution(after, ScoreBreakdown::smoothContribution)});
        metrics.addRow(new Object[] {"Total switch cost", contribution(before, ScoreBreakdown::totalContribution), contribution(after, ScoreBreakdown::totalContribution)});
        metrics.addRow(new Object[] {"Role cost", contribution(before, ScoreBreakdown::roleContribution), contribution(after, ScoreBreakdown::roleContribution)});
        metrics.addRow(new Object[] {"Keep cost", contribution(before, ScoreBreakdown::keepContribution), contribution(after, ScoreBreakdown::keepContribution)});
    }

    private void fillChanges(List<AssignmentComparison.SlotChange> slots) {
        changes.setRowCount(0);
        Map<Long, String> titles = songTitles();
        for (AssignmentComparison.SlotChange slot : slots) {
            changes.addRow(new Object[] {
                    titles.getOrDefault(slot.setlistItemId(), "Song " + slot.setlistItemId()),
                    playerName(slot.playerId()),
                    partLabel(slot.beforePart()),
                    partLabel(slot.afterPart())
            });
        }
    }

    private void fillBurdens(Map<Long, Integer> before, Map<Long, Integer> after) {
        burdens.setRowCount(0);
        if (loaded == null || loaded.problem() == null) {
            return;
        }
        for (Performer performer : loaded.problem().performers()) {
            long playerId = performer.playerId();
            burdens.addRow(new Object[] {
                    playerName(playerId),
                    before == null ? DASH : Integer.toString(before.getOrDefault(playerId, 0)),
                    after == null ? DASH : Integer.toString(after.getOrDefault(playerId, 0))
            });
        }
    }

    private Map<Long, String> songTitles() {
        Map<Long, String> titles = new HashMap<>();
        if (setlists == null || loaded == null) {
            return titles;
        }
        try {
            for (SetlistItemInfo item : setlists.listItems(loaded.setlistId())) {
                String title = item.songTitle();
                titles.put(item.id(), title == null || title.isBlank() ? "Song " + item.songId() : title);
            }
        } catch (LibraryException ex) {
            return titles;
        }
        return titles;
    }

    private String playerName(long playerId) {
        if (players != null) {
            try {
                PlayerInfo info = players.getPlayer(playerId);
                if (info != null && info.name() != null && !info.name().isBlank()) {
                    return info.name();
                }
            } catch (LibraryException ex) {
                return "Player " + playerId;
            }
        }
        return "Player " + playerId;
    }

    private static String explain(Diagnostic diagnostic) {
        String detail = diagnostic.detail() == null || diagnostic.detail().isBlank()
                ? ""
                : " (" + diagnostic.detail() + ")";
        return switch (diagnostic.code()) {
            case "pin_unknown_player", "rule_unknown_player" ->
                    "A saved optimizer rule refers to a character outside this band layout. "
                            + "Open Optimizer settings → Song exceptions to clear or change that rule." + detail;
            case "uncovered_part" -> "A part needs an assignment; Search can assign it automatically." + detail;
            case "missing_band_layout" -> "This setlist has no band layout.";
            case "setlist_not_found" -> "The setlist was not found.";
            case "missing_database" -> "The library database was not found.";
            case "unparsable_parts" -> "A song's parts could not be read." + detail;
            case "part_count_mismatch" -> "A song's part count does not match its parts." + detail;
            case "required_instrument" ->
                    "A player is set to always take an instrument on this song, "
                            + "and that assignment could not be made." + detail;
            default -> diagnostic.code().replace('_', ' ') + detail;
        };
    }

    private static String stopText(SearchOutcome.Solved solved) {
        return switch (solved.reason()) {
            case CANCELLED -> "Stopped. Kept the best valid assignment found.";
            case BUDGET -> "Stopped when the time budget ended.";
            case ATTEMPTS -> "Stopped when the comparison work limit was reached.";
            case LOCAL_OPTIMUM -> "Stopped at a local best.";
        };
    }

    private static String count(ScoreBreakdown breakdown, java.util.function.ToIntFunction<ScoreBreakdown> value) {
        return breakdown == null ? DASH : Integer.toString(value.applyAsInt(breakdown));
    }

    private static String contribution(
            ScoreBreakdown breakdown,
            java.util.function.ToDoubleFunction<ScoreBreakdown> value) {
        return breakdown == null ? DASH : weight(value.applyAsDouble(breakdown));
    }

    private static String score(ScoreBreakdown breakdown) {
        return breakdown == null ? DASH : weight(breakdown.weightedScore());
    }

    private static String histogram(ScoreBreakdown breakdown) {
        if (breakdown == null) {
            return DASH;
        }
        if (breakdown.histogram().isEmpty()) {
            return "none";
        }
        List<Integer> keys = new ArrayList<>(breakdown.histogram().keySet());
        keys.sort(Comparator.naturalOrder());
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            Integer key = keys.get(i);
            text.append(key).append('=').append(breakdown.histogram().get(key));
        }
        return text.toString();
    }

    private static String partLabel(Integer part) {
        return part == null ? "Idle" : "Part " + part;
    }

    private static String weight(double value) {
        if (Double.isFinite(value) && value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static String formatElapsed(long nanos) {
        return String.format(Locale.ROOT, "%.1f s", nanos / 1_000_000_000d);
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? DASH : value;
    }

    private static String message(Throwable failure) {
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        String text = cause.getMessage();
        return text == null || text.isBlank() ? "The optimization could not finish." : text;
    }

    private static long newSeed() {
        return ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
    }

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    void selectCustomSeconds(int seconds) {
        budget.setSelectedItem(RunBudget.CUSTOM);
        customSeconds.setValue(seconds);
    }

    void bindDurationPreferences(com.aevoreth.abcmm.domain.prefs.Preferences preferences,
            com.aevoreth.abcmm.domain.prefs.PreferencesStore store) {
        if (preferences == null) return;
        String key = "optimizer_duration:" + database.toAbsolutePath().normalize() + ":" + setlist.bandLayoutId();
        readDuration = () -> {
            Object value = preferences.extras().get(key);
            return value instanceof Number n ? n.intValue() : 0;
        };
        writeDuration = seconds -> {
            preferences.extras().put(key, seconds);
            if (store != null) {
                try { store.save(preferences); }
                catch (com.aevoreth.abcmm.domain.prefs.PreferencesException ex) {
                    System.getLogger(SetlistOptimizationPanel.class.getName()).log(System.Logger.Level.WARNING, "Could not remember optimizer duration", ex);
                }
            }
        };
    }

    void rememberDuration() { writeDuration.accept((int) selectedDuration().toSeconds()); }

    Duration selectedDuration() {
        try {
            customSeconds.commitEdit();
        } catch (ParseException ex) {
            // Keep the last valid spinner value.
        }
        RunBudget choice = (RunBudget) budget.getSelectedItem();
        if (choice == null) {
            choice = RunBudget.NORMAL;
        }
        if (choice == RunBudget.CUSTOM) {
            int seconds = ((Number) customSeconds.getValue()).intValue();
            return Duration.ofSeconds(Math.max(1, Math.min(CUSTOM_SECONDS_MAX, seconds)));
        }
        return choice.preset.budget();
    }

    private void syncCustomBudget(JLabel secondsCaption) {
        boolean custom = budget.getSelectedItem() == RunBudget.CUSTOM;
        if (custom) {
            customSeconds.setValue(rememberedCustomSeconds);
        } else if (customSeconds.isEnabled()) {
            rememberedCustomSeconds = ((Number) customSeconds.getValue()).intValue();
        }
        if (!custom) {
            RunBudget choice = (RunBudget) budget.getSelectedItem();
            if (choice != null && choice.preset != null) {
                customSeconds.setValue((int) choice.preset.budget().toSeconds());
            }
        }
        customSeconds.setEnabled(custom);
        secondsCaption.setEnabled(custom);
    }

    private static void fitOnScreen(JDialog dialog, Component parent) {
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        GraphicsConfiguration config = dialog.getGraphicsConfiguration();
        Rectangle screen = config == null
                ? new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())
                : config.getBounds();
        int maxWidth = Math.max(640, screen.width - 48);
        int maxHeight = Math.max(360, screen.height - 80);
        int width = Math.min(Math.max(dialog.getWidth(), 980), maxWidth);
        int height = Math.min(dialog.getHeight(), maxHeight);
        dialog.setSize(width, height);
        dialog.setLocationRelativeTo(parent);
    }

    private static JScrollPane scrolling(Component view, int width, int height) {
        JScrollPane scroll = new JScrollPane(view);
        scroll.setPreferredSize(new Dimension(width, height));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getHorizontalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private static ListCellRenderer<RunBudget> budgetRenderer() {
        return (list, value, index, selected, focus) -> {
            JLabel label = new JLabel(value == null ? "" : budgetLabel(value));
            label.setOpaque(true);
            if (selected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            }
            return label;
        };
    }

    private static String budgetLabel(RunBudget choice) {
        return switch (choice) {
            case QUICK -> "Quick (2 seconds)";
            case NORMAL -> "Normal (10 seconds)";
            case THOROUGH -> "Thorough (60 seconds)";
            case CUSTOM -> "Custom";
        };
    }

    /**
     * Grows with a tall window so the three columns fill it, and keeps its preferred
     * height when the window is shorter so the outer scroll bars can appear.
     */
    private static final class FittingBody extends JPanel implements Scrollable {

        private FittingBody() {
            super(new BorderLayout(0, 8));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(16, visible.height - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport viewport
                    && viewport.getHeight() > getPreferredSize().height;
        }
    }

    private enum RunBudget {
        QUICK(SearchBudgetPreset.QUICK),
        NORMAL(SearchBudgetPreset.NORMAL),
        THOROUGH(SearchBudgetPreset.THOROUGH),
        CUSTOM(null);

        private final SearchBudgetPreset preset;

        RunBudget(SearchBudgetPreset preset) {
            this.preset = preset;
        }

        static RunBudget from(SearchBudgetPreset preset) {
            for (RunBudget choice : values()) {
                if (choice.preset == preset) {
                    return choice;
                }
            }
            return NORMAL;
        }
    }
}

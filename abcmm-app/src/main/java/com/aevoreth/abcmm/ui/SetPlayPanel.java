package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import com.aevoreth.abcmm.domain.band.BandLayoutSlotInfo;
import com.aevoreth.abcmm.domain.band.BandRepository;
import com.aevoreth.abcmm.domain.band.PlayerRepository;
import com.aevoreth.abcmm.domain.band.SongLayoutRepository;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.library.PlayLogRepository;
import com.aevoreth.abcmm.domain.setlist.SetlistInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistItemInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistRepository;
import com.aevoreth.abcmm.domain.setplay.SetPlayLayoutBuilder;
import com.aevoreth.abcmm.domain.setplay.SetPlayLayoutCard;
import com.aevoreth.abcmm.domain.setplay.SetPlaySessionRules;
import com.aevoreth.abcmm.domain.setplay.SetPlaySessionState;

/**
 * Solo (local) Set Play leader view: NOW/NEXT/Skip/Advance, play logging, up-next band grid.
 * Relay / Band Assistant are out of scope for this milestone.
 */
public final class SetPlayPanel extends JPanel {

    private static final int COL_STATUS = 0;
    private static final int COL_SKIP = 1;
    private static final int COL_TITLE = 2;
    private static final int COL_PARTS = 3;
    private static final int COL_DUR = 4;
    private static final int COL_ARTIST = 5;
    private static final int COL_ACTIONS = 6;

    private static final Color STATUS_NOW = new Color(0x4C_AF_50);
    private static final Color STATUS_NEXT = new Color(0x5C_9F_D6);
    private static final Color STATUS_SKIP = new Color(0xE0_5A_5A);

    private SetlistRepository setlistRepository;
    private BandRepository bandRepository;
    private PlayLogRepository playLogRepository;
    private SetPlayLayoutBuilder layoutBuilder;

    private SetPlaySessionState session = new SetPlaySessionState(List.of());
    private final List<SetlistItemInfo> songRows = new ArrayList<>();
    private SetlistInfo loadedSetlist;
    private final Set<Long> highlightPlayers = new HashSet<>();
    private boolean checkboxGuard;

    private final JLabel setlistNameLabel = new JLabel("—");
    private final SetlistPickerCombo setlistCombo = new SetlistPickerCombo();
    private final JButton loadBtn = new JButton("Load set");
    private final JLabel infoLabel = new JLabel("Select a setlist and click Load set.");
    private final JButton markSetBtn = new JButton("Mark set as played (all non-skipped)…");
    private final JButton advanceBtn = new JButton("Advance song");
    private final JCheckBox autoLogCheck = new JCheckBox("Mark songs as played automatically");
    private final JLabel statusLabel = new JLabel(" ");
    private final SongTableModel tableModel = new SongTableModel();
    private final JTable table = new JTable(tableModel);
    private final JPanel playersInner = new JPanel();
    private final SetPlayBandGridPanel gridPanel = new SetPlayBandGridPanel();
    private final StatusCellRenderer statusRenderer = new StatusCellRenderer();

    public SetPlayPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        Font nameFont = setlistNameLabel.getFont().deriveFont(Font.BOLD, setlistNameLabel.getFont().getSize2D() + 2f);
        setlistNameLabel.setFont(nameFont);

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
        setlistNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(setlistNameLabel);
        left.add(Box.createVerticalStrut(8));

        JPanel pickRow = new JPanel(new BorderLayout(6, 0));
        pickRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        pickRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, setlistCombo.getPreferredSize().height));
        JLabel setlistLbl = new JLabel("Setlist:");
        pickRow.add(setlistLbl, BorderLayout.WEST);
        pickRow.add(setlistCombo, BorderLayout.CENTER);
        int btnH = setlistCombo.getPreferredSize().height;
        loadBtn.setMargin(new java.awt.Insets(2, 10, 2, 10));
        Dimension loadPref = new Dimension(loadBtn.getPreferredSize().width, btnH);
        loadBtn.setPreferredSize(loadPref);
        loadBtn.setMinimumSize(loadPref);
        loadBtn.setMaximumSize(loadPref);
        pickRow.add(loadBtn, BorderLayout.EAST);
        left.add(pickRow);
        left.add(Box.createVerticalStrut(8));

        infoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoLabel.setVerticalAlignment(JLabel.TOP);
        left.add(infoLabel);
        left.add(Box.createVerticalStrut(12));

        markSetBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        markSetBtn.setEnabled(false);
        left.add(markSetBtn);
        left.add(Box.createVerticalStrut(8));

        advanceBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        advanceBtn.setMinimumSize(new Dimension(200, 48));
        advanceBtn.setPreferredSize(new Dimension(280, 52));
        advanceBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        Font advFont = advanceBtn.getFont().deriveFont(Font.BOLD, advanceBtn.getFont().getSize2D() + 3f);
        advanceBtn.setFont(advFont);
        advanceBtn.setEnabled(false);
        left.add(advanceBtn);
        left.add(Box.createVerticalStrut(6));

        autoLogCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoLogCheck.setToolTipText(
                "When advancing, record the new current song in the library play history.");
        left.add(autoLogCheck);
        left.add(Box.createVerticalGlue());

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(false);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getTableHeader().setReorderingAllowed(false);
        table.setToolTipText("Double-click a row to set it as Next. Right-click for more actions.");
        sizeColumns();
        statusRenderer.setHorizontalAlignment(JLabel.CENTER);
        table.getColumnModel().getColumn(COL_STATUS).setCellRenderer(statusRenderer);
        table.getColumnModel().getColumn(COL_TITLE).setCellRenderer(statusRenderer);
        table.getColumnModel().getColumn(COL_PARTS).setCellRenderer(statusRenderer);
        table.getColumnModel().getColumn(COL_DUR).setCellRenderer(statusRenderer);
        table.getColumnModel().getColumn(COL_ARTIST).setCellRenderer(statusRenderer);
        table.getColumnModel().getColumn(COL_ACTIONS).setCellRenderer(new ActionsRenderer());
        table.getColumnModel().getColumn(COL_ACTIONS).setCellEditor(new ActionsEditor());

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        actionSetNext(songRows.get(row).id());
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowContextMenu(e);
            }
        });

        JScrollPane tableScroll = new JScrollPane(table);
        JPanel tablePanel = new JPanel(new BorderLayout(0, 4));
        tablePanel.add(tableScroll, BorderLayout.CENTER);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        tablePanel.add(statusLabel, BorderLayout.SOUTH);

        JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, tablePanel);
        topSplit.setResizeWeight(0.28);
        topSplit.setContinuousLayout(true);

        playersInner.setLayout(new BoxLayout(playersInner, BoxLayout.Y_AXIS));
        JScrollPane playersScroll = new JScrollPane(playersInner);
        playersScroll.setBorder(BorderFactory.createTitledBorder("Your players"));
        playersScroll.setPreferredSize(new Dimension(180, 200));
        playersScroll.setMinimumSize(new Dimension(140, 120));

        JSplitPane bottomSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, playersScroll, gridPanel);
        bottomSplit.setResizeWeight(0.22);
        bottomSplit.setContinuousLayout(true);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplit, bottomSplit);
        mainSplit.setResizeWeight(0.45);
        mainSplit.setContinuousLayout(true);
        mainSplit.setDividerLocation(320);

        add(mainSplit, BorderLayout.CENTER);

        loadBtn.addActionListener(e -> loadSet());
        advanceBtn.addActionListener(e -> advance());
        markSetBtn.addActionListener(e -> markSetAsPlayed());
    }

    public void bind(
            SetlistRepository setlistRepository,
            BandRepository bandRepository,
            PlayerRepository playerRepository,
            SongLayoutRepository songLayoutRepository,
            PlayLogRepository playLogRepository) {
        this.setlistRepository = setlistRepository;
        this.bandRepository = bandRepository;
        this.playLogRepository = playLogRepository;
        if (setlistRepository != null
                && bandRepository != null
                && playerRepository != null
                && songLayoutRepository != null) {
            layoutBuilder = new SetPlayLayoutBuilder(
                    bandRepository, playerRepository, setlistRepository, songLayoutRepository);
        } else {
            layoutBuilder = null;
        }
        refreshSetlistPicker();
    }

    /** Refresh setlist combo from the repository (call when tab shown or setlists change). */
    public void refreshSetlistPicker() {
        Long previous = selectedSetlistId();
        if (setlistRepository == null) {
            setlistCombo.populate(List.of(), List.of(), null);
            return;
        }
        try {
            setlistCombo.populate(
                    setlistRepository.listFolders(),
                    setlistRepository.listSetlists(),
                    previous);
        } catch (LibraryException ex) {
            statusLabel.setText(ex.getMessage() == null ? "Failed to load setlists." : ex.getMessage());
            setlistCombo.populate(List.of(), List.of(), null);
        }
    }

    public void onShown() {
        refreshSetlistPicker();
        SwingUtilities.invokeLater(gridPanel::fitCardsToView);
    }

    private Long selectedSetlistId() {
        return setlistCombo.getSelectedSetlistId();
    }

    private void loadSet() {
        if (setlistRepository == null) {
            return;
        }
        Long sid = selectedSetlistId();
        if (sid == null) {
            JOptionPane.showMessageDialog(this, "Select a setlist.", "Set Play", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            SetlistInfo found = null;
            for (SetlistInfo s : setlistRepository.listSetlists()) {
                if (s.id() == sid) {
                    found = s;
                    break;
                }
            }
            if (found == null) {
                JOptionPane.showMessageDialog(this, "Setlist not found.", "Set Play", JOptionPane.WARNING_MESSAGE);
                return;
            }
            loadedSetlist = found;
            songRows.clear();
            songRows.addAll(setlistRepository.listItems(sid));
            List<Long> order = new ArrayList<>();
            for (SetlistItemInfo row : songRows) {
                order.add(row.id());
            }
            session = new SetPlaySessionState(order);
            setlistNameLabel.setText(found.name());
            advanceBtn.setEnabled(!songRows.isEmpty());
            markSetBtn.setEnabled(!songRows.isEmpty());
            highlightPlayers.clear();
            refreshAll();
            SwingUtilities.invokeLater(gridPanel::fitCardsToView);
            statusLabel.setText("Loaded \"" + found.name() + "\" (" + songRows.size() + " songs).");
        } catch (LibraryException ex) {
            statusLabel.setText(ex.getMessage() == null ? "Failed to load set." : ex.getMessage());
        }
    }

    private void refreshAll() {
        refreshInfo();
        checkboxGuard = true;
        tableModel.fireTableDataChanged();
        checkboxGuard = false;
        refreshPlayers();
        refreshGrid();
        advanceBtn.setEnabled(loadedSetlist != null && !songRows.isEmpty());
        markSetBtn.setEnabled(loadedSetlist != null && !songRows.isEmpty());
    }

    private void afterStateChange() {
        checkboxGuard = true;
        tableModel.fireTableDataChanged();
        checkboxGuard = false;
        refreshInfo();
        refreshGrid();
    }

    private void refreshInfo() {
        if (loadedSetlist == null) {
            infoLabel.setText("Select a setlist and click Load set.");
            return;
        }
        int totalSec = 0;
        for (SetlistItemInfo row : songRows) {
            if (row.songDurationSeconds() != null) {
                totalSec += row.songDurationSeconds();
            }
        }
        int n = songRows.size();
        int delay = loadedSetlist.defaultChangeDurationSeconds() == null
                ? 0
                : loadedSetlist.defaultChangeDurationSeconds();
        int switchSec = n > 1 ? delay * (n - 1) : 0;
        int withSwitches = totalSec + switchSec;
        StringBuilder sb = new StringBuilder("<html>");
        sb.append(n).append(" song").append(n == 1 ? "" : "s");
        sb.append(" · ").append(LibraryDisplayFormats.formatDuration(withSwitches));
        if (loadedSetlist.targetDurationSeconds() != null && loadedSetlist.targetDurationSeconds() > 0) {
            int rem = loadedSetlist.targetDurationSeconds() - withSwitches;
            sb.append(" · target ")
                    .append(LibraryDisplayFormats.formatHoursMinutes(loadedSetlist.targetDurationSeconds()));
            sb.append(" (").append(rem >= 0 ? "+" : "").append(LibraryDisplayFormats.formatDuration(Math.abs(rem)));
            sb.append(rem >= 0 ? " under)" : " over)");
        }
        if (loadedSetlist.bandLayoutId() == null) {
            sb.append("<br>No band layout — up-next grid unavailable.");
        }
        sb.append("</html>");
        infoLabel.setText(sb.toString());
    }

    private SetlistItemInfo rowForItem(Long itemId) {
        if (itemId == null) {
            return null;
        }
        for (SetlistItemInfo row : songRows) {
            if (row.id() == itemId) {
                return row;
            }
        }
        return null;
    }

    private void refreshGrid() {
        if (layoutBuilder == null
                || loadedSetlist == null
                || loadedSetlist.bandLayoutId() == null) {
            gridPanel.clear();
            return;
        }
        try {
            SetlistItemInfo nextRow = rowForItem(session.nextItemId());
            SetlistItemInfo curRow = rowForItem(session.currentItemId());
            Long rightId = null;
            if (session.nextItemId() != null) {
                int ni = session.orderItemIds().indexOf(session.nextItemId());
                if (ni >= 0) {
                    rightId = SetPlaySessionRules.scanNextItemId(
                            session.orderItemIds(), session.skippedItemIds(), ni);
                }
            }
            SetlistItemInfo rightRow = rowForItem(rightId);
            List<SetPlayLayoutCard> cards = layoutBuilder.build(
                    loadedSetlist.bandLayoutId(),
                    nextRow,
                    curRow,
                    rightRow,
                    songRows);
            gridPanel.setCards(cards);
            gridPanel.setHighlightPlayerIds(highlightPlayers);
        } catch (LibraryException ex) {
            gridPanel.clear();
            statusLabel.setText(ex.getMessage() == null ? "Failed to refresh layout." : ex.getMessage());
        }
    }

    private void refreshPlayers() {
        playersInner.removeAll();
        Map<Long, String> players = new LinkedHashMap<>();
        if (loadedSetlist != null
                && loadedSetlist.bandLayoutId() != null
                && bandRepository != null) {
            try {
                for (BandLayoutSlotInfo slot : bandRepository.listSlots(loadedSetlist.bandLayoutId())) {
                    String name = slot.playerName() == null || slot.playerName().isBlank()
                            ? ("#" + slot.playerId())
                            : slot.playerName();
                    players.put(slot.playerId(), name);
                }
            } catch (LibraryException ex) {
                statusLabel.setText(ex.getMessage() == null ? "Failed to load players." : ex.getMessage());
            }
        }
        List<Map.Entry<Long, String>> sorted = new ArrayList<>(players.entrySet());
        sorted.sort(Comparator.comparing(e -> e.getValue().toLowerCase(Locale.ROOT)));
        for (Map.Entry<Long, String> entry : sorted) {
            long pid = entry.getKey();
            JCheckBox cb = new JCheckBox(entry.getValue());
            cb.setSelected(highlightPlayers.contains(pid));
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);
            cb.addActionListener(e -> {
                if (cb.isSelected()) {
                    highlightPlayers.add(pid);
                } else {
                    highlightPlayers.remove(pid);
                }
                gridPanel.setHighlightPlayerIds(highlightPlayers);
            });
            playersInner.add(cb);
        }
        playersInner.revalidate();
        playersInner.repaint();
    }

    private void advance() {
        if (session.nextItemId() == null) {
            statusLabel.setText("Choose a Next song before advancing.");
            return;
        }
        if (!SetPlaySessionRules.advanceSong(session)) {
            return;
        }
        if (autoLogCheck.isSelected()
                && playLogRepository != null
                && loadedSetlist != null
                && session.currentItemId() != null) {
            SetlistItemInfo row = rowForItem(session.currentItemId());
            if (row != null) {
                try {
                    playLogRepository.logPlay(row.songId(), loadedSetlist.id(), null);
                } catch (LibraryException ex) {
                    statusLabel.setText(ex.getMessage() == null ? "Failed to log play." : ex.getMessage());
                }
            }
        }
        afterStateChange();
        statusLabel.setText("Advanced.");
    }

    private void markSetAsPlayed() {
        if (loadedSetlist == null || playLogRepository == null) {
            return;
        }
        List<SetlistItemInfo> toMark = new ArrayList<>();
        for (SetlistItemInfo row : songRows) {
            if (!session.skippedItemIds().contains(row.id())) {
                toMark.add(row);
            }
        }
        if (toMark.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this, "No songs to mark (all skipped).", "Set Play", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        PlayDateTimeDialog dialog = new PlayDateTimeDialog(
                SwingUtilities.getWindowAncestor(this),
                "Mark set as played",
                Instant.now(),
                null,
                false);
        var result = dialog.showDialog();
        if (result.isEmpty()) {
            return;
        }
        String iso = result.get().playedAtIso();
        int logged = 0;
        try {
            for (SetlistItemInfo row : toMark) {
                playLogRepository.logPlayAt(row.songId(), iso, loadedSetlist.id(), null);
                session.playedItemIds().add(row.id());
                logged++;
            }
            if (session.currentItemId() != null
                    && session.playedItemIds().contains(session.currentItemId())) {
                session.setCurrentItemId(null);
            }
            if (session.nextItemId() != null
                    && session.playedItemIds().contains(session.nextItemId())) {
                session.setNextItemId(null);
            }
            session.bumpRevision();
            afterStateChange();
            statusLabel.setText("Logged " + logged + " song(s) as played.");
            JOptionPane.showMessageDialog(
                    this,
                    "Recorded play time for " + logged + " song(s).",
                    "Set Play",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (LibraryException ex) {
            statusLabel.setText(ex.getMessage() == null ? "Failed to mark set played." : ex.getMessage());
        }
    }

    private void actionSetCurrent(long itemId) {
        SetPlaySessionRules.applyExclusiveCurrent(session, itemId);
        afterStateChange();
    }

    private void actionSetNext(long itemId) {
        SetPlaySessionRules.applyExclusiveNext(session, itemId);
        afterStateChange();
    }

    private void actionMarkPlayed(long itemId) {
        boolean wasPlayed = session.playedItemIds().contains(itemId);
        SetPlaySessionRules.togglePlayed(session, itemId);
        if (!wasPlayed && playLogRepository != null && loadedSetlist != null) {
            SetlistItemInfo row = rowForItem(itemId);
            if (row != null) {
                try {
                    playLogRepository.logPlay(row.songId(), loadedSetlist.id(), null);
                } catch (LibraryException ex) {
                    statusLabel.setText(ex.getMessage() == null ? "Failed to log play." : ex.getMessage());
                }
            }
        }
        afterStateChange();
    }

    private void actionLogAt(long itemId) {
        if (playLogRepository == null || loadedSetlist == null) {
            return;
        }
        SetlistItemInfo row = rowForItem(itemId);
        if (row == null) {
            return;
        }
        PlayDateTimeDialog dialog = new PlayDateTimeDialog(
                SwingUtilities.getWindowAncestor(this),
                "Log play at time",
                Instant.now(),
                null,
                true);
        var result = dialog.showDialog();
        if (result.isEmpty()) {
            return;
        }
        try {
            playLogRepository.logPlayAt(
                    row.songId(),
                    result.get().playedAtIso(),
                    loadedSetlist.id(),
                    result.get().contextNote());
            if (!session.playedItemIds().contains(itemId)) {
                SetPlaySessionRules.togglePlayed(session, itemId);
            }
            afterStateChange();
            statusLabel.setText("Logged play for \"" + row.songTitle() + "\".");
        } catch (LibraryException ex) {
            statusLabel.setText(ex.getMessage() == null ? "Failed to log play." : ex.getMessage());
        }
    }

    private void toggleSkip(long itemId) {
        SetPlaySessionRules.toggleSkip(session, itemId);
        afterStateChange();
    }

    private void maybeShowContextMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int row = table.rowAtPoint(e.getPoint());
        if (row < 0) {
            return;
        }
        table.getSelectionModel().setSelectionInterval(row, row);
        showSongActionsMenu(songRows.get(row).id(), e.getComponent(), e.getX(), e.getY());
    }

    private void showSongActionsMenu(long itemId, Component invoker, int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem setCurrent = new JMenuItem("Set current");
        setCurrent.addActionListener(e -> actionSetCurrent(itemId));
        menu.add(setCurrent);
        JMenuItem setNext = new JMenuItem("Set next");
        setNext.addActionListener(e -> actionSetNext(itemId));
        menu.add(setNext);
        menu.addSeparator();
        boolean played = session.playedItemIds().contains(itemId);
        JMenuItem markPlayed = new JMenuItem(played ? "Clear played" : "Mark played");
        markPlayed.addActionListener(e -> actionMarkPlayed(itemId));
        menu.add(markPlayed);
        JMenuItem logAt = new JMenuItem("Log at time…");
        logAt.addActionListener(e -> actionLogAt(itemId));
        menu.add(logAt);
        menu.show(invoker, x, y);
    }

    private void sizeColumns() {
        setColWidth(COL_STATUS, 56, 72);
        setColWidth(COL_SKIP, 48, 56);
        setColWidth(COL_PARTS, 48, 64);
        setColWidth(COL_DUR, 64, 80);
        setColWidth(COL_ARTIST, 120, 200);
        setColWidth(COL_ACTIONS, 72, 90);
        table.getColumnModel().getColumn(COL_TITLE).setPreferredWidth(220);
    }

    private void setColWidth(int col, int min, int pref) {
        TableColumn column = table.getColumnModel().getColumn(col);
        column.setMinWidth(min);
        column.setPreferredWidth(pref);
        column.setMaxWidth(pref * 2);
    }

    private RowStyle rowStyle(long itemId) {
        if (session.skippedItemIds().contains(itemId)) {
            return RowStyle.SKIP;
        }
        if (Objects.equals(session.currentItemId(), itemId)) {
            return RowStyle.NOW;
        }
        if (Objects.equals(session.nextItemId(), itemId)) {
            return RowStyle.NEXT;
        }
        if (session.playedItemIds().contains(itemId)) {
            return RowStyle.PLAYED;
        }
        return RowStyle.NORMAL;
    }

    private static Color playedGrey(JTable table) {
        Color fg = table.getForeground();
        Color bg = table.getBackground();
        if (fg == null) {
            fg = UIManager.getColor("Table.foreground");
        }
        if (bg == null) {
            bg = UIManager.getColor("Table.background");
        }
        if (fg == null) {
            fg = Color.BLACK;
        }
        if (bg == null) {
            bg = Color.WHITE;
        }
        // Blend toward background so played rows read as muted in both themes.
        float t = 0.45f;
        int r = Math.round(fg.getRed() * (1 - t) + bg.getRed() * t);
        int g = Math.round(fg.getGreen() * (1 - t) + bg.getGreen() * t);
        int b = Math.round(fg.getBlue() * (1 - t) + bg.getBlue() * t);
        return new Color(r, g, b);
    }

    private enum RowStyle {
        SKIP,
        NOW,
        NEXT,
        PLAYED,
        NORMAL
    }

    private final class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (row < 0 || row >= songRows.size()) {
                return c;
            }
            long itemId = songRows.get(row).id();
            RowStyle style = rowStyle(itemId);
            Font base = table.getFont();
            Font font = base;
            Color fg = table.getForeground();
            switch (style) {
                case SKIP -> {
                    Map<java.awt.font.TextAttribute, Object> attrs = new HashMap<>(base.getAttributes());
                    attrs.put(
                            java.awt.font.TextAttribute.STRIKETHROUGH,
                            java.awt.font.TextAttribute.STRIKETHROUGH_ON);
                    font = base.deriveFont(attrs);
                    fg = STATUS_SKIP;
                }
                case NOW -> fg = STATUS_NOW;
                case NEXT -> fg = STATUS_NEXT;
                case PLAYED -> fg = playedGrey(table);
                case NORMAL -> fg = table.getForeground();
            }
            if (column == COL_STATUS) {
                setHorizontalAlignment(CENTER);
            } else if (column == COL_PARTS || column == COL_DUR) {
                setHorizontalAlignment(CENTER);
            } else {
                setHorizontalAlignment(LEFT);
            }
            setFont(font);
            if (isSelected) {
                setBackground(table.getSelectionBackground());
                // Keep status hue visible on selection when possible.
                setForeground(style == RowStyle.NORMAL ? table.getSelectionForeground() : fg);
            } else {
                setBackground(table.getBackground());
                setForeground(fg);
            }
            return c;
        }
    }

    private final class SongTableModel extends AbstractTableModel {
        private final String[] columns = {
                "Status", "Skip", "Title", "Parts", "Duration", "Artist", "Actions"
        };

        @Override
        public int getRowCount() {
            return songRows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == COL_SKIP) {
                return Boolean.class;
            }
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == COL_SKIP || columnIndex == COL_ACTIONS;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            SetlistItemInfo row = songRows.get(rowIndex);
            return switch (columnIndex) {
                case COL_STATUS -> SetPlaySessionRules.statusBadgeText(session, row.id());
                case COL_SKIP -> session.skippedItemIds().contains(row.id());
                case COL_TITLE -> row.songTitle() == null ? "" : row.songTitle();
                case COL_PARTS -> row.partCount() > 0 ? String.valueOf(row.partCount()) : "—";
                case COL_DUR -> LibraryDisplayFormats.formatDuration(row.songDurationSeconds());
                case COL_ARTIST -> row.songComposers() == null || row.songComposers().isBlank()
                        ? "—"
                        : row.songComposers();
                case COL_ACTIONS -> "…";
                default -> "";
            };
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (checkboxGuard || columnIndex != COL_SKIP || rowIndex < 0 || rowIndex >= songRows.size()) {
                return;
            }
            boolean wantSkip = Boolean.TRUE.equals(aValue);
            long itemId = songRows.get(rowIndex).id();
            boolean isSkip = session.skippedItemIds().contains(itemId);
            if (wantSkip != isSkip) {
                toggleSkip(itemId);
            }
        }
    }

    private static final class ActionsRenderer extends JButton implements TableCellRenderer {
        ActionsRenderer() {
            setText("…");
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else {
                setBackground(table.getBackground());
                setForeground(table.getForeground());
            }
            return this;
        }
    }

    private final class ActionsEditor extends AbstractCellEditor implements TableCellEditor {
        private final JButton button = new JButton("…");
        private int editingRow = -1;

        ActionsEditor() {
            button.addActionListener(e -> {
                if (editingRow >= 0 && editingRow < songRows.size()) {
                    Point p = button.getLocationOnScreen();
                    Point tablePos = table.getLocationOnScreen();
                    showSongActionsMenu(
                            songRows.get(editingRow).id(),
                            table,
                            p.x - tablePos.x,
                            p.y - tablePos.y + button.getHeight());
                }
                fireEditingStopped();
            });
        }

        @Override
        public Component getTableCellEditorComponent(
                JTable table, Object value, boolean isSelected, int row, int column) {
            editingRow = row;
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            return "…";
        }
    }
}

package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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
import java.util.Optional;
import java.util.Set;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
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
import com.aevoreth.abcmm.domain.prefs.Preferences;
import com.aevoreth.abcmm.domain.setlist.SetlistInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistItemInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistRepository;
import com.aevoreth.abcmm.domain.setplay.SetPlayLayoutBuilder;
import com.aevoreth.abcmm.domain.setplay.SetPlayLayoutCard;
import com.aevoreth.abcmm.domain.setplay.SetPlaySessionRules;
import com.aevoreth.abcmm.domain.setplay.SetPlaySessionState;
import com.aevoreth.abcmm.domain.setplay.relay.SetPlayRelayClient;
import com.aevoreth.abcmm.domain.setplay.relay.SetPlayRelayHttp;
import com.aevoreth.abcmm.domain.setplay.relay.SetPlayShareUrls;
import com.aevoreth.abcmm.domain.setplay.relay.SetPlaySync;

/**
 * Set Play leader (solo + optional Cloudflare relay broadcast) or Band Assistant
 * (read-only follower synced via relay).
 */
public final class SetPlayPanel extends JPanel {

    /** Extras key in preferences.json for Set Play divider sizes. */
    static final String SPLIT_PREF_KEY = "set_play_splitter_state";

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

    private static final int MAIN_SPLIT_DEFAULT = 320;
    private static final int MAIN_SPLIT_MIN = 160;
    private static final int TOP_SPLIT_MIN = 200;
    private static final int BOTTOM_SPLIT_MIN = 120;
    private static final int SECONDARY_PANE_MIN = 80;

    private final boolean assistantMode;

    private SetlistRepository setlistRepository;
    private BandRepository bandRepository;
    private PlayLogRepository playLogRepository;
    private SetPlayLayoutBuilder layoutBuilder;
    private Preferences preferences;
    private Runnable preferencesSaver;

    private SetPlaySessionState session = new SetPlaySessionState(List.of());
    private final List<SetlistItemInfo> songRows = new ArrayList<>();
    private SetlistInfo loadedSetlist;
    private List<SetPlayLayoutCard> layoutCards = List.of();
    private final Set<Long> highlightPlayers = new HashSet<>();
    private boolean checkboxGuard;
    private boolean splitsRestored;
    private boolean relayComboGuard;

    private final SetPlayRelayClient relay;
    private final SetPlayRelayHttp relayHttp = new SetPlayRelayHttp();
    private String relayCode;
    private String relayLeaderToken;
    private String relayShareUrl;
    private int lastPushedRevision = -1;
    private int broadcastGeneration;
    private final JLabel setlistNameLabel = new JLabel("—");
    private final SetlistPickerCombo setlistCombo = new SetlistPickerCombo();
    private final JButton loadBtn = new JButton("Load set");
    private final JComboBox<RelayItem> relayCombo = new JComboBox<>();
    private final JCheckBox broadcastCheck = new JCheckBox("Broadcast (Cloudflare relay)");
    private final JButton copyLinkBtn = new JButton("Copy link");
    private final JButton leaderReconnectBtn = new JButton("Reconnect");
    private final JLabel roomLabel = new JLabel("");
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

    private JTextField assistantLinkField;
    private JButton assistantConnectBtn;
    private JButton assistantDisconnectBtn;
    private JButton assistantReconnectBtn;
    private JLabel bannerCurrent;
    private JLabel bannerNext;

    private JSplitPane topSplit;
    private JSplitPane bottomSplit;
    private JSplitPane mainSplit;

    public SetPlayPanel() {
        this(false);
    }

    public SetPlayPanel(boolean assistantMode) {
        super(new BorderLayout(6, 6));
        this.assistantMode = assistantMode;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        relay = new SetPlayRelayClient(new SetPlayRelayClient.Listener() {
            @Override
            public void onConnected() {
                SwingUtilities.invokeLater(SetPlayPanel.this::onRelayConnected);
            }

            @Override
            public void onDisconnected() {
                SwingUtilities.invokeLater(SetPlayPanel.this::onRelayDisconnected);
            }

            @Override
            public void onStateReceived(Map<String, Object> data) {
                SwingUtilities.invokeLater(() -> onRelayState(data));
            }

            @Override
            public void onError(String message) {
                SwingUtilities.invokeLater(() -> onRelayError(message));
            }
        });

        Font nameFont = setlistNameLabel.getFont().deriveFont(Font.BOLD, setlistNameLabel.getFont().getSize2D() + 2f);
        setlistNameLabel.setFont(nameFont);

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));

        if (assistantMode) {
            buildAssistantLeft(left);
        } else {
            buildLeaderLeft(left);
        }

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(false);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getTableHeader().setReorderingAllowed(false);
        sizeColumns();
        if (assistantMode) {
            hideColumn(COL_SKIP);
            hideColumn(COL_ACTIONS);
        } else {
            table.setToolTipText("Double-click a row to set it as Next. Right-click for more actions.");
        }
        statusRenderer.setHorizontalAlignment(JLabel.CENTER);
        table.getColumnModel().getColumn(COL_STATUS).setCellRenderer(statusRenderer);
        table.getColumnModel().getColumn(COL_TITLE).setCellRenderer(statusRenderer);
        table.getColumnModel().getColumn(COL_PARTS).setCellRenderer(statusRenderer);
        table.getColumnModel().getColumn(COL_DUR).setCellRenderer(statusRenderer);
        table.getColumnModel().getColumn(COL_ARTIST).setCellRenderer(statusRenderer);
        if (!assistantMode) {
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
        }

        JScrollPane tableScroll = new JScrollPane(table);
        JPanel tablePanel = new JPanel(new BorderLayout(0, 4));
        if (assistantMode) {
            JPanel banners = new JPanel();
            banners.setLayout(new BoxLayout(banners, BoxLayout.Y_AXIS));
            bannerCurrent = newBannerLabel("Current: —", STATUS_NOW);
            bannerNext = newBannerLabel("Next: —", STATUS_NEXT);
            banners.add(bannerCurrent);
            banners.add(Box.createVerticalStrut(4));
            banners.add(bannerNext);
            banners.add(Box.createVerticalStrut(4));
            tablePanel.add(banners, BorderLayout.NORTH);
        }
        tablePanel.add(tableScroll, BorderLayout.CENTER);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        tablePanel.add(statusLabel, BorderLayout.SOUTH);

        JSplitPane topSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, tablePanel);
        topSplitPane.setResizeWeight(0.28);
        topSplitPane.setContinuousLayout(true);
        topSplit = topSplitPane;

        playersInner.setLayout(new BoxLayout(playersInner, BoxLayout.Y_AXIS));
        JScrollPane playersScroll = new JScrollPane(playersInner);
        playersScroll.setBorder(BorderFactory.createTitledBorder("Your players"));
        playersScroll.setPreferredSize(new Dimension(180, 200));
        playersScroll.setMinimumSize(new Dimension(140, 120));

        JSplitPane bottomSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, playersScroll, gridPanel);
        bottomSplitPane.setResizeWeight(0.22);
        bottomSplitPane.setContinuousLayout(true);
        bottomSplit = bottomSplitPane;

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplit, bottomSplit);
        mainSplitPane.setResizeWeight(0.45);
        mainSplitPane.setContinuousLayout(true);
        mainSplitPane.setDividerLocation(MAIN_SPLIT_DEFAULT);
        mainSplit = mainSplitPane;

        add(mainSplit, BorderLayout.CENTER);

        if (!assistantMode) {
            loadBtn.addActionListener(e -> loadSet());
            advanceBtn.addActionListener(e -> advance());
            markSetBtn.addActionListener(e -> markSetAsPlayed());
            broadcastCheck.addActionListener(e -> onBroadcastToggled(broadcastCheck.isSelected()));
            copyLinkBtn.addActionListener(e -> copyShareLink());
            leaderReconnectBtn.addActionListener(e -> leaderReconnect());
            relayCombo.addActionListener(e -> {
                if (!relayComboGuard) {
                    onRelayComboChanged();
                }
            });
        } else {
            assistantConnectBtn.addActionListener(e -> assistantConnect());
            assistantDisconnectBtn.addActionListener(e -> assistantDisconnect());
            assistantReconnectBtn.addActionListener(e -> assistantConnect());
            relayCombo.addActionListener(e -> {
                if (!relayComboGuard) {
                    onRelayComboChanged();
                }
            });
        }

        refreshRelayPicker();

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                maybeRestoreSplits();
            }
        });
    }

    private void buildLeaderLeft(JPanel left) {
        setlistNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(setlistNameLabel);
        left.add(Box.createVerticalStrut(8));

        JPanel pickRow = new JPanel(new BorderLayout(6, 0));
        pickRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        pickRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, setlistCombo.getPreferredSize().height));
        pickRow.add(new JLabel("Setlist:"), BorderLayout.WEST);
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

        JPanel relayPick = new JPanel(new BorderLayout(6, 0));
        relayPick.setAlignmentX(Component.LEFT_ALIGNMENT);
        relayPick.setMaximumSize(new Dimension(Integer.MAX_VALUE, relayCombo.getPreferredSize().height));
        relayPick.add(new JLabel("Relay:"), BorderLayout.WEST);
        relayPick.add(relayCombo, BorderLayout.CENTER);
        left.add(relayPick);
        left.add(Box.createVerticalStrut(6));

        JPanel broadcastRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        broadcastRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        broadcastRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        copyLinkBtn.setEnabled(false);
        copyLinkBtn.setToolTipText("Copy the /playback share link for band assistants (browser or app).");
        leaderReconnectBtn.setVisible(false);
        leaderReconnectBtn.setToolTipText(
                "Reconnect to the relay with the same room after a connection drop.");
        broadcastRow.add(broadcastCheck);
        broadcastRow.add(copyLinkBtn);
        broadcastRow.add(leaderReconnectBtn);
        left.add(broadcastRow);

        roomLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        roomLabel.setVerticalAlignment(JLabel.TOP);
        left.add(roomLabel);
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
    }

    private void buildAssistantLeft(JPanel left) {
        JLabel title = new JLabel("Band Assistant");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 2f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(title);
        left.add(Box.createVerticalStrut(8));

        JLabel linkLbl = new JLabel("Share link or code:");
        linkLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(linkLbl);
        left.add(Box.createVerticalStrut(4));

        assistantLinkField = new JTextField();
        assistantLinkField.setAlignmentX(Component.LEFT_ALIGNMENT);
        assistantLinkField.setMaximumSize(new Dimension(Integer.MAX_VALUE, assistantLinkField.getPreferredSize().height));
        assistantLinkField.setToolTipText(
                "Paste the bandleader’s /playback?set=… link, or a bare room code "
                        + "(bare code needs a relay selected below).");
        left.add(assistantLinkField);
        left.add(Box.createVerticalStrut(6));

        JPanel roomRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        roomRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        roomRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        assistantConnectBtn = new JButton("Connect");
        assistantDisconnectBtn = new JButton("Disconnect");
        assistantDisconnectBtn.setEnabled(false);
        assistantReconnectBtn = new JButton("Reconnect");
        assistantReconnectBtn.setEnabled(false);
        assistantReconnectBtn.setToolTipText("Connect again with the same link or code after a drop.");
        roomRow.add(assistantConnectBtn);
        roomRow.add(assistantDisconnectBtn);
        roomRow.add(assistantReconnectBtn);
        left.add(roomRow);
        left.add(Box.createVerticalStrut(6));

        JPanel relayPick = new JPanel(new BorderLayout(6, 0));
        relayPick.setAlignmentX(Component.LEFT_ALIGNMENT);
        relayPick.setMaximumSize(new Dimension(Integer.MAX_VALUE, relayCombo.getPreferredSize().height));
        relayPick.add(new JLabel("Relay (for bare code):"), BorderLayout.WEST);
        relayPick.add(relayCombo, BorderLayout.CENTER);
        left.add(relayPick);
        left.add(Box.createVerticalStrut(8));

        infoLabel.setText("—");
        infoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoLabel.setVerticalAlignment(JLabel.TOP);
        left.add(infoLabel);
        left.add(Box.createVerticalGlue());
    }

    private static JLabel newBannerLabel(String text, Color accent) {
        JLabel label = new JLabel(text);
        label.setOpaque(true);
        Color bg = UIManager.getColor("Panel.background");
        if (bg == null) {
            bg = new Color(0x2A_2A_2A);
        }
        label.setBackground(bg);
        label.setForeground(accent);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    public void setPreferences(Preferences preferences) {
        this.preferences = preferences;
        splitsRestored = false;
        refreshRelayPicker();
        if (isShowing()) {
            maybeRestoreSplits();
        }
    }

    public void setPreferencesSaver(Runnable savePrefs) {
        this.preferencesSaver = savePrefs;
    }

    public void persistUiState(Preferences preferences) {
        if (preferences == null || mainSplit == null || topSplit == null || bottomSplit == null) {
            return;
        }
        Map<String, Object> state = new LinkedHashMap<>();
        putSplitSizes(state, "main", mainSplit, MAIN_SPLIT_MIN, SECONDARY_PANE_MIN, true);
        putSplitSizes(state, "top", topSplit, TOP_SPLIT_MIN, SECONDARY_PANE_MIN, false);
        putSplitSizes(state, "bottom", bottomSplit, BOTTOM_SPLIT_MIN, SECONDARY_PANE_MIN, false);
        if (!state.isEmpty()) {
            preferences.extras().put(SPLIT_PREF_KEY, state);
        }
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
        if (assistantMode) {
            return;
        }
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

    /** Reload relay combo from preferences (leader or assistant bare-code fallback). */
    public void refreshRelayPicker() {
        relayComboGuard = true;
        try {
            relayCombo.removeAllItems();
            List<Map<String, Object>> relays =
                    preferences == null ? List.of() : preferences.setPlayRelays();
            String selected = preferences == null ? null : preferences.setPlaySelectedRelayId();
            if (relays == null || relays.isEmpty()) {
                relayCombo.addItem(new RelayItem("", "(add a relay in Settings → Set Playback)"));
            } else {
                RelayItem selectItem = null;
                for (Map<String, Object> relayMap : relays) {
                    if (relayMap == null) {
                        continue;
                    }
                    Object idObj = relayMap.get("id");
                    Object nameObj = relayMap.get("name");
                    String id = idObj == null ? "" : String.valueOf(idObj);
                    String name = nameObj == null || String.valueOf(nameObj).isBlank()
                            ? id
                            : String.valueOf(nameObj);
                    RelayItem item = new RelayItem(id, name);
                    relayCombo.addItem(item);
                    if (selected != null && selected.equals(id)) {
                        selectItem = item;
                    }
                }
                if (selectItem != null) {
                    relayCombo.setSelectedItem(selectItem);
                }
            }
        } finally {
            relayComboGuard = false;
        }
    }

    public void onShown() {
        refreshSetlistPicker();
        refreshRelayPicker();
        maybeRestoreSplits();
        SwingUtilities.invokeLater(gridPanel::fitCardsToView);
    }

    /** Close the relay WebSocket (call when disposing the window/panel). */
    public void shutdown() {
        broadcastGeneration++;
        relay.close();
    }

    private void onRelayComboChanged() {
        if (preferences == null) {
            return;
        }
        RelayItem item = (RelayItem) relayCombo.getSelectedItem();
        String id = item == null || item.id.isBlank() ? null : item.id;
        preferences.setSetPlaySelectedRelayId(id);
        if (preferencesSaver != null) {
            preferencesSaver.run();
        }
    }

    private void updateLeaderReconnectVisibility() {
        if (assistantMode) {
            return;
        }
        boolean vis = broadcastCheck.isSelected()
                && relayCode != null
                && !relayCode.isBlank()
                && relayLeaderToken != null
                && !relayLeaderToken.isBlank();
        leaderReconnectBtn.setVisible(vis);
        leaderReconnectBtn.setEnabled(vis && !relay.isOpen());
    }

    private void leaderReconnect() {
        if (assistantMode || relayCode == null || relayLeaderToken == null) {
            return;
        }
        String base = preferences == null ? "" : preferences.activeSetPlayRelayUrl();
        if (base == null || base.isBlank()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Choose a relay in Settings → Set Playback.",
                    "Relay",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        relay.close();
        relay.openLeader(base, relayCode, relayLeaderToken);
        statusLabel.setText("Reconnecting…");
        updateLeaderReconnectVisibility();
    }

    private void onBroadcastToggled(boolean on) {
        if (assistantMode) {
            return;
        }
        if (!on) {
            broadcastGeneration++;
            relay.close();
            relayCode = null;
            relayLeaderToken = null;
            relayShareUrl = null;
            roomLabel.setText("");
            copyLinkBtn.setEnabled(false);
            updateLeaderReconnectVisibility();
            return;
        }
        String base = preferences == null ? "" : preferences.activeSetPlayRelayUrl();
        if (base == null || base.isBlank()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Add a relay in Settings → Set Playback (use wss:// from your Worker).",
                    "Relay",
                    JOptionPane.WARNING_MESSAGE);
            broadcastCheck.setSelected(false);
            return;
        }
        final int gen = ++broadcastGeneration;
        final String baseUrl = base;
        statusLabel.setText("Creating relay room…");
        Thread t = new Thread(() -> {
            try {
                SetPlayRelayHttp.RoomCredentials creds = relayHttp.createRelayRoom(baseUrl);
                SwingUtilities.invokeLater(() -> {
                    if (gen != broadcastGeneration || !broadcastCheck.isSelected()) {
                        return;
                    }
                    relayCode = creds.roomCode();
                    relayLeaderToken = creds.leaderToken();
                    String share = SetPlayShareUrls.buildPlaybackShareUrl(baseUrl, relayCode);
                    relayShareUrl = share;
                    roomLabel.setText(
                            "<html>Share: <a href=\"" + share + "\">" + share + "</a><br/>Code: <b>"
                                    + relayCode + "</b></html>");
                    copyLinkBtn.setEnabled(true);
                    relay.openLeader(baseUrl, relayCode, relayLeaderToken);
                    updateLeaderReconnectVisibility();
                    statusLabel.setText("Opening relay…");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    if (gen != broadcastGeneration) {
                        return;
                    }
                    JOptionPane.showMessageDialog(
                            this,
                            "Could not create room: " + (ex.getMessage() == null ? ex : ex.getMessage()),
                            "Relay",
                            JOptionPane.WARNING_MESSAGE);
                    broadcastCheck.setSelected(false);
                    roomLabel.setText("");
                    copyLinkBtn.setEnabled(false);
                    updateLeaderReconnectVisibility();
                    statusLabel.setText(" ");
                });
            }
        }, "set-play-create-room");
        t.setDaemon(true);
        t.start();
    }

    private void copyShareLink() {
        String text = relayShareUrl != null && !relayShareUrl.isBlank() ? relayShareUrl : relayCode;
        if (text == null || text.isBlank()) {
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        statusLabel.setText("Share link copied.");
    }

    private void assistantConnect() {
        if (!assistantMode || assistantLinkField == null) {
            return;
        }
        String raw = assistantLinkField.getText() == null ? "" : assistantLinkField.getText().strip();
        String fallback = preferences == null ? "" : preferences.activeSetPlayRelayUrl();
        Optional<SetPlayShareUrls.ParsedShareLink> parsed =
                SetPlayShareUrls.parseShareOrCode(raw, fallback);
        if (parsed.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Paste the bandleader’s share link (…/playback?set=CODE), "
                            + "or enter a room code and select a relay under Settings → Set Playback.",
                    "Relay",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        SetPlayShareUrls.ParsedShareLink link = parsed.get();
        relayCode = link.roomCode();
        if (raw.toLowerCase(Locale.ROOT).contains("://")) {
            assistantLinkField.setText(
                    SetPlayShareUrls.buildPlaybackShareUrl(link.relayWsUrl(), link.roomCode()));
        } else {
            assistantLinkField.setText(link.roomCode());
        }
        relay.close();
        relay.openAssistant(link.relayWsUrl(), link.roomCode());
        assistantDisconnectBtn.setEnabled(true);
        assistantReconnectBtn.setEnabled(true);
        statusLabel.setText("Connecting…");
    }

    private void assistantDisconnect() {
        if (!assistantMode) {
            return;
        }
        relay.close();
        if (assistantDisconnectBtn != null) {
            assistantDisconnectBtn.setEnabled(false);
        }
        if (assistantReconnectBtn != null && assistantLinkField != null) {
            String raw = assistantLinkField.getText() == null ? "" : assistantLinkField.getText().strip();
            assistantReconnectBtn.setEnabled(raw.length() >= 5);
        }
        statusLabel.setText("Disconnected.");
    }

    private void onRelayConnected() {
        statusLabel.setText("Relay connected.");
        pushRelayIfLeader();
        updateLeaderReconnectVisibility();
        if (assistantMode && assistantReconnectBtn != null) {
            assistantReconnectBtn.setEnabled(true);
        }
    }

    private void onRelayDisconnected() {
        if (!assistantMode) {
            statusLabel.setText("Relay disconnected.");
            updateLeaderReconnectVisibility();
        } else {
            if (assistantDisconnectBtn != null) {
                assistantDisconnectBtn.setEnabled(false);
            }
            if (assistantReconnectBtn != null && assistantLinkField != null) {
                String raw = assistantLinkField.getText() == null ? "" : assistantLinkField.getText().strip();
                assistantReconnectBtn.setEnabled(raw.length() >= 5);
            }
        }
    }

    private void onRelayError(String message) {
        statusLabel.setText("Relay: " + (message == null ? "error" : message));
    }

    private void onRelayState(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        if (!assistantMode) {
            int revision = toInt(data.get("revision"), 0);
            if (revision <= lastPushedRevision) {
                return;
            }
        }
        if (!SetPlaySync.STATE_TYPE.equals(String.valueOf(data.get("type")))) {
            return;
        }
        applyRemoteSnapshot(data);
    }

    private void applyRemoteSnapshot(Map<String, Object> data) {
        SetPlaySync.AppliedSnapshot applied = SetPlaySync.applySnapshot(data);
        session = applied.session();
        Map<String, Object> meta = applied.setMeta();
        long setlistId = toLong(data.get("setlist_id"), 0L);

        songRows.clear();
        for (Map<String, Object> rd : applied.rows()) {
            if (rd == null) {
                continue;
            }
            long itemId = toLong(rd.get("item_id"), 0L);
            long songId = toLong(rd.get("song_id"), 0L);
            int position = toInt(rd.get("position"), 0);
            int partCount = toInt(rd.get("part_count"), 0);
            Integer duration = toIntegerOrNull(rd.get("duration_seconds"));
            String title = rd.get("title") == null ? "" : String.valueOf(rd.get("title"));
            String artist = rd.get("artist") == null ? "—" : String.valueOf(rd.get("artist"));
            songRows.add(new SetlistItemInfo(
                    itemId,
                    setlistId,
                    songId,
                    title,
                    artist,
                    duration,
                    partCount,
                    null,
                    position,
                    null,
                    null));
        }

        layoutCards = List.copyOf(applied.layoutCards());

        String name = meta.get("name") == null ? "Set" : String.valueOf(meta.get("name"));
        Long bandLayoutId = toLongOrNull(meta.get("band_layout_id"));
        Integer defaultChange = toIntegerOrNull(meta.get("default_change_duration_seconds"));
        Integer target = toIntegerOrNull(meta.get("target_duration_seconds"));
        String notes = meta.get("notes") == null ? null : String.valueOf(meta.get("notes"));
        String setDate = meta.get("set_date") == null ? null : String.valueOf(meta.get("set_date"));
        String setTime = meta.get("set_time") == null ? null : String.valueOf(meta.get("set_time"));
        loadedSetlist = new SetlistInfo(
                setlistId,
                name,
                bandLayoutId,
                null,
                0,
                false,
                defaultChange,
                notes,
                setDate,
                setTime,
                target);

        if (assistantMode) {
            StringBuilder sb = new StringBuilder("<html>");
            sb.append("<b>").append(escapeHtml(name)).append("</b>");
            sb.append("<br>Date: ").append(setDate == null || setDate.isBlank() ? "—" : escapeHtml(setDate));
            sb.append(" &nbsp; Time: ").append(setTime == null || setTime.isBlank() ? "—" : escapeHtml(setTime));
            String notesText = notes == null ? "" : notes.strip();
            sb.append("<br>Notes: ").append(notesText.isEmpty() ? "—" : escapeHtml(notesText));
            Integer tw = toIntegerOrNull(meta.get("computed_duration_seconds"));
            if (tw != null) {
                sb.append("<br>Duration (incl. switches): ")
                        .append(LibraryDisplayFormats.formatHoursMinutesSeconds(tw));
            }
            sb.append("</html>");
            infoLabel.setText(sb.toString());
        } else {
            setlistNameLabel.setText(name);
        }

        statusLabel.setText("Synced (rev " + session.revision() + ").");
        refreshAll();
        refreshSongBanners();
        SwingUtilities.invokeLater(gridPanel::fitCardsToView);
    }

    private void pushRelayIfLeader() {
        if (assistantMode || !relay.isOpen() || loadedSetlist == null) {
            return;
        }
        Map<String, Object> payload = SetPlaySync.snapshotFromLeader(
                session,
                loadedSetlist,
                songRows,
                computedDurationSeconds(),
                layoutCards);
        lastPushedRevision = session.revision();
        relay.sendSnapshot(payload);
    }

    private Integer computedDurationSeconds() {
        if (loadedSetlist == null || songRows.isEmpty()) {
            return null;
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
        return totalSec + switchSec;
    }

    private void maybeRestoreSplits() {
        if (splitsRestored || mainSplit == null) {
            return;
        }
        if (mainSplit.getHeight() <= 0 || topSplit.getWidth() <= 0 || bottomSplit.getWidth() <= 0) {
            return;
        }
        restoreSplits();
        splitsRestored = true;
    }

    private void restoreSplits() {
        Map<String, Object> saved = asStringKeyedMap(
                preferences == null ? null : preferences.extras().get(SPLIT_PREF_KEY));
        applySplitDivider(mainSplit, saved == null ? null : saved.get("main"), MAIN_SPLIT_DEFAULT, MAIN_SPLIT_MIN, true);
        applySplitDivider(topSplit, saved == null ? null : saved.get("top"), -1, TOP_SPLIT_MIN, false);
        applySplitDivider(bottomSplit, saved == null ? null : saved.get("bottom"), -1, BOTTOM_SPLIT_MIN, false);
    }

    private static void putSplitSizes(
            Map<String, Object> state,
            String key,
            JSplitPane split,
            int firstMin,
            int secondMin,
            boolean vertical) {
        int first = split.getDividerLocation();
        int total = vertical ? split.getHeight() : split.getWidth();
        int second = Math.max(0, total - first - split.getDividerSize());
        if (total > 0 && first >= firstMin && second >= secondMin) {
            state.put(key, List.of(first, second));
        }
    }

    private static void applySplitDivider(
            JSplitPane split, Object raw, int defaultDivider, int firstMin, boolean vertical) {
        List<Integer> sizes = asIntegerList(raw);
        int divider = defaultDivider;
        if (sizes != null && !sizes.isEmpty() && sizes.get(0) != null && sizes.get(0) >= firstMin) {
            divider = sizes.get(0);
            int total = vertical ? split.getHeight() : split.getWidth();
            int max = Math.max(firstMin, total - split.getDividerSize() - SECONDARY_PANE_MIN);
            divider = Math.min(divider, max);
        }
        if (divider >= firstMin) {
            split.setDividerLocation(divider);
        }
    }

    private static Map<String, Object> asStringKeyedMap(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                out.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return out.isEmpty() ? null : out;
    }

    private static List<Integer> asIntegerList(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<Integer> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Number number) {
                out.add(number.intValue());
            } else if (item instanceof String text) {
                try {
                    out.add(Integer.parseInt(text.trim()));
                } catch (NumberFormatException ignored) {
                    out.add(null);
                }
            } else {
                out.add(null);
            }
        }
        return out;
    }

    private Long selectedSetlistId() {
        return setlistCombo.getSelectedSetlistId();
    }

    private void loadSet() {
        if (assistantMode || setlistRepository == null) {
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
            pushRelayIfLeader();
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
        refreshSongBanners();
        refreshPlayers();
        refreshGrid();
        if (!assistantMode) {
            advanceBtn.setEnabled(loadedSetlist != null && !songRows.isEmpty());
            markSetBtn.setEnabled(loadedSetlist != null && !songRows.isEmpty());
        }
    }

    private void afterStateChange() {
        checkboxGuard = true;
        tableModel.fireTableDataChanged();
        checkboxGuard = false;
        refreshInfo();
        refreshSongBanners();
        refreshGrid();
        pushRelayIfLeader();
    }

    private void refreshSongBanners() {
        if (bannerCurrent == null || bannerNext == null) {
            return;
        }
        bannerCurrent.setText(bannerLine(session.currentItemId(), "Current"));
        bannerNext.setText(bannerLine(session.nextItemId(), "Next"));
    }

    private String bannerLine(Long itemId, String label) {
        if (itemId == null) {
            return label + ": —";
        }
        SetlistItemInfo row = rowForItem(itemId);
        if (row == null) {
            return label + ": —";
        }
        String meta = LibraryDisplayFormats.formatDuration(row.songDurationSeconds());
        String artist = row.songComposers() == null ? "" : row.songComposers().strip();
        String extra = !artist.isEmpty() && !"—".equals(artist) ? " · " + artist : "";
        String title = row.songTitle() == null ? "" : row.songTitle();
        return "<html>" + label + ": <b>" + escapeHtml(title) + "</b> (" + escapeHtml(meta) + escapeHtml(extra)
                + ")</html>";
    }

    private void refreshInfo() {
        if (assistantMode) {
            return;
        }
        if (loadedSetlist == null) {
            infoLabel.setText("Select a setlist and click Load set.");
            return;
        }
        Integer withSwitches = computedDurationSeconds();
        int n = songRows.size();
        int durationSec = withSwitches == null ? 0 : withSwitches;
        StringBuilder sb = new StringBuilder("<html>");
        sb.append(n).append(" song").append(n == 1 ? "" : "s");
        sb.append(" · ").append(LibraryDisplayFormats.formatHoursMinutesSeconds(durationSec));
        if (loadedSetlist.targetDurationSeconds() != null && loadedSetlist.targetDurationSeconds() > 0) {
            int rem = loadedSetlist.targetDurationSeconds() - durationSec;
            sb.append(" · target ")
                    .append(LibraryDisplayFormats.formatHoursMinutesSeconds(
                            loadedSetlist.targetDurationSeconds()));
            sb.append(" (").append(rem >= 0 ? "+" : "")
                    .append(LibraryDisplayFormats.formatHoursMinutesSeconds(Math.abs(rem)));
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
        if (assistantMode) {
            gridPanel.setCards(layoutCards);
            gridPanel.setHighlightPlayerIds(highlightPlayers);
            return;
        }
        if (layoutBuilder == null
                || loadedSetlist == null
                || loadedSetlist.bandLayoutId() == null) {
            layoutCards = List.of();
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
            layoutCards = layoutBuilder.build(
                    loadedSetlist.bandLayoutId(),
                    nextRow,
                    curRow,
                    rightRow,
                    songRows);
            gridPanel.setCards(layoutCards);
            gridPanel.setHighlightPlayerIds(highlightPlayers);
        } catch (LibraryException ex) {
            layoutCards = List.of();
            gridPanel.clear();
            statusLabel.setText(ex.getMessage() == null ? "Failed to refresh layout." : ex.getMessage());
        }
    }

    private void refreshPlayers() {
        playersInner.removeAll();
        Map<Long, String> players = new LinkedHashMap<>();
        if (assistantMode) {
            for (SetPlayLayoutCard card : layoutCards) {
                players.put(card.playerId(), card.playerName());
            }
        } else if (loadedSetlist != null
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
        if (assistantMode) {
            return;
        }
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
        if (assistantMode || loadedSetlist == null || playLogRepository == null) {
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
        if (assistantMode || !e.isPopupTrigger()) {
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

    private void hideColumn(int col) {
        TableColumn column = table.getColumnModel().getColumn(col);
        column.setMinWidth(0);
        column.setMaxWidth(0);
        column.setPreferredWidth(0);
        column.setResizable(false);
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

    private static String escapeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static int toInt(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static long toLong(Object value, long fallback) {
        Long n = toLongOrNull(value);
        return n == null ? fallback : n;
    }

    private static Long toLongOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Integer toIntegerOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private enum RowStyle {
        SKIP,
        NOW,
        NEXT,
        PLAYED,
        NORMAL
    }

    private static final class RelayItem {
        final String id;
        final String name;

        RelayItem(String id, String name) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
        }

        @Override
        public String toString() {
            return name;
        }
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
            if (assistantMode) {
                return false;
            }
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
            if (assistantMode
                    || checkboxGuard
                    || columnIndex != COL_SKIP
                    || rowIndex < 0
                    || rowIndex >= songRows.size()) {
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

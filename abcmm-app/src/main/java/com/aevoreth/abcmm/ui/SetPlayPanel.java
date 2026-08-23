package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.border.Border;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableColumn;

import com.aevoreth.abcmm.domain.band.BandLayoutSlotInfo;
import com.aevoreth.abcmm.domain.band.BandRepository;
import com.aevoreth.abcmm.domain.band.PlayerRepository;
import com.aevoreth.abcmm.domain.band.SongLayoutRepository;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.library.PlayLogRepository;
import com.aevoreth.abcmm.domain.library.SongRepository;
import com.aevoreth.abcmm.domain.prefs.LotroPaths;
import com.aevoreth.abcmm.domain.prefs.Preferences;
import com.aevoreth.abcmm.domain.setlist.SetlistInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistItemInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistRepository;
import com.aevoreth.abcmm.domain.setplay.SetPlayExpiry;
import com.aevoreth.abcmm.domain.setplay.SetPlayLayoutBuilder;
import com.aevoreth.abcmm.domain.setplay.SetPlayLayoutCard;
import com.aevoreth.abcmm.domain.setplay.SetPlayPartsSheet;
import com.aevoreth.abcmm.domain.setplay.SetPlayPartsSheetBuilder;
import com.aevoreth.abcmm.domain.setplay.SetPlayPublishedSessionInfo;
import com.aevoreth.abcmm.domain.setplay.SetPlayRelayInfo;
import com.aevoreth.abcmm.domain.setplay.SetPlayRelayRepository;
import com.aevoreth.abcmm.domain.setplay.SetPlaySessionRules;
import com.aevoreth.abcmm.domain.setplay.SetPlaySessionState;
import com.aevoreth.abcmm.domain.setplay.SetPlayZipNames;
import com.aevoreth.abcmm.domain.setplay.relay.SetPlayRelayClient;
import com.aevoreth.abcmm.domain.setplay.relay.SetPlayRelayHttp;
import com.aevoreth.abcmm.domain.setplay.relay.SetPlayShareUrls;
import com.aevoreth.abcmm.domain.setplay.relay.SetPlaySync;

/**
 * Set Play leader (solo rehearsal or named relay sessions) or Band Assistant
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
    private static final int COL_LAYOUT = 6;
    private static final int COL_ACTIONS = 7;

    private static final int PARTS_COL_STATUS = 0;
    private static final int PARTS_COL_TITLE = 1;
    private static final int PARTS_COL_DUR = 2;
    private static final int PARTS_COL_COUNT = 3;
    private static final int PARTS_COL_FIRST_PLAYER = 4;

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
    private PlayerRepository playerRepository;
    private PlayLogRepository playLogRepository;
    private SetPlayLayoutBuilder layoutBuilder;
    private SetPlayRelayRepository setPlayRelayRepository;
    private SongRepository songRepository;
    private Preferences preferences;
    private Runnable preferencesSaver;

    private SetPlaySessionState session = new SetPlaySessionState(List.of());
    private final List<SetlistItemInfo> songRows = new ArrayList<>();
    private SetlistInfo loadedSetlist;
    private List<SetPlayLayoutCard> layoutCards = List.of();
    private Map<Long, List<SetPlayLayoutCard>> layoutCardsByItemId = Map.of();
    private final Set<Long> highlightPlayers = new HashSet<>();
    private boolean checkboxGuard;
    private boolean splitsRestored;
    private boolean relayComboGuard;

    private final SetPlayRelayClient relay;
    private final SetPlayRelayHttp relayHttp = new SetPlayRelayHttp();
    private String relayCode;
    private String sessionPassphrase;
    private String sessionName;
    private String sessionDate;
    private String sessionTime;
    private String relayShareUrl;
    private boolean zipAvailable;
    private boolean hostingFromSnapshot;
    private List<SetPlayRelayHttp.SessionSummary> remoteSessions = List.of();
    private SetPlayPartsSheet partsSheet = SetPlayPartsSheet.empty();
    private final JTabbedPane innerTabs = new JTabbedPane();
    private final DefaultTableModel sessionListModel =
            new DefaultTableModel(new Object[] {"Name", "Code", "Zip", "PIN", "Expires"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
    private final JTable sessionList = new JTable(sessionListModel);
    private final JButton createSessionBtn = new JButton("Create session");
    private final JButton reconnectSessionBtn = new JButton("Reconnect");
    private final JButton renameSessionBtn = new JButton("Rename");
    private final JButton republishBtn = new JButton("Republish");
    private final JButton uploadZipBtn = new JButton("Upload zip");
    private final JButton clearSessionBtn = new JButton("Clear session");
    private final JButton deleteSessionBtn = new JButton("Delete session");
    private final JButton copyPlayOnlyBtn = new JButton("Copy Play Only");
    private final JButton copyDownloadBtn = new JButton("Copy Download and Play");
    private int lastPushedRevision = -1;
    private int broadcastGeneration;
    private final JLabel setlistNameLabel = new JLabel("—");
    private final SetlistPickerCombo setlistCombo = new SetlistPickerCombo();
    private final JButton loadBtn = new JButton("Load set");
    private final JComboBox<RelayItem> relayCombo = new JComboBox<>();
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
    private JTextField assistantPinField;
    private JButton assistantConnectBtn;
    private JButton assistantDisconnectBtn;
    private JButton assistantReconnectBtn;
    private JButton downloadZipBtn;
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
            public void onClosed(int code, String reason) {
                SwingUtilities.invokeLater(() -> onRelayClosed(code, reason));
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
        table.getColumnModel().getColumn(COL_LAYOUT).setCellRenderer(new LayoutPreviewRenderer());
        if (!assistantMode) {
            table.getColumnModel().getColumn(COL_ACTIONS).setCellRenderer(new ActionsRenderer());
            table.getColumnModel().getColumn(COL_ACTIONS).setCellEditor(new ActionsEditor());
        }
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row < 0 || row >= songRows.size()) {
                    return;
                }
                if (col == COL_LAYOUT && e.getClickCount() == 1) {
                    showLayoutPreview(songRows.get(row).id());
                    return;
                }
                if (!assistantMode
                        && e.getClickCount() == 2
                        && col != COL_LAYOUT
                        && col != COL_ACTIONS) {
                    actionSetNext(songRows.get(row).id());
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
        playersScroll.setToolTipText("Same as Players on the Parts tab — highlight the layout and Parts column accents.");
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

        add(innerTabs, BorderLayout.CENTER);
        innerTabs.addTab(assistantMode ? "Connect" : "Sessions",
                assistantMode ? buildAssistantConnectPanel() : buildSessionsPanel());
        innerTabs.addTab("Playback", mainSplit);
        innerTabs.addTab("Parts", buildPartsPanel());
        innerTabs.addChangeListener(e -> onInnerTabChanged());

        if (!assistantMode) {
            loadBtn.addActionListener(e -> loadSet());
            advanceBtn.addActionListener(e -> advance());
            markSetBtn.addActionListener(e -> markSetAsPlayed());
            createSessionBtn.addActionListener(e -> createSession());
            reconnectSessionBtn.addActionListener(e -> reconnectSelectedSession());
            renameSessionBtn.addActionListener(e -> renameSelectedSession());
            republishBtn.addActionListener(e -> republishSession());
            uploadZipBtn.addActionListener(e -> uploadZip());
            clearSessionBtn.addActionListener(e -> clearRemoteSession());
            deleteSessionBtn.addActionListener(e -> deleteRemoteSession());
            copyPlayOnlyBtn.addActionListener(e -> copyShareLink(false));
            copyDownloadBtn.addActionListener(e -> copyShareLink(true));
            relayCombo.addActionListener(e -> {
                if (!relayComboGuard) {
                    onRelayComboChanged();
                    refreshRemoteSessions();
                }
            });
        } else {
            assistantConnectBtn.addActionListener(e -> assistantConnect());
            assistantDisconnectBtn.addActionListener(e -> assistantDisconnect());
            assistantReconnectBtn.addActionListener(e -> assistantConnect());
            downloadZipBtn.addActionListener(e -> openDownloadDialog());
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
        setlistNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(setlistNameLabel);
        left.add(Box.createVerticalStrut(8));
        infoLabel.setText("—");
        infoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoLabel.setVerticalAlignment(JLabel.TOP);
        left.add(infoLabel);
        left.add(Box.createVerticalGlue());
    }

    private JPanel buildSessionsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        sizeToChars(setlistCombo, 30);
        sizeToChars(relayCombo, 30);

        JPanel pickRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        pickRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        pickRow.add(new JLabel("Setlist:"));
        pickRow.add(setlistCombo);
        pickRow.add(loadBtn);
        capRowHeight(pickRow);
        panel.add(pickRow);
        panel.add(Box.createVerticalStrut(8));

        JPanel relayPick = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        relayPick.setAlignmentX(Component.LEFT_ALIGNMENT);
        relayPick.add(new JLabel("Relay:"));
        relayPick.add(relayCombo);
        relayPick.add(createSessionBtn);
        capRowHeight(relayPick);
        panel.add(relayPick);
        panel.add(Box.createVerticalStrut(8));

        sessionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sessionList.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        sessionList.getTableHeader().setReorderingAllowed(false);
        sizeSessionColumns();
        JScrollPane listScroll = new JScrollPane(sessionList);
        listScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        int tableW = sessionTableWidth();
        listScroll.setPreferredSize(new Dimension(tableW + 24, 180));
        listScroll.setMaximumSize(new Dimension(tableW + 24, Integer.MAX_VALUE));
        panel.add(listScroll);
        panel.add(Box.createVerticalStrut(6));

        reconnectSessionBtn.setToolTipText("Connect to the selected session as bandleader.");
        renameSessionBtn.setToolTipText("Change the name. The code and links stay the same.");
        republishBtn.setToolTipText(
                "Replace the hosted set with the one you have loaded. Resets NOW/NEXT and removes the zip.");
        uploadZipBtn.setToolTipText("Attach a zip of the set files so players can download it (2 MB max).");
        clearSessionBtn.setToolTipText("Clear NOW, NEXT, played, and skip. Keeps the session.");
        deleteSessionBtn.setToolTipText("Delete this session for everyone. Cannot be undone.");
        copyPlayOnlyBtn.setToolTipText("Copy a watch-only link. No download PIN.");
        copyDownloadBtn.setToolTipText("Copy a link that includes the zip PIN so players can download files.");

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btns.setAlignmentX(Component.LEFT_ALIGNMENT);
        btns.add(reconnectSessionBtn);
        btns.add(renameSessionBtn);
        btns.add(republishBtn);
        btns.add(uploadZipBtn);
        btns.add(clearSessionBtn);
        btns.add(deleteSessionBtn);
        panel.add(btns);
        JPanel copyRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        copyRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        copyPlayOnlyBtn.setEnabled(false);
        copyDownloadBtn.setEnabled(false);
        copyRow.add(copyPlayOnlyBtn);
        copyRow.add(copyDownloadBtn);
        panel.add(copyRow);
        roomLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(roomLabel);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private void sizeToChars(Component component, int chars) {
        int rowH = Math.max(22, component.getPreferredSize().height);
        int width = charsToPx(component, chars);
        Dimension d = new Dimension(width, rowH);
        component.setPreferredSize(d);
        component.setMinimumSize(new Dimension(Math.min(80, width), rowH));
        component.setMaximumSize(d);
    }

    private void capRowHeight(JPanel row) {
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height + 4));
    }

    private int charsToPx(Component component, int chars) {
        int em = component.getFontMetrics(component.getFont()).charWidth('M');
        return Math.max(24, em * chars + 12);
    }

    private void sizeSessionColumns() {
        int[] widths = {
                charsToPx(sessionList, 30),
                charsToPx(sessionList, 9),
                charsToPx(sessionList, 5),
                charsToPx(sessionList, 8),
                charsToPx(sessionList, 25)
        };
        for (int i = 0; i < widths.length && i < sessionList.getColumnCount(); i++) {
            TableColumn col = sessionList.getColumnModel().getColumn(i);
            col.setMinWidth(widths[i]);
            col.setPreferredWidth(widths[i]);
            col.setMaxWidth(widths[i]);
        }
    }

    private int sessionTableWidth() {
        int w = 0;
        for (int i = 0; i < sessionList.getColumnCount(); i++) {
            w += sessionList.getColumnModel().getColumn(i).getPreferredWidth();
        }
        return Math.max(w, 200);
    }

    private JPanel buildAssistantConnectPanel() {
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JLabel title = new JLabel("Band Assistant");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 2f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(title);
        left.add(Box.createVerticalStrut(8));
        JLabel linkLbl = new JLabel("Share link or code:");
        linkLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(linkLbl);
        assistantLinkField = new JTextField();
        assistantLinkField.setAlignmentX(Component.LEFT_ALIGNMENT);
        assistantLinkField.setMaximumSize(new Dimension(Integer.MAX_VALUE, assistantLinkField.getPreferredSize().height));
        left.add(assistantLinkField);
        left.add(Box.createVerticalStrut(6));
        JLabel pinLbl = new JLabel("Download PIN (optional):");
        pinLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(pinLbl);
        assistantPinField = new JTextField();
        assistantPinField.setAlignmentX(Component.LEFT_ALIGNMENT);
        assistantPinField.setMaximumSize(new Dimension(Integer.MAX_VALUE, assistantPinField.getPreferredSize().height));
        assistantPinField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateDownloadButton();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateDownloadButton();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateDownloadButton();
            }
        });
        left.add(assistantPinField);
        left.add(Box.createVerticalStrut(6));
        JPanel roomRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        roomRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        assistantConnectBtn = new JButton("Connect");
        assistantDisconnectBtn = new JButton("Disconnect");
        assistantDisconnectBtn.setEnabled(false);
        assistantReconnectBtn = new JButton("Reconnect");
        assistantReconnectBtn.setEnabled(false);
        downloadZipBtn = new JButton("Download ZIP");
        downloadZipBtn.setEnabled(false);
        roomRow.add(assistantConnectBtn);
        roomRow.add(assistantDisconnectBtn);
        roomRow.add(assistantReconnectBtn);
        roomRow.add(downloadZipBtn);
        left.add(roomRow);
        left.add(Box.createVerticalStrut(6));
        JPanel relayPick = new JPanel(new BorderLayout(6, 0));
        relayPick.setAlignmentX(Component.LEFT_ALIGNMENT);
        relayPick.setMaximumSize(new Dimension(Integer.MAX_VALUE, relayCombo.getPreferredSize().height));
        relayPick.add(new JLabel("Relay (for bare code):"), BorderLayout.WEST);
        relayPick.add(relayCombo, BorderLayout.CENTER);
        left.add(relayPick);
        left.add(Box.createVerticalGlue());
        return left;
    }

    private final PartsTableModel partsTableModel = new PartsTableModel();
    private final JTable partsTable = new JTable(partsTableModel);
    private JCheckBox partsShowSelectedOnly;
    private JButton partsPlayersBtn;

    private JPanel buildPartsPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        partsShowSelectedOnly = new JCheckBox("Selected players only");
        partsShowSelectedOnly.addActionListener(e -> refreshPartsTable());
        JButton instruments = new JButton("Instruments needed…");
        instruments.addActionListener(e -> showInstrumentsNeeded());
        partsPlayersBtn = new JButton("Players: All");
        partsPlayersBtn.setToolTipText("Same as Your players on Playback — highlight on the grid and Parts columns.");
        partsPlayersBtn.addActionListener(e -> showPartsPlayersMenu());
        north.add(partsShowSelectedOnly);
        north.add(instruments);
        north.add(partsPlayersBtn);
        if (!assistantMode) {
            JButton adv = new JButton("Advance song");
            adv.addActionListener(e -> advance());
            north.add(adv);
        }
        panel.add(north, BorderLayout.NORTH);
        partsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        partsTable.setFillsViewportHeight(true);
        if (!assistantMode) {
            partsTable.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                        int row = partsTable.rowAtPoint(e.getPoint());
                        if (row >= 0 && row < songRows.size()) {
                            actionSetNext(songRows.get(row).id());
                        }
                    }
                }
            });
        }
        panel.add(new JScrollPane(partsTable), BorderLayout.CENTER);
        return panel;
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
            PlayLogRepository playLogRepository,
            SetPlayRelayRepository setPlayRelayRepository,
            SongRepository songRepository) {
        this.setlistRepository = setlistRepository;
        this.bandRepository = bandRepository;
        this.playerRepository = playerRepository;
        this.playLogRepository = playLogRepository;
        this.setPlayRelayRepository = setPlayRelayRepository;
        this.songRepository = songRepository;
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
        refreshRelayPicker();
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
            List<SetPlayRelayInfo> relays = List.of();
            if (setPlayRelayRepository != null) {
                try {
                    relays = setPlayRelayRepository.listRelays();
                } catch (LibraryException ignored) {
                    relays = List.of();
                }
            }
            String selected = preferences == null ? null : preferences.setPlaySelectedRelayId();
            if (relays.isEmpty()) {
                relayCombo.addItem(new RelayItem("", "(add a relay in Settings → Set Playback)"));
            } else {
                RelayItem selectItem = null;
                for (SetPlayRelayInfo relayInfo : relays) {
                    RelayItem item = new RelayItem(String.valueOf(relayInfo.id()), relayInfo.name());
                    relayCombo.addItem(item);
                    if (selected != null && selected.equals(String.valueOf(relayInfo.id()))) {
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
        if (!assistantMode) {
            refreshRemoteSessions();
        }
        maybeRestoreSplits();
        SwingUtilities.invokeLater(gridPanel::fitCardsToView);
    }

    /** Close the relay WebSocket (call when disposing the window/panel). */
    public void shutdown() {
        broadcastGeneration++;
        relay.close();
    }

    private SetPlayRelayInfo selectedRelayOrNull() {
        if (setPlayRelayRepository == null) {
            return null;
        }
        RelayItem item = (RelayItem) relayCombo.getSelectedItem();
        if (item == null || item.id.isBlank()) {
            return null;
        }
        try {
            return setPlayRelayRepository.findRelay(Long.parseLong(item.id)).orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private String activeRelayUrl() {
        SetPlayRelayInfo selected = selectedRelayOrNull();
        if (selected != null) {
            return selected.normalizedUrl();
        }
        if (setPlayRelayRepository != null) {
            try {
                List<SetPlayRelayInfo> relays = setPlayRelayRepository.listRelays();
                if (!relays.isEmpty()) {
                    return relays.get(0).normalizedUrl();
                }
            } catch (LibraryException ignored) {
                // fall through
            }
        }
        return "";
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

    private SetPlayRelayInfo requireOwnerRelay() {
        SetPlayRelayInfo relayInfo = selectedRelayOrNull();
        if (relayInfo == null || relayInfo.normalizedUrl().isBlank()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Add a relay in Settings → Set Playback (use the deploy wizard).",
                    "Relay",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }
        if (!relayInfo.hasToken()) {
            JOptionPane.showMessageDialog(
                    this,
                    "This relay has no token. Edit it in Settings → Set Playback and paste the relay token, "
                            + "or redeploy the worker to issue a new one.",
                    "Relay token",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return relayInfo;
    }

    private String selectedRemoteCode() {
        int row = sessionList.getSelectedRow();
        if (row < 0 || row >= remoteSessions.size()) {
            return relayCode;
        }
        return remoteSessions.get(row).code();
    }

    private void refreshRemoteSessions() {
        if (assistantMode) {
            return;
        }
        SetPlayRelayInfo relayInfo = selectedRelayOrNull();
        if (relayInfo == null || !relayInfo.hasToken()) {
            remoteSessions = List.of();
            sessionListModel.setRowCount(0);
            updateCopyButtons();
            return;
        }
        final SetPlayRelayInfo info = relayInfo;
        Thread t = new Thread(() -> {
            try {
                List<SetPlayRelayHttp.SessionSummary> list =
                        relayHttp.listSessions(info.normalizedUrl(), info.token());
                SwingUtilities.invokeLater(() -> {
                    remoteSessions = list;
                    Map<String, String> pins = localPinsByCode(info.id());
                    sessionListModel.setRowCount(0);
                    for (SetPlayRelayHttp.SessionSummary s : list) {
                        String pin = pins.getOrDefault(s.code() == null ? "" : s.code().toUpperCase(Locale.ROOT), "—");
                        String expires = s.expiresAt() == null || s.expiresAt().isBlank() ? "—" : s.expiresAt();
                        if (expires.length() > 25) {
                            expires = expires.substring(0, 25);
                        }
                        sessionListModel.addRow(new Object[] {
                                s.name() == null ? "" : s.name(),
                                s.code(),
                                s.zipAvailable() ? "Yes" : "No",
                                pin,
                                expires
                        });
                    }
                    sizeSessionColumns();
                    if (relayCode != null) {
                        for (int i = 0; i < list.size(); i++) {
                            if (relayCode.equalsIgnoreCase(list.get(i).code())) {
                                sessionList.setRowSelectionInterval(i, i);
                                zipAvailable = list.get(i).zipAvailable();
                                break;
                            }
                        }
                    }
                    updateCopyButtons();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        statusLabel.setText("Could not list sessions: "
                                + (ex.getMessage() == null ? ex : ex.getMessage())));
            }
        }, "set-play-list-sessions");
        t.setDaemon(true);
        t.start();
    }

    private Map<String, String> localPinsByCode(long relayId) {
        Map<String, String> pins = new LinkedHashMap<>();
        if (setPlayRelayRepository == null) {
            return pins;
        }
        try {
            for (SetPlayPublishedSessionInfo local : setPlayRelayRepository.listPublishedSessions(relayId)) {
                if (local.code() != null && local.passphrase() != null && !local.passphrase().isBlank()) {
                    pins.put(local.code().toUpperCase(Locale.ROOT), local.passphrase());
                }
            }
        } catch (LibraryException ignored) {
            // PIN column stays blank when the local copy is missing
        }
        return pins;
    }

    private Optional<SessionMetaPrompt> promptSessionMeta(String defaultName, String defaultDate, String defaultTime) {
        JTextField nameField = new JTextField(defaultName == null ? "" : defaultName, 28);
        JTextField dateField = new JTextField(defaultDate == null ? "" : defaultDate, 12);
        JTextField timeField = new JTextField(defaultTime == null ? "" : defaultTime, 8);
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(new JLabel("Session name"));
        form.add(nameField);
        form.add(Box.createVerticalStrut(8));
        form.add(new JLabel("Set date (YYYY-MM-DD) — session metadata only"));
        form.add(dateField);
        form.add(Box.createVerticalStrut(8));
        form.add(new JLabel("Set time (HH:MM, America/New_York)"));
        form.add(timeField);
        int ok = JOptionPane.showConfirmDialog(
                this, form, "Session", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) {
            return Optional.empty();
        }
        String name = nameField.getText() == null ? "" : nameField.getText().strip();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name is required.", "Session", JOptionPane.WARNING_MESSAGE);
            return Optional.empty();
        }
        LocalDate date = parseDateOrNull(dateField.getText());
        LocalTime time = parseTimeOrNull(timeField.getText());
        if (date == null) {
            date = LocalDate.now(SetPlayExpiry.ZONE).plusDays(7);
        }
        if (time == null) {
            time = LocalTime.of(19, 0);
        }
        return Optional.of(new SessionMetaPrompt(name, date, time));
    }

    private record SessionMetaPrompt(String name, LocalDate date, LocalTime time) {
    }

    private static LocalDate parseDateOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.strip(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static LocalTime parseTimeOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.strip();
        try {
            return LocalTime.parse(t, DateTimeFormatter.ofPattern("H:mm"));
        } catch (DateTimeParseException ex) {
            try {
                return LocalTime.parse(t, DateTimeFormatter.ofPattern("HH:mm"));
            } catch (DateTimeParseException ex2) {
                return null;
            }
        }
    }

    private boolean warnDuplicateName(String name, String exceptCode) {
        boolean dup = false;
        for (SetPlayRelayHttp.SessionSummary s : remoteSessions) {
            if (exceptCode != null && exceptCode.equalsIgnoreCase(s.code())) {
                continue;
            }
            if (name.equalsIgnoreCase(s.name() == null ? "" : s.name())) {
                dup = true;
                break;
            }
        }
        if (!dup) {
            return true;
        }
        int ok = JOptionPane.showConfirmDialog(
                this,
                "Another session on this relay already uses that name. Continue anyway?",
                "Duplicate name",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return ok == JOptionPane.YES_OPTION;
    }

    private void createSession() {
        if (assistantMode) {
            return;
        }
        if (loadedSetlist == null || songRows.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Load a set first. Create session publishes the currently loaded setlist.",
                    "Create session",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        SetPlayRelayInfo relayInfo = requireOwnerRelay();
        if (relayInfo == null) {
            return;
        }
        String prefillDate = loadedSetlist.setDate();
        String prefillTime = loadedSetlist.setTime();
        if (prefillDate == null || prefillDate.isBlank()) {
            prefillDate = LocalDate.now(SetPlayExpiry.ZONE).plusDays(7).toString();
        }
        if (prefillTime == null || prefillTime.isBlank()) {
            prefillTime = "19:00";
        }
        Optional<SessionMetaPrompt> meta = promptSessionMeta(loadedSetlist.name(), prefillDate, prefillTime);
        if (meta.isEmpty()) {
            return;
        }
        SessionMetaPrompt prompt = meta.get();
        if (!warnDuplicateName(prompt.name(), null)) {
            return;
        }
        rebuildPartsSheetFromLocal();
        hostingFromSnapshot = false;
        Map<String, Object> state = currentSnapshotPayload(prompt.name());
        final SetPlayRelayInfo info = relayInfo;
        statusLabel.setText("Creating session…");
        Thread t = new Thread(() -> {
            try {
                SetPlayRelayHttp.CreateResult created = relayHttp.createSession(
                        info.normalizedUrl(),
                        info.token(),
                        prompt.name(),
                        loadedSetlist.id(),
                        loadedSetlist.name(),
                        loadedSetlist.notes(),
                        prompt.date().toString(),
                        prompt.time().toString().substring(0, 5),
                        state);
                if (setPlayRelayRepository != null) {
                    setPlayRelayRepository.upsertPublishedSession(
                            info.id(),
                            created.roomCode(),
                            created.name(),
                            created.passphrase(),
                            loadedSetlist.id());
                }
                SwingUtilities.invokeLater(() -> {
                    relayCode = created.roomCode();
                    sessionPassphrase = created.passphrase();
                    sessionName = created.name();
                    sessionDate = prompt.date().toString();
                    sessionTime = prompt.time().toString().substring(0, 5);
                    lastPushedRevision = session.revision();
                    updateShareLabels(info.normalizedUrl());
                    relay.openLeader(info.normalizedUrl(), relayCode, info.token());
                    refreshRemoteSessions();
                    statusLabel.setText("Session created. PIN shown once in Download and Play link.");
                    JOptionPane.showMessageDialog(
                            this,
                            "Session code: " + created.roomCode()
                                    + "\nDownload PIN: " + created.passphrase()
                                    + "\n\nCopy Download and Play now — the PIN cannot be recovered later.",
                            "Session created",
                            JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        this,
                        "Could not create session: " + (ex.getMessage() == null ? ex : ex.getMessage()),
                        "Relay",
                        JOptionPane.WARNING_MESSAGE));
            }
        }, "set-play-create-session");
        t.setDaemon(true);
        t.start();
    }

    private void reconnectSelectedSession() {
        if (assistantMode) {
            return;
        }
        SetPlayRelayInfo relayInfo = requireOwnerRelay();
        if (relayInfo == null) {
            return;
        }
        String code = selectedRemoteCode();
        if (code == null || code.isBlank()) {
            JOptionPane.showMessageDialog(this, "Select a session.", "Reconnect", JOptionPane.WARNING_MESSAGE);
            return;
        }
        SetPlayRelayHttp.SessionSummary summary = findRemote(code);
        if (summary != null) {
            sessionName = summary.name();
            sessionDate = summary.setDate();
            sessionTime = summary.setTime();
            zipAvailable = summary.zipAvailable();
            if (loadedSetlist == null || loadedSetlist.id() != (summary.setlistId() == null ? 0L : summary.setlistId())) {
                hostingFromSnapshot = true;
                JOptionPane.showMessageDialog(
                        this,
                        "The local setlist is missing or different from this session.\n"
                                + "Hosting from the relay snapshot. Advance still works.\n"
                                + "Play history is skipped for songs that are not in this library.",
                        "Snapshot only",
                        JOptionPane.WARNING_MESSAGE);
            }
        }
        if (setPlayRelayRepository != null) {
            try {
                setPlayRelayRepository.findPublishedSession(relayInfo.id(), code).ifPresent(local -> {
                    sessionPassphrase = local.passphrase();
                    if (sessionName == null || sessionName.isBlank()) {
                        sessionName = local.name();
                    }
                });
            } catch (LibraryException ignored) {
                // local PIN is optional
            }
        }
        lastPushedRevision = -1;
        relayCode = code;
        updateShareLabels(relayInfo.normalizedUrl());
        relay.close();
        relay.openLeader(relayInfo.normalizedUrl(), code, relayInfo.token());
        statusLabel.setText("Reconnecting…");
        updateCopyButtons();
    }

    private void renameSelectedSession() {
        if (assistantMode) {
            return;
        }
        SetPlayRelayInfo relayInfo = requireOwnerRelay();
        if (relayInfo == null) {
            return;
        }
        String code = selectedRemoteCode();
        if (code == null || code.isBlank()) {
            JOptionPane.showMessageDialog(this, "Select a session.", "Rename", JOptionPane.WARNING_MESSAGE);
            return;
        }
        SetPlayRelayHttp.SessionSummary summary = findRemote(code);
        String current = summary == null || summary.name() == null ? "" : summary.name();
        String next = JOptionPane.showInputDialog(this, "New session name:", current);
        if (next == null) {
            return;
        }
        next = next.strip();
        if (next.isEmpty()) {
            return;
        }
        if (!warnDuplicateName(next, code)) {
            return;
        }
        final String name = next;
        final SetPlayRelayInfo info = relayInfo;
        Thread t = new Thread(() -> {
            try {
                relayHttp.renameSession(info.normalizedUrl(), info.token(), code, name);
                if (setPlayRelayRepository != null) {
                    setPlayRelayRepository.updatePublishedSessionName(info.id(), code, name);
                }
                SwingUtilities.invokeLater(() -> {
                    if (code.equalsIgnoreCase(relayCode)) {
                        sessionName = name;
                        updateShareLabels(info.normalizedUrl());
                    }
                    refreshRemoteSessions();
                    if (relay.isOpen() && code.equalsIgnoreCase(relayCode)) {
                        pushRelayIfLeader();
                    }
                    statusLabel.setText("Renamed session.");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        this,
                        "Rename failed: " + (ex.getMessage() == null ? ex : ex.getMessage()),
                        "Relay",
                        JOptionPane.WARNING_MESSAGE));
            }
        }, "set-play-rename");
        t.setDaemon(true);
        t.start();
    }

    private void republishSession() {
        if (assistantMode) {
            return;
        }
        if (loadedSetlist == null || songRows.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this, "Load a set first, then Republish to replace the hosted song list.",
                    "Republish", JOptionPane.WARNING_MESSAGE);
            return;
        }
        SetPlayRelayInfo relayInfo = requireOwnerRelay();
        if (relayInfo == null) {
            return;
        }
        String code = selectedRemoteCode();
        if (code == null || code.isBlank()) {
            JOptionPane.showMessageDialog(this, "Select a session.", "Republish", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int ok = JOptionPane.showConfirmDialog(
                this,
                "Replace the hosted set with the currently loaded setlist, reset NOW/NEXT/played/skip, "
                        + "and delete the attached zip?",
                "Republish",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) {
            return;
        }
        session = new SetPlaySessionState(session.orderItemIds());
        List<Long> order = new ArrayList<>();
        for (SetlistItemInfo row : songRows) {
            order.add(row.id());
        }
        session = new SetPlaySessionState(order);
        hostingFromSnapshot = false;
        zipAvailable = false;
        rebuildPartsSheetFromLocal();
        SetPlayRelayHttp.SessionSummary summary = findRemote(code);
        String date = summary != null && summary.setDate() != null ? summary.setDate() : sessionDate;
        String time = summary != null && summary.setTime() != null ? summary.setTime() : sessionTime;
        Map<String, Object> state = currentSnapshotPayload(sessionName);
        final SetPlayRelayInfo info = relayInfo;
        Thread t = new Thread(() -> {
            try {
                relayHttp.republishSession(
                        info.normalizedUrl(),
                        info.token(),
                        code,
                        loadedSetlist.id(),
                        loadedSetlist.name(),
                        loadedSetlist.notes(),
                        date,
                        time,
                        state);
                SwingUtilities.invokeLater(() -> {
                    lastPushedRevision = session.revision();
                    relayCode = code;
                    updateShareLabels(info.normalizedUrl());
                    if (!relay.isOpen()) {
                        relay.openLeader(info.normalizedUrl(), code, info.token());
                    } else {
                        pushRelayIfLeader();
                    }
                    refreshAll();
                    refreshRemoteSessions();
                    statusLabel.setText("Republished. Zip removed.");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        this,
                        "Republish failed: " + (ex.getMessage() == null ? ex : ex.getMessage()),
                        "Relay",
                        JOptionPane.WARNING_MESSAGE));
            }
        }, "set-play-republish");
        t.setDaemon(true);
        t.start();
    }

    private void uploadZip() {
        if (assistantMode) {
            return;
        }
        SetPlayRelayInfo relayInfo = requireOwnerRelay();
        if (relayInfo == null) {
            return;
        }
        String code = selectedRemoteCode();
        if (code == null || code.isBlank()) {
            JOptionPane.showMessageDialog(this, "Select a session.", "Upload zip", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Zip archives", "zip"));
        LotroPaths.resolveSetExportDirectory(preferences)
                .ifPresent(dir -> chooser.setCurrentDirectory(dir.toFile()));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION
                || chooser.getSelectedFile() == null) {
            return;
        }
        Path file = chooser.getSelectedFile().toPath();
        LocalDate date = parseDateOrNull(sessionDate);
        LocalTime time = parseTimeOrNull(sessionTime);
        SetPlayRelayHttp.SessionSummary summary = findRemote(code);
        if (date == null && summary != null) {
            date = parseDateOrNull(summary.setDate());
            time = parseTimeOrNull(summary.setTime());
        }
        String expires = SetPlayExpiry.expiresAtIso(date, time, relayInfo.retentionDays());
        final SetPlayRelayInfo info = relayInfo;
        Thread t = new Thread(() -> {
            try {
                byte[] bytes = Files.readAllBytes(file);
                if (bytes.length > SetPlayRelayHttp.MAX_ZIP_BYTES) {
                    throw new java.io.IOException("Zip is larger than 2 MB.");
                }
                relayHttp.uploadZip(info.normalizedUrl(), info.token(), code, bytes, expires);
                SwingUtilities.invokeLater(() -> {
                    zipAvailable = true;
                    refreshRemoteSessions();
                    if (relay.isOpen() && code.equalsIgnoreCase(relayCode)) {
                        pushRelayIfLeader();
                    }
                    statusLabel.setText("Zip uploaded.");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        this,
                        "Upload failed: " + (ex.getMessage() == null ? ex : ex.getMessage()),
                        "Relay",
                        JOptionPane.WARNING_MESSAGE));
            }
        }, "set-play-upload-zip");
        t.setDaemon(true);
        t.start();
    }

    private void clearRemoteSession() {
        if (assistantMode) {
            return;
        }
        SetPlayRelayInfo relayInfo = requireOwnerRelay();
        if (relayInfo == null) {
            return;
        }
        String code = selectedRemoteCode();
        if (code == null || code.isBlank()) {
            JOptionPane.showMessageDialog(this, "Select a session.", "Clear session", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int ok = JOptionPane.showConfirmDialog(
                this,
                "Reset NOW, NEXT, played, and skip flags on the hosted session?",
                "Clear session",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) {
            return;
        }
        final SetPlayRelayInfo info = relayInfo;
        Thread t = new Thread(() -> {
            try {
                relayHttp.clearSession(info.normalizedUrl(), info.token(), code);
                SwingUtilities.invokeLater(() -> {
                    if (code.equalsIgnoreCase(relayCode)) {
                        session.playedItemIds().clear();
                        session.skippedItemIds().clear();
                        session.setCurrentItemId(null);
                        session.setNextItemId(null);
                        session.bumpRevision();
                        recoverLocalHostIfPossible();
                        lastPushedRevision = session.revision();
                        refreshAll();
                        pushRelayIfLeader();
                    }
                    statusLabel.setText("Session cleared.");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        this,
                        "Clear failed: " + (ex.getMessage() == null ? ex : ex.getMessage()),
                        "Relay",
                        JOptionPane.WARNING_MESSAGE));
            }
        }, "set-play-clear");
        t.setDaemon(true);
        t.start();
    }

    private void deleteRemoteSession() {
        if (assistantMode) {
            return;
        }
        SetPlayRelayInfo relayInfo = requireOwnerRelay();
        if (relayInfo == null) {
            return;
        }
        String code = selectedRemoteCode();
        if (code == null || code.isBlank()) {
            JOptionPane.showMessageDialog(this, "Select a session.", "Delete session", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int ok = JOptionPane.showConfirmDialog(
                this,
                "Delete this session, its zip, and disconnect everyone? This cannot be undone.",
                "Delete session",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) {
            return;
        }
        final SetPlayRelayInfo info = relayInfo;
        Thread t = new Thread(() -> {
            try {
                relayHttp.deleteSession(info.normalizedUrl(), info.token(), code);
                if (setPlayRelayRepository != null) {
                    setPlayRelayRepository.deletePublishedSession(info.id(), code);
                }
                SwingUtilities.invokeLater(() -> {
                    if (code.equalsIgnoreCase(relayCode)) {
                        broadcastGeneration++;
                        relay.close();
                        relayCode = null;
                        sessionPassphrase = null;
                        sessionName = null;
                        roomLabel.setText("");
                        zipAvailable = false;
                    }
                    refreshRemoteSessions();
                    updateCopyButtons();
                    statusLabel.setText("Session deleted.");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        this,
                        "Delete failed: " + (ex.getMessage() == null ? ex : ex.getMessage()),
                        "Relay",
                        JOptionPane.WARNING_MESSAGE));
            }
        }, "set-play-delete");
        t.setDaemon(true);
        t.start();
    }

    private void copyShareLink(boolean downloadAndPlay) {
        SetPlayRelayInfo relayInfo = selectedRelayOrNull();
        String base = relayInfo == null ? activeRelayUrl() : relayInfo.normalizedUrl();
        String code = selectedRemoteCode();
        if (code == null || code.isBlank()) {
            return;
        }
        String text;
        if (downloadAndPlay) {
            String pin = sessionPassphrase;
            if ((pin == null || pin.isBlank()) && relayInfo != null && setPlayRelayRepository != null) {
                try {
                    pin = setPlayRelayRepository.findPublishedSession(relayInfo.id(), code)
                            .map(SetPlayPublishedSessionInfo::passphrase)
                            .orElse(null);
                } catch (LibraryException ignored) {
                    pin = null;
                }
            }
            if (pin == null || pin.isBlank()) {
                JOptionPane.showMessageDialog(
                        this,
                        "The download PIN is only stored on this PC after Create session. "
                                + "It cannot be recovered from the relay.",
                        "Download and Play",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            text = SetPlayShareUrls.buildDownloadShareUrl(base, code, pin);
        } else {
            text = SetPlayShareUrls.buildPlaybackShareUrl(base, code);
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        statusLabel.setText(downloadAndPlay ? "Download and Play link copied." : "Play Only link copied.");
    }

    private void updateShareLabels(String baseUrl) {
        if (relayCode == null) {
            roomLabel.setText("");
            updateCopyButtons();
            return;
        }
        String play = SetPlayShareUrls.buildPlaybackShareUrl(baseUrl, relayCode);
        relayShareUrl = play;
        roomLabel.setText("<html>Play Only: " + escapeHtml(play)
                + "<br/>Code: <b>" + escapeHtml(relayCode) + "</b></html>");
        updateCopyButtons();
    }

    private void updateCopyButtons() {
        boolean hasCode = selectedRemoteCode() != null && !selectedRemoteCode().isBlank();
        copyPlayOnlyBtn.setEnabled(hasCode);
        boolean hasPin = sessionPassphrase != null && !sessionPassphrase.isBlank();
        if (!hasPin && setPlayRelayRepository != null) {
            SetPlayRelayInfo info = selectedRelayOrNull();
            String code = selectedRemoteCode();
            if (info != null && code != null) {
                try {
                    hasPin = setPlayRelayRepository.findPublishedSession(info.id(), code)
                            .map(s -> s.passphrase() != null && !s.passphrase().isBlank())
                            .orElse(false);
                } catch (LibraryException ignored) {
                    hasPin = false;
                }
            }
        }
        copyDownloadBtn.setEnabled(hasCode && hasPin);
    }

    private SetPlayRelayHttp.SessionSummary findRemote(String code) {
        if (code == null) {
            return null;
        }
        for (SetPlayRelayHttp.SessionSummary s : remoteSessions) {
            if (code.equalsIgnoreCase(s.code())) {
                return s;
            }
        }
        return null;
    }

    private void assistantConnect() {
        if (!assistantMode || assistantLinkField == null) {
            return;
        }
        String raw = assistantLinkField.getText() == null ? "" : assistantLinkField.getText().strip();
        String fallback = activeRelayUrl();
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
        if (link.passphrase() != null && !link.passphrase().isBlank() && assistantPinField != null) {
            assistantPinField.setText(link.passphrase());
        }
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
        updateDownloadButton();
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
        if (assistantMode && assistantReconnectBtn != null) {
            assistantReconnectBtn.setEnabled(true);
        }
    }

    private void onRelayDisconnected() {
        if (!assistantMode) {
            statusLabel.setText("Relay disconnected.");
        } else {
            if (assistantDisconnectBtn != null) {
                assistantDisconnectBtn.setEnabled(false);
            }
            if (assistantReconnectBtn != null && assistantLinkField != null) {
                String raw = assistantLinkField.getText() == null ? "" : assistantLinkField.getText().strip();
                assistantReconnectBtn.setEnabled(raw.length() >= 5);
            }
            updateDownloadButton();
        }
    }

    private void onRelayClosed(int code, String reason) {
        String why = reason == null ? "" : reason.strip();
        if (why.toLowerCase(Locale.ROOT).contains("session ended")) {
            statusLabel.setText("Session ended.");
            JOptionPane.showMessageDialog(
                    this,
                    "This session was ended by the bandleader.",
                    "Session ended",
                    JOptionPane.INFORMATION_MESSAGE);
            if (assistantMode) {
                if (assistantDisconnectBtn != null) {
                    assistantDisconnectBtn.setEnabled(false);
                }
                if (downloadZipBtn != null) {
                    downloadZipBtn.setEnabled(false);
                }
            }
        } else {
            onRelayDisconnected();
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
        long setlistId = toLong(data.get("setlist_id"), 0L);
        session = applied.session();
        zipAvailable = applied.zipAvailable();
        if (applied.sessionName() != null && !applied.sessionName().isBlank()) {
            sessionName = applied.sessionName();
        }

        boolean recovered = false;
        if (!assistantMode && adoptLocalRowsForSnapshot(setlistId, applied.rows())) {
            recovered = hostingFromSnapshot;
            hostingFromSnapshot = false;
            rebuildPartsSheetFromLocal();
            if (loadedSetlist != null) {
                setlistNameLabel.setText(loadedSetlist.name());
            }
            rememberAppliedRevision();
            statusLabel.setText("Synced (rev " + session.revision() + ").");
            refreshAll();
            refreshSongBanners();
            updateDownloadButton();
            SwingUtilities.invokeLater(gridPanel::fitCardsToView);
            if (recovered && relay.isOpen()) {
                session.bumpRevision();
                pushRelayIfLeader();
            }
            return;
        }

        if (!assistantMode && !hostingFromSnapshot) {
            hostingFromSnapshot = true;
        }

        hydrateFromSnapshot(applied, setlistId);
        rememberAppliedRevision();
        if (!assistantMode && hostingFromSnapshot) {
            statusLabel.setText("Hosting from relay snapshot (rev " + session.revision() + ").");
        } else {
            statusLabel.setText("Synced (rev " + session.revision() + ").");
        }
        refreshAll();
        refreshSongBanners();
        updateDownloadButton();
        SwingUtilities.invokeLater(gridPanel::fitCardsToView);
    }

    private void hydrateFromSnapshot(SetPlaySync.AppliedSnapshot applied, long setlistId) {
        Map<String, Object> meta = applied.setMeta();
        partsSheet = applied.partsSheet() == null ? SetPlayPartsSheet.empty() : applied.partsSheet();

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

        layoutCards = SetPlaySync.layoutCardsForFocus(
                SetPlaySessionRules.layoutFocusItemId(session),
                applied.layoutCardsByItemId(),
                applied.layoutCards());
        Map<Long, List<SetPlayLayoutCard>> byItem = applied.layoutCardsByItemId();
        layoutCardsByItemId = byItem == null ? Map.of() : Map.copyOf(byItem);

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
    }

    /**
     * Prefer complete library rows over snapshot stubs when this setlist is in the
     * local DB and item ids still match the hosted session.
     */
    private boolean adoptLocalRowsForSnapshot(long setlistId, List<Map<String, Object>> snapshotRows) {
        if (assistantMode || setlistId <= 0) {
            return false;
        }
        if (loadedSetlist != null
                && loadedSetlist.id() == setlistId
                && SetPlaySync.canHostFromLocal(songRows, snapshotRows)) {
            return true;
        }
        return reloadSongRowsFromDb(setlistId)
                && SetPlaySync.canHostFromLocal(songRows, snapshotRows);
    }

    private boolean recoverLocalHostIfPossible() {
        if (assistantMode || loadedSetlist == null) {
            return false;
        }
        if (!reloadSongRowsFromDb(loadedSetlist.id())) {
            return false;
        }
        Set<Long> localIds = new HashSet<>();
        for (SetlistItemInfo row : songRows) {
            localIds.add(row.id());
        }
        if (!localIds.equals(new HashSet<>(session.orderItemIds()))) {
            return false;
        }
        hostingFromSnapshot = false;
        rebuildPartsSheetFromLocal();
        return true;
    }

    private boolean reloadSongRowsFromDb(long setlistId) {
        if (setlistRepository == null || setlistId <= 0) {
            return false;
        }
        try {
            SetlistInfo found = null;
            for (SetlistInfo candidate : setlistRepository.listSetlists()) {
                if (candidate.id() == setlistId) {
                    found = candidate;
                    break;
                }
            }
            if (found == null) {
                return false;
            }
            List<SetlistItemInfo> items = setlistRepository.listItems(setlistId);
            if (!SetPlaySync.songRowsHavePartData(items)) {
                return false;
            }
            loadedSetlist = found;
            songRows.clear();
            songRows.addAll(items);
            return true;
        } catch (LibraryException ex) {
            return false;
        }
    }

    private void rememberAppliedRevision() {
        lastPushedRevision = Math.max(lastPushedRevision, session.revision());
    }

    private void onInnerTabChanged() {
        maybeRestoreSplits();
        refreshPartsTable();
        refreshGrid();
        SwingUtilities.invokeLater(gridPanel::fitCardsToView);
    }

    private Map<String, Object> currentSnapshotPayload(String name) {
        return SetPlaySync.snapshotFromLeader(
                session,
                loadedSetlist,
                songRows,
                computedDurationSeconds(),
                layoutCards,
                partsSheet,
                zipAvailable,
                name,
                buildLayoutCardsByItemId());
    }

    private void rebuildPartsSheetFromLocal() {
        if (assistantMode || loadedSetlist == null
                || setlistRepository == null
                || bandRepository == null
                || playerRepository == null) {
            return;
        }
        if (!SetPlaySync.songRowsHavePartData(songRows)) {
            return;
        }
        try {
            partsSheet = SetPlayPartsSheetBuilder.build(
                    loadedSetlist,
                    songRows,
                    setlistRepository,
                    bandRepository,
                    playerRepository,
                    preferences == null ? null : preferences.setExport());
        } catch (LibraryException ex) {
            partsSheet = SetPlayPartsSheet.empty();
        }
    }

    private void pushRelayIfLeader() {
        if (assistantMode || !relay.isOpen() || loadedSetlist == null) {
            return;
        }
        if (!hostingFromSnapshot) {
            rebuildPartsSheetFromLocal();
        }
        lastPushedRevision = session.revision();
        relay.sendSnapshot(currentSnapshotPayload(sessionName));
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
            hostingFromSnapshot = false;
            layoutCardsByItemId = Map.of();
            rebuildPartsSheetFromLocal();
            refreshAll();
            SwingUtilities.invokeLater(gridPanel::fitCardsToView);
            if (relay.isOpen()) {
                JOptionPane.showMessageDialog(
                        this,
                        "The loaded set is local only. Use Republish to replace the hosted song list, "
                                + "reset flags, and delete the zip.",
                        "Load set",
                        JOptionPane.INFORMATION_MESSAGE);
                statusLabel.setText("Loaded locally. Republish to push song-list changes.");
            } else {
                statusLabel.setText("Loaded \"" + found.name() + "\" (" + songRows.size() + " songs).");
            }
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
        refreshPartsTable();
        if (!assistantMode) {
            advanceBtn.setEnabled(loadedSetlist != null && !songRows.isEmpty());
            markSetBtn.setEnabled(loadedSetlist != null && !songRows.isEmpty());
        }
        updateDownloadButton();
    }

    private void afterStateChange() {
        if (!assistantMode && hostingFromSnapshot) {
            recoverLocalHostIfPossible();
        }
        checkboxGuard = true;
        tableModel.fireTableDataChanged();
        checkboxGuard = false;
        refreshInfo();
        refreshSongBanners();
        refreshGrid();
        refreshPartsTable();
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
        if (hostingFromSnapshot) {
            layoutCards = SetPlaySync.layoutCardsForFocus(
                    SetPlaySessionRules.layoutFocusItemId(session),
                    layoutCardsByItemId,
                    layoutCards);
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
            Long focusId = SetPlaySessionRules.layoutFocusItemId(session);
            SetlistItemInfo nextRow = rowForItem(focusId);
            SetlistItemInfo curRow = null;
            Long rightId = null;
            if (session.nextItemId() != null) {
                curRow = rowForItem(session.currentItemId());
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
        List<Map.Entry<Long, String>> sorted = listedPlayers();
        for (Map.Entry<Long, String> entry : sorted) {
            long pid = entry.getKey();
            JCheckBox cb = new JCheckBox(entry.getValue());
            cb.setSelected(highlightPlayers.contains(pid));
            cb.putClientProperty("playerId", pid);
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);
            cb.addActionListener(e -> applyPlayerHighlight(pid, cb.isSelected(), false));
            playersInner.add(cb);
        }
        playersInner.revalidate();
        playersInner.repaint();
        updatePartsPlayersButton();
    }

    private List<Map.Entry<Long, String>> listedPlayers() {
        Map<Long, String> players = new LinkedHashMap<>();
        if (assistantMode) {
            List<SetPlayLayoutCard> source = layoutCards;
            if (source.isEmpty()) {
                for (List<SetPlayLayoutCard> cards : layoutCardsByItemId.values()) {
                    if (!cards.isEmpty()) {
                        source = cards;
                        break;
                    }
                }
            }
            for (SetPlayLayoutCard card : source) {
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
        for (SetPlayPartsSheet.Column col : partsSheet.columns()) {
            if (col.playerId() != null) {
                String title = col.title() == null || col.title().isBlank()
                        ? ("#" + col.playerId())
                        : col.title();
                players.putIfAbsent(col.playerId(), title);
            }
        }
        List<Map.Entry<Long, String>> sorted = new ArrayList<>(players.entrySet());
        sorted.sort(Comparator.comparing(e -> e.getValue().toLowerCase(Locale.ROOT)));
        return sorted;
    }

    private void applyPlayerHighlight(long playerId, boolean selected, boolean syncPlaybackList) {
        if (selected) {
            highlightPlayers.add(playerId);
        } else {
            highlightPlayers.remove(playerId);
        }
        gridPanel.setHighlightPlayerIds(highlightPlayers);
        updatePartsPlayersButton();
        refreshPartsTable();
        if (syncPlaybackList) {
            for (Component c : playersInner.getComponents()) {
                if (c instanceof JCheckBox cb) {
                    Object raw = cb.getClientProperty("playerId");
                    if (raw instanceof Number n) {
                        boolean want = highlightPlayers.contains(n.longValue());
                        if (cb.isSelected() != want) {
                            cb.setSelected(want);
                        }
                    }
                }
            }
        }
    }

    private void updatePartsPlayersButton() {
        if (partsPlayersBtn == null) {
            return;
        }
        int total = 0;
        for (Component c : playersInner.getComponents()) {
            if (c instanceof JCheckBox) {
                total++;
            }
        }
        if (total == 0) {
            Set<Long> ids = new HashSet<>();
            for (SetPlayPartsSheet.Column col : partsSheet.columns()) {
                if (col.playerId() != null) {
                    ids.add(col.playerId());
                }
            }
            total = ids.size();
        }
        int n = highlightPlayers.size();
        partsPlayersBtn.setText(n == 0 || (total > 0 && n == total) ? "Players: All" : "Players: " + n);
    }

    private void showPartsPlayersMenu() {
        List<Map.Entry<Long, String>> players = listedPlayers();
        if (players.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No players yet. Load a set with a band layout.", "Players",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (Map.Entry<Long, String> entry : players) {
            long pid = entry.getKey();
            JCheckBox cb = new JCheckBox(entry.getValue(), highlightPlayers.contains(pid));
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);
            cb.addActionListener(e -> applyPlayerHighlight(pid, cb.isSelected(), true));
            panel.add(cb);
        }
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        scroll.setPreferredSize(new Dimension(220, Math.min(240, 28 * players.size() + 16)));
        JPopupMenu menu = new JPopupMenu();
        menu.setLayout(new BorderLayout());
        menu.add(scroll, BorderLayout.CENTER);
        menu.show(partsPlayersBtn, 0, partsPlayersBtn.getHeight());
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
            if (row != null && songExistsLocally(row.songId())) {
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
                if (!songExistsLocally(row.songId())) {
                    continue;
                }
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
            if (row != null && songExistsLocally(row.songId())) {
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
        if (row == null || !songExistsLocally(row.songId())) {
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

    private void showLayoutPreview(long itemId) {
        SetlistItemInfo row = rowForItem(itemId);
        String songTitle = row == null || row.songTitle() == null || row.songTitle().isBlank()
                ? "Song"
                : row.songTitle();
        List<SetPlayLayoutCard> cards = layoutCardsForItem(itemId);
        if (cards.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "No band layout is available for this song.",
                    "Layout preview",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(
                owner,
                "Layout — " + songTitle,
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        SetPlayBandGridPanel preview = new SetPlayBandGridPanel("");
        preview.setCanvasPreferredSize(720, 420);
        preview.setCards(cards);
        preview.setHighlightPlayerIds(highlightPlayers);
        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(close);
        dialog.getContentPane().add(preview, BorderLayout.CENTER);
        dialog.getContentPane().add(south, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        SwingUtilities.invokeLater(preview::fitCardsToView);
        dialog.setVisible(true);
    }

    private List<SetPlayLayoutCard> layoutCardsForItem(long itemId) {
        if (!assistantMode && !hostingFromSnapshot) {
            SetlistItemInfo row = rowForItem(itemId);
            if (row == null || layoutBuilder == null
                    || loadedSetlist == null
                    || loadedSetlist.bandLayoutId() == null) {
                return List.of();
            }
            try {
                return layoutCardsForSong(row);
            } catch (LibraryException ex) {
                return List.of();
            }
        }
        List<SetPlayLayoutCard> cards = layoutCardsByItemId.get(itemId);
        return cards == null ? List.of() : cards;
    }

    private List<SetPlayLayoutCard> layoutCardsForSong(SetlistItemInfo row) throws LibraryException {
        int idx = session.orderItemIds().indexOf(row.id());
        Long rightId = idx >= 0
                ? SetPlaySessionRules.scanNextItemId(
                        session.orderItemIds(), session.skippedItemIds(), idx)
                : null;
        return layoutBuilder.build(
                loadedSetlist.bandLayoutId(),
                row,
                null,
                rowForItem(rightId),
                songRows);
    }

    private Map<Long, List<SetPlayLayoutCard>> buildLayoutCardsByItemId() {
        if (assistantMode || hostingFromSnapshot) {
            return layoutCardsByItemId;
        }
        if (layoutBuilder == null
                || loadedSetlist == null
                || loadedSetlist.bandLayoutId() == null) {
            return Map.of();
        }
        Map<Long, List<SetPlayLayoutCard>> out = new LinkedHashMap<>();
        for (SetlistItemInfo row : songRows) {
            try {
                out.put(row.id(), layoutCardsForSong(row));
            } catch (LibraryException ex) {
                out.put(row.id(), List.of());
            }
        }
        return out;
    }

    private void sizeColumns() {
        setColWidth(COL_STATUS, 56, 72);
        setColWidth(COL_SKIP, 48, 56);
        setColWidth(COL_PARTS, 48, 64);
        setColWidth(COL_DUR, 64, 80);
        setColWidth(COL_ARTIST, 120, 200);
        setColWidth(COL_LAYOUT, 36, 40);
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
                setForeground(style == RowStyle.NORMAL ? table.getSelectionForeground() : fg);
            } else {
                setBackground(table.getBackground());
                setForeground(fg);
            }
            applyNowNextCellBorder(this, style);
            return c;
        }
    }

    private boolean songExistsLocally(long songId) {
        if (songRepository == null) {
            return false;
        }
        try {
            return songRepository.findSongById(songId).isPresent();
        } catch (LibraryException ex) {
            return false;
        }
    }

    private void updateDownloadButton() {
        if (downloadZipBtn == null) {
            return;
        }
        String pin = assistantPinField == null || assistantPinField.getText() == null
                ? ""
                : assistantPinField.getText().strip();
        downloadZipBtn.setEnabled(zipAvailable && !pin.isEmpty() && relayCode != null && !relayCode.isBlank());
    }

    private void openDownloadDialog() {
        if (!assistantMode || relayCode == null) {
            return;
        }
        String pin = assistantPinField == null || assistantPinField.getText() == null
                ? ""
                : assistantPinField.getText().strip();
        if (pin.isEmpty() || !zipAvailable) {
            return;
        }
        String base = activeRelayUrl();
        if (assistantLinkField != null) {
            Optional<SetPlayShareUrls.ParsedShareLink> parsed =
                    SetPlayShareUrls.parseShareOrCode(assistantLinkField.getText(), base);
            if (parsed.isPresent()) {
                base = parsed.get().relayWsUrl();
            }
        }
        SetPlayDownloadDialog dialog = new SetPlayDownloadDialog(
                SwingUtilities.getWindowAncestor(this),
                relayHttp,
                base,
                relayCode,
                pin,
                zipDownloadBaseName(),
                sessionName,
                preferences,
                songRepository);
        dialog.setVisible(true);
    }

    private List<SetPlayPartsSheet.Column> visiblePartColumns() {
        List<SetPlayPartsSheet.Column> out = new ArrayList<>();
        boolean selectedOnly = partsShowSelectedOnly != null && partsShowSelectedOnly.isSelected();
        for (SetPlayPartsSheet.Column col : partsSheet.columns()) {
            if (selectedOnly
                    && !highlightPlayers.isEmpty()
                    && col.playerId() != null
                    && !highlightPlayers.contains(col.playerId())) {
                continue;
            }
            out.add(col);
        }
        return out;
    }

    private void refreshPartsTable() {
        partsTableModel.fireTableStructureChanged();
        PartsCellRenderer renderer = new PartsCellRenderer();
        PartsHeaderRenderer headerRenderer = new PartsHeaderRenderer();
        for (int i = 0; i < partsTable.getColumnCount(); i++) {
            TableColumn column = partsTable.getColumnModel().getColumn(i);
            column.setCellRenderer(renderer);
            if (i >= PARTS_COL_FIRST_PLAYER) {
                column.setHeaderRenderer(headerRenderer);
            }
        }
        packPartsColumns();
        updatePartsPlayersButton();
    }

    private void packPartsColumns() {
        int n = partsTable.getColumnCount();
        if (n == 0) {
            return;
        }
        packFixedPartsColumn(PARTS_COL_STATUS);
        if (n > PARTS_COL_TITLE) {
            partsTable.getColumnModel().getColumn(PARTS_COL_TITLE).setMinWidth(80);
            partsTable.getColumnModel().getColumn(PARTS_COL_TITLE).setMaxWidth(Integer.MAX_VALUE);
            partsTable.getColumnModel().getColumn(PARTS_COL_TITLE).setPreferredWidth(220);
        }
        if (n > PARTS_COL_DUR) {
            packFixedPartsColumn(PARTS_COL_DUR);
        }
        if (n > PARTS_COL_COUNT) {
            packFixedPartsColumn(PARTS_COL_COUNT);
        }
        for (int i = PARTS_COL_FIRST_PLAYER; i < n; i++) {
            partsTable.getColumnModel().getColumn(i).setPreferredWidth(110);
            partsTable.getColumnModel().getColumn(i).setMinWidth(64);
            partsTable.getColumnModel().getColumn(i).setMaxWidth(Integer.MAX_VALUE);
        }
    }

    private void packFixedPartsColumn(int col) {
        if (col < 0 || col >= partsTable.getColumnCount()) {
            return;
        }
        TableColumn column = partsTable.getColumnModel().getColumn(col);
        int width = 24;
        TableCellRenderer headerRenderer = partsTable.getTableHeader().getDefaultRenderer();
        Component header = headerRenderer.getTableCellRendererComponent(
                partsTable, column.getHeaderValue(), false, false, 0, col);
        width = Math.max(width, header.getPreferredSize().width);
        for (int row = 0; row < partsTable.getRowCount(); row++) {
            TableCellRenderer r = partsTable.getCellRenderer(row, col);
            Component c = r.getTableCellRendererComponent(
                    partsTable, partsTable.getValueAt(row, col), false, false, row, col);
            width = Math.max(width, c.getPreferredSize().width);
        }
        width += 16;
        column.setMinWidth(width);
        column.setPreferredWidth(width);
        column.setMaxWidth(width);
    }

    private List<SetPlayPartsSheet.InstrumentsNeeded> visibleInstrumentsNeeded() {
        List<SetPlayPartsSheet.InstrumentsNeeded> out = new ArrayList<>();
        boolean selectedOnly = partsShowSelectedOnly != null && partsShowSelectedOnly.isSelected();
        for (SetPlayPartsSheet.InstrumentsNeeded n : partsSheet.instrumentsNeeded()) {
            if (selectedOnly && !highlightPlayers.isEmpty() && !highlightPlayers.contains(n.playerId())) {
                continue;
            }
            out.add(n);
        }
        return out;
    }

    private static String instrumentsMarkdown(SetPlayPartsSheet.InstrumentsNeeded needed) {
        StringBuilder sb = new StringBuilder();
        String name = needed.playerName() == null || needed.playerName().isBlank()
                ? "Player"
                : needed.playerName();
        sb.append("**").append(name).append("**\n");
        if (needed.instruments().isEmpty()) {
            sb.append("- (none)\n");
        } else {
            for (String inst : needed.instruments()) {
                sb.append("- ").append(inst).append('\n');
            }
        }
        return sb.toString();
    }

    private static String instrumentsMarkdownAll(List<SetPlayPartsSheet.InstrumentsNeeded> needed) {
        StringBuilder sb = new StringBuilder();
        for (SetPlayPartsSheet.InstrumentsNeeded n : needed) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(instrumentsMarkdown(n));
        }
        return sb.toString();
    }

    private void copyText(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    private void showInstrumentsNeeded() {
        List<SetPlayPartsSheet.InstrumentsNeeded> needed = visibleInstrumentsNeeded();
        if (needed.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No instruments listed.", "Instruments needed",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int cols = Math.min(3, needed.size());
        JPanel grid = new JPanel(new GridLayout(0, cols, 16, 12));
        for (SetPlayPartsSheet.InstrumentsNeeded n : needed) {
            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            JLabel name = new JLabel(n.playerName() == null ? "" : n.playerName());
            name.setFont(name.getFont().deriveFont(Font.BOLD));
            name.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(name);
            StringBuilder bullets = new StringBuilder("<html>");
            if (n.instruments().isEmpty()) {
                bullets.append("• (none)");
            } else {
                for (int i = 0; i < n.instruments().size(); i++) {
                    if (i > 0) {
                        bullets.append("<br>");
                    }
                    bullets.append("• ").append(escapeHtml(n.instruments().get(i)));
                }
            }
            bullets.append("</html>");
            JLabel list = new JLabel(bullets.toString());
            list.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(Box.createVerticalStrut(4));
            card.add(list);
            JButton copy = new JButton("Copy");
            copy.setAlignmentX(Component.LEFT_ALIGNMENT);
            copy.addActionListener(e -> copyText(instrumentsMarkdown(n)));
            card.add(Box.createVerticalStrut(6));
            card.add(copy);
            grid.add(card);
        }
        JPanel root = new JPanel(new BorderLayout(8, 8));
        JButton copyAll = new JButton("Copy all");
        copyAll.addActionListener(e -> copyText(instrumentsMarkdownAll(needed)));
        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        north.add(copyAll);
        root.add(north, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(grid);
        scroll.setPreferredSize(new Dimension(Math.min(720, 240 * cols + 48), 320));
        root.add(scroll, BorderLayout.CENTER);
        JOptionPane.showMessageDialog(this, root, "Instruments needed", JOptionPane.PLAIN_MESSAGE);
    }

    private final class PartsTableModel extends AbstractTableModel {
        @Override
        public int getRowCount() {
            return songRows.size();
        }

        @Override
        public int getColumnCount() {
            return PARTS_COL_FIRST_PLAYER + visiblePartColumns().size();
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case PARTS_COL_STATUS -> "Status";
                case PARTS_COL_TITLE -> "Title";
                case PARTS_COL_DUR -> "Duration";
                case PARTS_COL_COUNT -> "Parts";
                default -> {
                    List<SetPlayPartsSheet.Column> cols = visiblePartColumns();
                    int idx = column - PARTS_COL_FIRST_PLAYER;
                    yield idx >= 0 && idx < cols.size() ? cols.get(idx).title() : "";
                }
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex < 0 || rowIndex >= songRows.size()) {
                return "";
            }
            SetlistItemInfo row = songRows.get(rowIndex);
            return switch (columnIndex) {
                case PARTS_COL_STATUS -> SetPlaySessionRules.statusBadgeText(session, row.id());
                case PARTS_COL_TITLE -> row.songTitle() == null ? "" : row.songTitle();
                case PARTS_COL_DUR -> LibraryDisplayFormats.formatDuration(row.songDurationSeconds());
                case PARTS_COL_COUNT -> row.partCount() > 0 ? String.valueOf(row.partCount()) : "—";
                default -> {
                    List<SetPlayPartsSheet.Column> cols = visiblePartColumns();
                    int idx = columnIndex - PARTS_COL_FIRST_PLAYER;
                    if (idx < 0 || idx >= cols.size()) {
                        yield "";
                    }
                    SetPlayPartsSheet.Column col = cols.get(idx);
                    int sourceIndex = partsSheet.columns().indexOf(col);
                    for (SetPlayPartsSheet.Row sheetRow : partsSheet.rows()) {
                        if (sheetRow.itemId() == row.id()) {
                            if (sourceIndex >= 0 && sourceIndex < sheetRow.cells().size()) {
                                yield sheetRow.cells().get(sourceIndex);
                            }
                            yield "";
                        }
                    }
                    yield "";
                }
            };
        }
    }

    private final class PartsCellRenderer extends DefaultTableCellRenderer {
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
            setFont(font);
            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(style == RowStyle.NORMAL ? table.getSelectionForeground() : fg);
            } else {
                Color bg = table.getBackground();
                if (column >= PARTS_COL_FIRST_PLAYER) {
                    List<SetPlayPartsSheet.Column> cols = visiblePartColumns();
                    int idx = column - PARTS_COL_FIRST_PLAYER;
                    if (idx >= 0 && idx < cols.size()) {
                        bg = playerTint(tintIndexForColumn(cols.get(idx), idx));
                    }
                }
                setBackground(bg);
                setForeground(fg);
            }
            if (column == PARTS_COL_STATUS || column == PARTS_COL_DUR || column == PARTS_COL_COUNT) {
                setHorizontalAlignment(CENTER);
            } else {
                setHorizontalAlignment(LEFT);
            }
            applyPartsCellBorder(this, style, column);
            return c;
        }
    }

    private final class PartsHeaderRenderer implements TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            TableCellRenderer delegate = table.getTableHeader().getDefaultRenderer();
            Component c = delegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (c instanceof JComponent jc) {
                applyPlayerAccentBorder(jc, column, jc.getBorder());
            }
            return c;
        }
    }

    /** Soft column wash: 8 looping hues; dark theme stays muted. */
    private static Color playerTint(int index) {
        float hue = Math.floorMod(index, 8) / 8f;
        if (AbcmmThemer.isDarkMode()) {
            return Color.getHSBColor(hue, 0.12f, 0.22f);
        }
        return Color.getHSBColor(hue, 0.12f, 0.96f);
    }

    /** Left accent: darker in dark mode, lighter in light mode; selected is thicker and more saturated. */
    private static Color playerAccent(int index, boolean selected) {
        float hue = Math.floorMod(index, 8) / 8f;
        if (AbcmmThemer.isDarkMode()) {
            return selected
                    ? Color.getHSBColor(hue, 0.75f, 0.72f)
                    : Color.getHSBColor(hue, 0.40f, 0.16f);
        }
        return selected
                ? Color.getHSBColor(hue, 0.72f, 0.58f)
                : Color.getHSBColor(hue, 0.18f, 0.90f);
    }

    private int tintIndexForColumn(SetPlayPartsSheet.Column col, int visibleIdx) {
        int canonical = 0;
        for (SetPlayPartsSheet.Column all : partsSheet.columns()) {
            if (Objects.equals(all.playerId(), col.playerId()) && all.key().equals(col.key())) {
                return all.playerId() != null ? canonical : visibleIdx;
            }
            if (all.playerId() != null) {
                canonical++;
            }
        }
        return visibleIdx;
    }

    private boolean isPlayerColumnSelected(SetPlayPartsSheet.Column col) {
        return col.playerId() != null && highlightPlayers.contains(col.playerId());
    }

    private void applyPartsCellBorder(JComponent cell, RowStyle style, int column) {
        int pad = 2;
        Color line = switch (style) {
            case NOW -> STATUS_NOW;
            case NEXT -> STATUS_NEXT;
            default -> null;
        };
        Border topBottom = line != null
                ? BorderFactory.createMatteBorder(pad, 0, pad, 0, line)
                : BorderFactory.createEmptyBorder(pad, 0, pad, 0);
        applyPlayerAccentBorder(cell, column, topBottom);
    }

    private void applyPlayerAccentBorder(JComponent cell, int column, Border outer) {
        if (column < PARTS_COL_FIRST_PLAYER) {
            cell.setBorder(outer);
            return;
        }
        List<SetPlayPartsSheet.Column> cols = visiblePartColumns();
        int idx = column - PARTS_COL_FIRST_PLAYER;
        if (idx < 0 || idx >= cols.size()) {
            cell.setBorder(outer);
            return;
        }
        SetPlayPartsSheet.Column col = cols.get(idx);
        int tint = tintIndexForColumn(col, idx);
        boolean selected = isPlayerColumnSelected(col);
        int width = selected ? 5 : 3;
        Border accent = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, width, 0, 0, playerAccent(tint, selected)),
                BorderFactory.createEmptyBorder(0, 4, 0, 0));
        cell.setBorder(outer == null ? accent : BorderFactory.createCompoundBorder(outer, accent));
    }

    private static void applyNowNextCellBorder(javax.swing.JComponent cell, RowStyle style) {
        int pad = 2;
        Color line = switch (style) {
            case NOW -> STATUS_NOW;
            case NEXT -> STATUS_NEXT;
            default -> null;
        };
        if (line != null) {
            cell.setBorder(BorderFactory.createMatteBorder(pad, 0, pad, 0, line));
        } else {
            cell.setBorder(BorderFactory.createEmptyBorder(pad, 0, pad, 0));
        }
    }

    private String zipDownloadBaseName() {
        if (loadedSetlist != null && loadedSetlist.name() != null && !loadedSetlist.name().isBlank()) {
            return loadedSetlist.name();
        }
        String label = setlistNameLabel.getText();
        if (label != null && !label.isBlank() && !"—".equals(label.strip())) {
            return label;
        }
        return relayCode;
    }

    private final class SongTableModel extends AbstractTableModel {
        private final String[] columns = {
                "Status", "Skip", "Title", "Parts", "Duration", "Artist", "", "Actions"
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
                case COL_LAYOUT -> "";
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

    private static final class LayoutPreviewRenderer extends JButton implements TableCellRenderer {
        private static final Icon ICON = new LayoutPreviewIcon(12);

        LayoutPreviewRenderer() {
            setIcon(ICON);
            setText(null);
            setOpaque(true);
            setFocusable(false);
            setToolTipText("Preview layout");
            setMargin(new java.awt.Insets(1, 1, 1, 1));
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

    private static final class LayoutPreviewIcon implements Icon {
        private final int size;

        LayoutPreviewIcon(int size) {
            this.size = size;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color color = c.getForeground() == null ? Color.DARK_GRAY : c.getForeground();
            g2.setColor(color);
            int gap = Math.max(1, size / 6);
            int cell = (size - gap) / 2;
            g2.drawRect(x, y, cell, cell);
            g2.drawRect(x + cell + gap, y, cell, cell);
            g2.drawRect(x, y + cell + gap, cell, cell);
            g2.drawRect(x + cell + gap, y + cell + gap, cell, cell);
            g2.dispose();
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

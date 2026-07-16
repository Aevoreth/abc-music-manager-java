package com.aevoreth.abcmm.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.library.LibraryFilter;
import com.aevoreth.abcmm.domain.library.LibrarySong;
import com.aevoreth.abcmm.domain.library.PlayLogRepository;
import com.aevoreth.abcmm.domain.library.SetlistRef;
import com.aevoreth.abcmm.domain.library.SongAppMetadataUpdate;
import com.aevoreth.abcmm.domain.library.SongRepository;
import com.aevoreth.abcmm.domain.library.StatusInfo;
import com.aevoreth.abcmm.domain.prefs.DefaultFilters;
import com.aevoreth.abcmm.domain.setlist.SetlistInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistRepository;

/**
 * Library table with filters/search matching the Python edition terminology.
 */
public final class LibraryPanel extends JPanel {

    private static final String CARD_TABLE = "table";
    private static final String CARD_EMPTY = "empty";

    private final LibraryTableModel tableModel = new LibraryTableModel();
    private final JTable table = new JTable(tableModel);
    private final CardLayout libraryCards = new CardLayout();
    private final JPanel libraryStack = new JPanel(libraryCards);

    private final JTextField titleComposerField = new JTextField(18);
    private final FilterMultiSelect<StatusInfo> statusFilter =
            new FilterMultiSelect<>(LibraryDisplayFormats::statusFilterLabel);
    private final JComboBox<String> inSetCombo = new JComboBox<>(new String[] {"Either", "Yes", "No"});
    private final JSpinner ratingFrom = new JSpinner(new SpinnerNumberModel(0, 0, 5, 1));
    private final JSpinner ratingTo = new JSpinner(new SpinnerNumberModel(5, 0, 5, 1));
    private final JSpinner partsFrom = new JSpinner(new SpinnerNumberModel(1, 1, 24, 1));
    private final JSpinner partsTo = new JSpinner(new SpinnerNumberModel(24, 1, 24, 1));
    private final JPanel moreFiltersPanel = new JPanel(new GridBagLayout());
    private final JScrollPane moreFiltersScroll = new JScrollPane(moreFiltersPanel);
    private final JCheckBox durationMinNone = new JCheckBox("None", true);
    private final JCheckBox durationMaxNone = new JCheckBox("None", true);
    private final JSpinner durationMinSec = DurationSpinners.create(0, 0, 7200, 1);
    private final JSpinner durationMaxSec = DurationSpinners.create(1200, 0, 7200, 1);
    private final JComboBox<String> lastPlayedMode = new JComboBox<>(new String[] {"Time", "Date"});
    private final JComboBox<LastPlayedTimeOptions.Option> lastPlayedFromCombo = new JComboBox<>();
    private final JComboBox<LastPlayedTimeOptions.Option> lastPlayedToCombo = new JComboBox<>();
    private final JPanel lastPlayedTimePanel = new JPanel();
    private final JPanel lastPlayedDatePanel = new JPanel();
    private final JTextField lastPlayedFromIso = new JTextField(12);
    private final JTextField lastPlayedToIso = new JTextField(12);
    private final FilterMultiSelect<String> transcriberFilter = new FilterMultiSelect<>(name -> name);
    private final JToggleButton filtersButton = new JToggleButton("Filters", new FilterIcon(16));
    private final Timer debounceTimer;

    private List<StatusInfo> statuses = List.of();
    private DefaultFilters defaultFilters = DefaultFilters.builtins();
    private Consumer<LibraryFilter> filterListener = filter -> {
    };
    private Consumer<LibrarySong> playListener = song -> {
    };
    private Consumer<LibrarySong> enqueueListener = song -> {
    };
    private BiConsumer<LibrarySong, SetlistInfo> addToSetlistListener = (song, setlist) -> {
    };
    private Consumer<LibrarySong> editSongListener = song -> {
    };
    private LongConsumer navigateToSetlistListener = setlistId -> {
    };
    private Runnable libraryChangedListener = () -> {
    };
    private SetlistRepository setlistRepository;
    private SongRepository songRepository;
    private PlayLogRepository playLogRepository;
    private Consumer<Map<String, Object>> headerStateListener = state -> {
    };
    private final Timer headerSaveTimer;
    private boolean suppressEvents;
    private boolean suppressHeaderEvents;

    public LibraryPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel heading = new JLabel("Library");
        heading.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        configureTable();
        debounceTimer = new Timer(300, e -> notifyFilterChanged());
        debounceTimer.setRepeats(false);
        headerSaveTimer = new Timer(400, e -> notifyHeaderStateChanged());
        headerSaveTimer.setRepeats(false);

        for (LastPlayedTimeOptions.Option option : LastPlayedTimeOptions.all()) {
            lastPlayedFromCombo.addItem(option);
            lastPlayedToCombo.addItem(option);
        }
        lastPlayedFromCombo.setSelectedIndex(LastPlayedTimeOptions.indexForSecondsAgo(0));
        lastPlayedToCombo.setSelectedIndex(LastPlayedTimeOptions.indexForSecondsAgo(null));
        lastPlayedFromCombo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value == null ? "" : value.label());
            if (isSelected) {
                label.setOpaque(true);
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            }
            return label;
        });
        lastPlayedToCombo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value == null ? "" : value.label());
            if (isSelected) {
                label.setOpaque(true);
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            }
            return label;
        });

        add(heading, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.add(buildFilterBar(), BorderLayout.NORTH);
        JPanel tableArea = new JPanel(new BorderLayout(8, 0));
        buildMoreFiltersPanel();
        moreFiltersScroll.setVisible(false);
        moreFiltersScroll.setPreferredSize(new Dimension(300, 0));
        moreFiltersScroll.setBorder(BorderFactory.createEmptyBorder());
        moreFiltersScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        tableArea.add(moreFiltersScroll, BorderLayout.WEST);
        tableArea.add(libraryStack, BorderLayout.CENTER);
        center.add(tableArea, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        wireFilterEvents();
        updateLastPlayedModeVisibility();
        libraryCards.show(libraryStack, CARD_EMPTY);
    }

    public void setFilterListener(Consumer<LibraryFilter> filterListener) {
        this.filterListener = Objects.requireNonNullElse(filterListener, filter -> {
        });
    }

    public void setPlayListener(Consumer<LibrarySong> playListener) {
        this.playListener = Objects.requireNonNullElse(playListener, song -> {
        });
    }

    public void setEnqueueListener(Consumer<LibrarySong> enqueueListener) {
        this.enqueueListener = Objects.requireNonNullElse(enqueueListener, song -> {
        });
    }

    public void setAddToSetlistListener(BiConsumer<LibrarySong, SetlistInfo> addToSetlistListener) {
        this.addToSetlistListener = Objects.requireNonNullElse(addToSetlistListener, (song, setlist) -> {
        });
    }

    public void setEditSongListener(Consumer<LibrarySong> editSongListener) {
        this.editSongListener = Objects.requireNonNullElse(editSongListener, song -> {
        });
    }

    public void setNavigateToSetlistListener(LongConsumer navigateToSetlistListener) {
        this.navigateToSetlistListener = Objects.requireNonNullElse(navigateToSetlistListener, id -> {
        });
    }

    public void setLibraryChangedListener(Runnable libraryChangedListener) {
        this.libraryChangedListener = Objects.requireNonNullElse(libraryChangedListener, () -> {
        });
    }

    public void setSetlistRepository(SetlistRepository setlistRepository) {
        this.setlistRepository = setlistRepository;
    }

    public void setSongRepository(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    public void setPlayLogRepository(PlayLogRepository playLogRepository) {
        this.playLogRepository = playLogRepository;
    }

    public void setHeaderStateListener(Consumer<Map<String, Object>> headerStateListener) {
        this.headerStateListener = Objects.requireNonNullElse(headerStateListener, state -> {
        });
    }

    /**
     * Apply saved {@code library_table_header_state} (Python-compatible keys).
     */
    public void applyHeaderState(Object rawState) {
        if (!(rawState instanceof Map<?, ?> map)) {
            return;
        }
        suppressHeaderEvents = true;
        try {
            Object sizesRaw = map.get("section_sizes");
            if (sizesRaw instanceof List<?> sizes) {
                TableColumnModel columns = table.getColumnModel();
                int count = Math.min(columns.getColumnCount(), sizes.size());
                for (int i = 0; i < count; i++) {
                    Object size = sizes.get(i);
                    if (size instanceof Number number) {
                        columns.getColumn(i).setPreferredWidth(Math.max(24, number.intValue()));
                    }
                }
            }
            Object hiddenRaw = map.get("hidden_columns");
            if (hiddenRaw instanceof List<?> hidden) {
                for (Object nameObj : hidden) {
                    if (nameObj == null) {
                        continue;
                    }
                    String name = String.valueOf(nameObj);
                    if ("Actions".equals(name) || "Edit".equals(name)) {
                        continue;
                    }
                    try {
                        TableColumn column = table.getColumn(name);
                        table.removeColumn(column);
                    } catch (IllegalArgumentException ignored) {
                        // already hidden or unknown
                    }
                }
            }
            Object sortColumnRaw = map.get("sort_column");
            Object sortOrderRaw = map.get("sort_order");
            if (sortColumnRaw instanceof Number number && table.getRowSorter() != null) {
                int modelCol = number.intValue();
                if (modelCol >= 0 && modelCol < tableModel.getColumnCount()) {
                    SortOrder order = "descending".equalsIgnoreCase(String.valueOf(sortOrderRaw))
                            ? SortOrder.DESCENDING
                            : SortOrder.ASCENDING;
                    List<RowSorter.SortKey> keys = List.of(new RowSorter.SortKey(modelCol, order));
                    table.getRowSorter().setSortKeys(keys);
                }
            }
        } finally {
            suppressHeaderEvents = false;
        }
    }

    public Map<String, Object> captureHeaderState() {
        Map<String, Object> state = new LinkedHashMap<>();
        TableColumnModel columns = table.getColumnModel();
        List<Integer> sizes = new ArrayList<>();
        List<String> visible = new ArrayList<>();
        for (int i = 0; i < columns.getColumnCount(); i++) {
            TableColumn column = columns.getColumn(i);
            sizes.add(column.getPreferredWidth());
            Object header = column.getHeaderValue();
            visible.add(header == null ? "" : String.valueOf(header));
        }
        state.put("section_sizes", sizes);
        List<String> hidden = new ArrayList<>();
        for (String name : LibraryTableModel.COLUMN_NAMES) {
            if (!visible.contains(name)) {
                hidden.add(name);
            }
        }
        state.put("hidden_columns", hidden);
        if (table.getRowSorter() != null && table.getRowSorter().getSortKeys() != null
                && !table.getRowSorter().getSortKeys().isEmpty()) {
            RowSorter.SortKey key = table.getRowSorter().getSortKeys().get(0);
            state.put("sort_column", key.getColumn());
            state.put("sort_order",
                    key.getSortOrder() == SortOrder.DESCENDING ? "descending" : "ascending");
        }
        return state;
    }

    private void scheduleHeaderStateSave() {
        if (suppressHeaderEvents) {
            return;
        }
        headerSaveTimer.restart();
    }

    private void notifyHeaderStateChanged() {
        headerStateListener.accept(captureHeaderState());
    }

    public void setStatuses(List<StatusInfo> statuses) {
        this.statuses = statuses == null ? List.of() : List.copyOf(statuses);
        statusFilter.setItems(this.statuses);
    }

    public void setTranscribers(List<String> transcribers) {
        transcriberFilter.setItems(transcribers);
    }

    public void setDefaultFilters(DefaultFilters defaultFilters) {
        this.defaultFilters = defaultFilters == null ? DefaultFilters.builtins() : defaultFilters.copy();
    }

    public void applyDefaultFilters() {
        applyFilterState(defaultFilters.toLibraryFilter());
        notifyFilterChanged();
    }

    public void clearFilters() {
        applyFilterState(LibraryFilter.cleared());
        notifyFilterChanged();
    }

    public LibraryFilter currentFilter() {
        LibraryFilter filter = new LibraryFilter();
        filter.setTitleOrComposer(titleComposerField.getText());
        filter.setStatusIds(statusFilter.selectedItems().stream()
                .map(StatusInfo::id)
                .toList());
        filter.setTranscribers(transcriberFilter.selectedItems());
        int inSetIndex = inSetCombo.getSelectedIndex();
        filter.setInSet(inSetIndex == 1
                ? LibraryFilter.InSet.YES
                : inSetIndex == 2 ? LibraryFilter.InSet.NO : LibraryFilter.InSet.EITHER);
        filter.setRatingFrom((Integer) ratingFrom.getValue());
        filter.setRatingTo((Integer) ratingTo.getValue());
        filter.setPartsMin((Integer) partsFrom.getValue());
        filter.setPartsMax((Integer) partsTo.getValue());
        filter.setDurationMinNone(durationMinNone.isSelected());
        filter.setDurationMaxNone(durationMaxNone.isSelected());
        filter.setDurationMinSec((Integer) durationMinSec.getValue());
        filter.setDurationMaxSec((Integer) durationMaxSec.getValue());
        filter.setLastPlayedMode(lastPlayedMode.getSelectedIndex() == 1
                ? LibraryFilter.LastPlayedMode.DATE
                : LibraryFilter.LastPlayedMode.TIME);
        if (filter.lastPlayedMode() == LibraryFilter.LastPlayedMode.TIME) {
            Integer fromSec = selectedSeconds(lastPlayedFromCombo);
            Integer toSec = selectedSeconds(lastPlayedToCombo);
            if (fromSec == null && toSec == null) {
                filter.setLastPlayedNever(true);
                filter.setLastPlayedFromSecondsAgo(null);
                filter.setLastPlayedToSecondsAgo(null);
            } else {
                filter.setLastPlayedNever(false);
                filter.setLastPlayedFromSecondsAgo(fromSec);
                filter.setLastPlayedToSecondsAgo(toSec);
            }
        } else {
            filter.setLastPlayedNever(false);
            filter.setLastPlayedFromSecondsAgo(null);
            filter.setLastPlayedToSecondsAgo(null);
            filter.setLastPlayedFromIso(blankToNull(lastPlayedFromIso.getText()));
            filter.setLastPlayedToIso(blankToNull(lastPlayedToIso.getText()));
        }
        return filter;
    }

    public void setSongs(List<LibrarySong> songs) {
        tableModel.setSongs(songs);
        if (songs == null || songs.isEmpty()) {
            libraryCards.show(libraryStack, CARD_EMPTY);
        } else {
            libraryCards.show(libraryStack, CARD_TABLE);
        }
    }

    private void configureTable() {
        table.setFillsViewportHeight(true);
        table.setRowHeight(28);
        table.setAutoCreateRowSorter(false);
        TableRowSorter<LibraryTableModel> sorter = new TableRowSorter<>(tableModel);
        tableModel.configureSorter(sorter);
        table.setRowSorter(sorter);
        // Bind by column name so drag-reorder / hide-show keep the right renderers.
        table.getColumn("Actions").setCellRenderer(new LibraryTableModel.ActionsRenderer());
        table.getColumn("Actions").setMinWidth(56);
        table.getColumn("Actions").setPreferredWidth(64);
        table.getColumn("Actions").setMaxWidth(72);
        table.getColumn("Playback History").setCellRenderer(new LibraryTableModel.HistoryRenderer());
        table.getColumn("Playback History").setMinWidth(120);
        table.getColumn("Playback History").setPreferredWidth(140);
        table.getColumn("Edit").setCellRenderer(new LibraryTableModel.EditRenderer());
        table.getColumn("Edit").setMinWidth(44);
        table.getColumn("Edit").setPreferredWidth(52);
        table.getColumn("Edit").setMaxWidth(64);
        table.getColumn("Duration").setCellRenderer(new LibraryTableModel.DurationRenderer());
        table.getColumn("Last played").setCellRenderer(new LibraryTableModel.LastPlayedRenderer());
        table.getColumn("Rating").setCellRenderer(new LibraryTableModel.RatingRenderer());
        table.getColumn("Set").setCellRenderer(new LibraryTableModel.SetRenderer());
        table.getColumn("Status").setCellRenderer(new LibraryTableModel.StatusCellRenderer());
        table.getColumn("Transcriber").setCellRenderer(new LibraryTableModel.EmDashRenderer());
        applyDefaultColumnWidths();
        table.setToolTipText("");
        table.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                int viewRow = table.rowAtPoint(e.getPoint());
                int viewCol = table.columnAtPoint(e.getPoint());
                if (viewRow < 0 || viewCol < 0) {
                    table.setToolTipText(null);
                    return;
                }
                int modelRow = table.convertRowIndexToModel(viewRow);
                int modelCol = table.convertColumnIndexToModel(viewCol);
                table.setToolTipText(tableModel.tooltipAt(modelRow, modelCol));
            }
        });
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                int viewRow = table.rowAtPoint(e.getPoint());
                int viewCol = table.columnAtPoint(e.getPoint());
                if (viewRow < 0 || viewCol < 0) {
                    return;
                }
                int modelRow = table.convertRowIndexToModel(viewRow);
                int modelCol = table.convertColumnIndexToModel(viewCol);
                LibrarySong song = tableModel.songAt(modelRow);
                if (song == null) {
                    return;
                }
                if (e.getClickCount() == 2
                        && modelCol != LibraryTableModel.COL_ACTIONS
                        && modelCol != LibraryTableModel.COL_HISTORY
                        && modelCol != LibraryTableModel.COL_EDIT
                        && modelCol != LibraryTableModel.COL_RATING
                        && modelCol != LibraryTableModel.COL_STATUS
                        && modelCol != LibraryTableModel.COL_SET) {
                    editSongListener.accept(song);
                    return;
                }
                if (e.getClickCount() != 1) {
                    return;
                }
                java.awt.Rectangle cell = table.getCellRect(viewRow, viewCol, false);
                int xInCell = e.getX() - cell.x;
                handleCellClick(song, modelCol, xInCell, cell.width, e.getXOnScreen(), e.getYOnScreen());
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                maybeShowRowPopup(e);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                maybeShowRowPopup(e);
            }
        });
        configureHeaderColumnMenu();
        table.getColumnModel().addColumnModelListener(new javax.swing.event.TableColumnModelListener() {
            @Override
            public void columnAdded(javax.swing.event.TableColumnModelEvent e) {
                scheduleHeaderStateSave();
            }

            @Override
            public void columnRemoved(javax.swing.event.TableColumnModelEvent e) {
                scheduleHeaderStateSave();
            }

            @Override
            public void columnMoved(javax.swing.event.TableColumnModelEvent e) {
                scheduleHeaderStateSave();
            }

            @Override
            public void columnMarginChanged(javax.swing.event.ChangeEvent e) {
                scheduleHeaderStateSave();
            }

            @Override
            public void columnSelectionChanged(javax.swing.event.ListSelectionEvent e) {
            }
        });
        table.getRowSorter().addRowSorterListener(e -> scheduleHeaderStateSave());

        JScrollPane tableScroll = new JScrollPane(table);
        JPanel empty = buildEmptyState();
        libraryStack.add(tableScroll, CARD_TABLE);
        libraryStack.add(empty, CARD_EMPTY);
    }

    /**
     * Compact columns sized to their header (or rating stars). Max width keeps
     * AUTO_RESIZE_ALL_COLUMNS from stretching them past that intent.
     */
    private void applyDefaultColumnWidths() {
        FontMetrics headerMetrics = table.getTableHeader().getFontMetrics(table.getTableHeader().getFont());
        int headerPad = 16;

        setCompactColumnWidth(table.getColumn("Duration"), headerMetrics.stringWidth("Duration") + headerPad);
        setCompactColumnWidth(table.getColumn("Last played"), headerMetrics.stringWidth("Last played") + headerPad);
        setCompactColumnWidth(table.getColumn("Parts"), headerMetrics.stringWidth("Parts") + headerPad);
        setCompactColumnWidth(table.getColumn("Set"), headerMetrics.stringWidth("Set") + headerPad);
        TableColumn history = table.getColumn("Playback History");
        history.setPreferredWidth(140);
        history.setMaxWidth(180);

        Font starFont = table.getFont().deriveFont(table.getFont().getSize2D() + 4f);
        FontMetrics starMetrics = table.getFontMetrics(starFont);
        setCompactColumnWidth(
                table.getColumn("Rating"),
                starMetrics.stringWidth("\u2605\u2605\u2605\u2605\u2605") + headerPad);

        // About half a typical equal-column share; capped so it doesn't grow with Title/etc.
        TableColumn status = table.getColumn("Status");
        status.setPreferredWidth(60);
        status.setMaxWidth(100);
    }

    private static void setCompactColumnWidth(TableColumn column, int width) {
        int safe = Math.max(width, 28);
        column.setMinWidth(Math.min(safe, 24));
        column.setPreferredWidth(safe);
        column.setMaxWidth(safe);
    }

    private void maybeShowRowPopup(java.awt.event.MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int viewRow = table.rowAtPoint(e.getPoint());
        if (viewRow < 0) {
            return;
        }
        if (!table.isRowSelected(viewRow)) {
            table.setRowSelectionInterval(viewRow, viewRow);
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        LibrarySong song = tableModel.songAt(modelRow);
        if (song == null) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        JMenuItem play = new JMenuItem("Play");
        play.addActionListener(ev -> playListener.accept(song));
        JMenuItem enqueue = new JMenuItem("Add to queue");
        enqueue.addActionListener(ev -> enqueueListener.accept(song));
        menu.add(play);
        menu.add(enqueue);
        JMenuItem edit = new JMenuItem("Edit");
        edit.addActionListener(ev -> editSongListener.accept(song));
        menu.add(edit);
        menu.addSeparator();
        menu.add(AddToSetlistMenu.build(
                setlistRepository, null, setlist -> addToSetlistListener.accept(song, setlist)));
        menu.show(table, e.getX(), e.getY());
    }

    private void handleCellClick(
            LibrarySong song, int modelCol, int xInCell, int cellWidth, int screenX, int screenY) {
        switch (modelCol) {
            case LibraryTableModel.COL_ACTIONS -> {
                if (xInCell < cellWidth / 2) {
                    playListener.accept(song);
                } else {
                    enqueueListener.accept(song);
                }
            }
            case LibraryTableModel.COL_HISTORY -> handleHistoryClick(song, xInCell, cellWidth);
            case LibraryTableModel.COL_RATING -> handleRatingClick(song, xInCell, cellWidth);
            case LibraryTableModel.COL_SET -> handleSetClick(song, screenX, screenY);
            case LibraryTableModel.COL_STATUS -> handleStatusClick(song, screenX, screenY);
            case LibraryTableModel.COL_EDIT -> editSongListener.accept(song);
            default -> {
            }
        }
    }

    private void handleHistoryClick(LibrarySong song, int xInCell, int cellWidth) {
        if (playLogRepository == null) {
            return;
        }
        // Three equal thirds: Now | Set… | History
        int third = Math.max(1, cellWidth / 3);
        if (xInCell < third) {
            try {
                playLogRepository.logPlay(song.id(), null, null);
                libraryChangedListener.run();
            } catch (LibraryException ex) {
                JOptionPane.showMessageDialog(
                        this, ex.getMessage(), "Playback History", JOptionPane.ERROR_MESSAGE);
            }
        } else if (xInCell < third * 2) {
            PlayDateTimeDialog dialog = new PlayDateTimeDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "Set last played",
                    java.time.Instant.now(),
                    null,
                    false);
            dialog.showDialog().ifPresent(result -> {
                try {
                    playLogRepository.logPlayAt(song.id(), result.playedAtIso(), null, null);
                    libraryChangedListener.run();
                } catch (LibraryException ex) {
                    JOptionPane.showMessageDialog(
                            this, ex.getMessage(), "Playback History", JOptionPane.ERROR_MESSAGE);
                }
            });
        } else {
            PlayHistoryDialog dialog = new PlayHistoryDialog(
                    SwingUtilities.getWindowAncestor(this),
                    playLogRepository,
                    song.id(),
                    song.title());
            if (dialog.showDialog()) {
                libraryChangedListener.run();
            }
        }
    }

    private void handleRatingClick(LibrarySong song, int xInCell, int cellWidth) {
        if (songRepository == null) {
            return;
        }
        int starWidth = Math.max(1, cellWidth / 5);
        int starIdx = Math.min(5, Math.max(1, (xInCell / starWidth) + 1));
        int current = song.rating() == null ? 0 : song.rating();
        int newRating = current == starIdx ? 0 : starIdx;
        try {
            songRepository.updateSongAppMetadata(song.id(), SongAppMetadataUpdate.ratingOnly(newRating));
            libraryChangedListener.run();
        } catch (LibraryException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Rating", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleSetClick(LibrarySong song, int screenX, int screenY) {
        if (!song.inUpcomingSet() || songRepository == null) {
            return;
        }
        try {
            List<SetlistRef> setlists = songRepository.listUnlockedSetlistsContainingSong(song.id());
            if (setlists.isEmpty()) {
                return;
            }
            JPopupMenu menu = new JPopupMenu();
            for (SetlistRef setlist : setlists) {
                JMenuItem item = new JMenuItem("Go to: " + setlist.name());
                long setlistId = setlist.id();
                item.addActionListener(ev -> navigateToSetlistListener.accept(setlistId));
                menu.add(item);
            }
            java.awt.Point tableLoc = table.getLocationOnScreen();
            menu.show(table, screenX - tableLoc.x, screenY - tableLoc.y);
        } catch (LibraryException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Set", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleStatusClick(LibrarySong song, int screenX, int screenY) {
        if (songRepository == null || statuses.isEmpty()) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        for (StatusInfo status : statuses) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(status.name());
            item.setSelected(song.statusId() != null && song.statusId() == status.id());
            long statusId = status.id();
            item.addActionListener(ev -> {
                try {
                    songRepository.updateSongAppMetadata(
                            song.id(), SongAppMetadataUpdate.statusOnly(statusId));
                    libraryChangedListener.run();
                } catch (LibraryException ex) {
                    JOptionPane.showMessageDialog(
                            this, ex.getMessage(), "Status", JOptionPane.ERROR_MESSAGE);
                }
            });
            menu.add(item);
        }
        java.awt.Point tableLoc = table.getLocationOnScreen();
        menu.show(table, screenX - tableLoc.x, screenY - tableLoc.y);
    }

    /** Right-click a column header to show or hide columns. */
    private void configureHeaderColumnMenu() {
        // Snapshot columns while all are still in the view model.
        JPopupMenu headerMenu = new JPopupMenu();
        List<String> columnNames = Arrays.asList(LibraryTableModel.COLUMN_NAMES);
        for (int i = 0; i < LibraryTableModel.COLUMN_NAMES.length; i++) {
            int modelIndex = i;
            String name = LibraryTableModel.COLUMN_NAMES[i];
            if (modelIndex == LibraryTableModel.COL_ACTIONS || modelIndex == LibraryTableModel.COL_EDIT) {
                continue;
            }
            TableColumn column = table.getColumn(name);
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(name, true);
            item.addActionListener(e -> {
                if (item.isSelected()) {
                    table.addColumn(column);
                    int from = table.getColumnCount() - 1;
                    int to = -1;
                    for (int viewIndex = 0; viewIndex <= from; viewIndex++) {
                        String visibleName = table.getColumnName(viewIndex);
                        if (columnNames.indexOf(visibleName) > modelIndex) {
                            to = viewIndex;
                            break;
                        }
                    }
                    if (to != -1) {
                        table.moveColumn(from, to);
                    }
                } else if (table.getColumnCount() <= 1) {
                    item.setSelected(true);
                } else {
                    table.removeColumn(column);
                }
                scheduleHeaderStateSave();
            });
            headerMenu.add(item);
        }
        table.getTableHeader().setComponentPopupMenu(headerMenu);
    }

    private JPanel buildEmptyState() {
        JPanel empty = new JPanel(new BorderLayout());
        empty.setBorder(BorderFactory.createEmptyBorder(48, 24, 48, 24));
        JLabel message = new JLabel(
                "<html><div style='text-align:center'>"
                        + "No songs match the current filters.<br>"
                        + "Try <b>Clear Filters</b>, or adjust Default Filters in Settings."
                        + "</div></html>",
                JLabel.CENTER);
        message.setFont(message.getFont().deriveFont(Font.PLAIN, 14f));
        empty.add(message, BorderLayout.CENTER);
        return empty;
    }

    private JPanel buildFilterBar() {
        JPanel mainRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filtersButton.setFocusable(false);
        filtersButton.setHorizontalAlignment(SwingConstants.CENTER);
        filtersButton.setHorizontalTextPosition(SwingConstants.RIGHT);
        filtersButton.setIconTextGap(6);
        filtersButton.setMargin(new Insets(4, 10, 4, 12));
        filtersButton.addActionListener(e -> {
            moreFiltersScroll.setVisible(filtersButton.isSelected());
            revalidate();
        });
        mainRow.add(filtersButton);
        mainRow.add(new JLabel("Title / Composer"));
        mainRow.add(titleComposerField);
        JButton reset = new JButton("Reset Filters");
        JButton clear = new JButton("Clear Filters");
        reset.addActionListener(e -> applyDefaultFilters());
        clear.addActionListener(e -> clearFilters());
        mainRow.add(reset);
        mainRow.add(clear);
        return mainRow;
    }

    private void buildMoreFiltersPanel() {
        moreFiltersPanel.setBorder(BorderFactory.createTitledBorder("Filters"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        int row = 0;

        c.gridy = row++;
        c.gridx = 0;
        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        statusRow.add(new JLabel("Status"));
        statusRow.add(statusFilter);
        moreFiltersPanel.add(statusRow, c);

        c.gridy = row++;
        JPanel inSetRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        inSetRow.add(new JLabel("In set"));
        inSetRow.add(inSetCombo);
        moreFiltersPanel.add(inSetRow, c);

        c.gridy = row++;
        moreFiltersPanel.add(new JLabel("Rating"), c);
        c.gridy = row++;
        JPanel ratingRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        ratingRow.add(ratingFrom);
        ratingRow.add(new JLabel("\u2013"));
        ratingRow.add(ratingTo);
        moreFiltersPanel.add(ratingRow, c);

        c.gridy = row++;
        moreFiltersPanel.add(new JLabel("Duration"), c);
        c.gridy = row++;
        JPanel durationFromRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        durationFromRow.add(rangeLabel("From:"));
        durationFromRow.add(durationMinSec);
        durationFromRow.add(durationMinNone);
        moreFiltersPanel.add(durationFromRow, c);
        c.gridy = row++;
        JPanel durationToRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        durationToRow.add(rangeLabel("To:"));
        durationToRow.add(durationMaxSec);
        durationToRow.add(durationMaxNone);
        moreFiltersPanel.add(durationToRow, c);

        c.gridy = row++;
        JPanel lastPlayedModeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        lastPlayedModeRow.add(new JLabel("Last played"));
        lastPlayedModeRow.add(lastPlayedMode);
        moreFiltersPanel.add(lastPlayedModeRow, c);

        lastPlayedTimePanel.setLayout(new BoxLayout(lastPlayedTimePanel, BoxLayout.Y_AXIS));
        JPanel lastPlayedTimeFromRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        lastPlayedTimeFromRow.setAlignmentX(LEFT_ALIGNMENT);
        lastPlayedTimeFromRow.add(rangeLabel("From:"));
        lastPlayedTimeFromRow.add(lastPlayedFromCombo);
        JPanel lastPlayedTimeToRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        lastPlayedTimeToRow.setAlignmentX(LEFT_ALIGNMENT);
        lastPlayedTimeToRow.add(rangeLabel("To:"));
        lastPlayedTimeToRow.add(lastPlayedToCombo);
        lastPlayedTimePanel.add(lastPlayedTimeFromRow);
        lastPlayedTimePanel.add(lastPlayedTimeToRow);

        lastPlayedDatePanel.setLayout(new BoxLayout(lastPlayedDatePanel, BoxLayout.Y_AXIS));
        JPanel lastPlayedDateFromRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        lastPlayedDateFromRow.setAlignmentX(LEFT_ALIGNMENT);
        lastPlayedDateFromRow.add(rangeLabel("From:"));
        lastPlayedDateFromRow.add(lastPlayedFromIso);
        JPanel lastPlayedDateToRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        lastPlayedDateToRow.setAlignmentX(LEFT_ALIGNMENT);
        lastPlayedDateToRow.add(rangeLabel("To:"));
        lastPlayedDateToRow.add(lastPlayedToIso);
        lastPlayedDatePanel.add(lastPlayedDateFromRow);
        lastPlayedDatePanel.add(lastPlayedDateToRow);

        JPanel lastPlayedHost = new JPanel(new BorderLayout());
        lastPlayedHost.add(lastPlayedTimePanel, BorderLayout.NORTH);
        lastPlayedHost.add(lastPlayedDatePanel, BorderLayout.SOUTH);
        c.gridy = row++;
        moreFiltersPanel.add(lastPlayedHost, c);

        c.gridy = row++;
        moreFiltersPanel.add(new JLabel("Parts"), c);
        c.gridy = row++;
        JPanel partsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        partsRow.add(partsFrom);
        partsRow.add(new JLabel("\u2013"));
        partsRow.add(partsTo);
        moreFiltersPanel.add(partsRow, c);

        c.gridy = row++;
        JPanel transcriberRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        transcriberRow.add(new JLabel("Transcriber"));
        transcriberRow.add(transcriberFilter);
        moreFiltersPanel.add(transcriberRow, c);

        // Keep controls packed to the top of the sidebar.
        c.gridy = row;
        c.weighty = 1;
        moreFiltersPanel.add(new JPanel(), c);
    }

    private static JLabel rangeLabel(String text) {
        JLabel label = new JLabel(text);
        label.setPreferredSize(new Dimension(40, label.getPreferredSize().height));
        return label;
    }

    private void wireFilterEvents() {
        titleComposerField.getDocument().addDocumentListener(debounceListener());
        statusFilter.setChangeListener(selected -> notifyFilterChanged());
        transcriberFilter.setChangeListener(selected -> notifyFilterChanged());
        inSetCombo.addActionListener(e -> notifyFilterChanged());
        wireCoupledRangeSpinners(ratingFrom, ratingTo);
        wireCoupledRangeSpinners(partsFrom, partsTo);
        SpinnerMouseWheel.install(ratingFrom);
        SpinnerMouseWheel.install(ratingTo);
        SpinnerMouseWheel.install(partsFrom);
        SpinnerMouseWheel.install(partsTo);
        durationMinNone.addActionListener(e -> notifyFilterChanged());
        durationMaxNone.addActionListener(e -> notifyFilterChanged());
        durationMinSec.addChangeListener(e -> notifyFilterChanged());
        durationMaxSec.addChangeListener(e -> notifyFilterChanged());
        lastPlayedMode.addActionListener(e -> {
            updateLastPlayedModeVisibility();
            notifyFilterChanged();
        });
        lastPlayedFromCombo.addActionListener(e -> notifyFilterChanged());
        lastPlayedToCombo.addActionListener(e -> notifyFilterChanged());
        lastPlayedFromIso.getDocument().addDocumentListener(debounceListener());
        lastPlayedToIso.getDocument().addDocumentListener(debounceListener());
    }

    /**
     * Keeps a from/to spinner pair ordered: raising from past to lifts to,
     * and lowering to past from drops from.
     */
    private void wireCoupledRangeSpinners(JSpinner from, JSpinner to) {
        from.addChangeListener(e -> {
            if (suppressEvents) {
                return;
            }
            int fromValue = ((Number) from.getValue()).intValue();
            int toValue = ((Number) to.getValue()).intValue();
            if (fromValue > toValue) {
                suppressEvents = true;
                try {
                    to.setValue(fromValue);
                } finally {
                    suppressEvents = false;
                }
            }
            notifyFilterChanged();
        });
        to.addChangeListener(e -> {
            if (suppressEvents) {
                return;
            }
            int fromValue = ((Number) from.getValue()).intValue();
            int toValue = ((Number) to.getValue()).intValue();
            if (toValue < fromValue) {
                suppressEvents = true;
                try {
                    from.setValue(toValue);
                } finally {
                    suppressEvents = false;
                }
            }
            notifyFilterChanged();
        });
    }

    private DocumentListener debounceListener() {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                scheduleFilterChange();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                scheduleFilterChange();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                scheduleFilterChange();
            }
        };
    }

    private void updateLastPlayedModeVisibility() {
        boolean dateMode = lastPlayedMode.getSelectedIndex() == 1;
        lastPlayedTimePanel.setVisible(!dateMode);
        lastPlayedDatePanel.setVisible(dateMode);
        moreFiltersPanel.revalidate();
        moreFiltersScroll.revalidate();
    }

    /** Classic funnel filter glyph sized for a toolbar toggle. */
    private static final class FilterIcon implements Icon {
        private final int size;

        FilterIcon(int size) {
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(c.getForeground());
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            float left = x + size * 0.12f;
            float right = x + size * 0.88f;
            float top = y + size * 0.18f;
            float midY = y + size * 0.48f;
            float neck = size * 0.18f;
            float cx = x + size * 0.5f;
            float bottom = y + size * 0.82f;

            Path2D funnel = new Path2D.Float();
            funnel.moveTo(left, top);
            funnel.lineTo(right, top);
            funnel.lineTo(cx + neck, midY);
            funnel.lineTo(cx + neck, bottom);
            funnel.lineTo(cx - neck, bottom);
            funnel.lineTo(cx - neck, midY);
            funnel.closePath();
            g2.draw(funnel);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    private void scheduleFilterChange() {
        if (suppressEvents) {
            return;
        }
        debounceTimer.restart();
    }

    private void notifyFilterChanged() {
        if (suppressEvents) {
            return;
        }
        filterListener.accept(currentFilter());
    }

    private void applyFilterState(LibraryFilter filter) {
        suppressEvents = true;
        try {
            titleComposerField.setText(filter.titleOrComposer());
            statusFilter.setSelectedItems(statuses.stream()
                    .filter(status -> filter.statusIds().contains(status.id()))
                    .toList());
            transcriberFilter.setSelectedItems(filter.transcribers());
            inSetCombo.setSelectedIndex(switch (filter.inSet()) {
                case YES -> 1;
                case NO -> 2;
                default -> 0;
            });
            ratingFrom.setValue(filter.ratingFrom() == null ? 0 : filter.ratingFrom());
            ratingTo.setValue(filter.ratingTo() == null ? 5 : filter.ratingTo());
            partsFrom.setValue(filter.partsMin() == null ? 1 : filter.partsMin());
            partsTo.setValue(filter.partsMax() == null ? 24 : filter.partsMax());
            durationMinNone.setSelected(filter.durationMinNone());
            durationMaxNone.setSelected(filter.durationMaxNone());
            durationMinSec.setValue(filter.durationMinSec() == null ? 0 : filter.durationMinSec());
            durationMaxSec.setValue(filter.durationMaxSec() == null ? 1200 : filter.durationMaxSec());
            lastPlayedMode.setSelectedIndex(
                    filter.lastPlayedMode() == LibraryFilter.LastPlayedMode.DATE ? 1 : 0);
            if (filter.lastPlayedNever()) {
                lastPlayedFromCombo.setSelectedIndex(LastPlayedTimeOptions.indexForSecondsAgo(null));
                lastPlayedToCombo.setSelectedIndex(LastPlayedTimeOptions.indexForSecondsAgo(null));
            } else {
                lastPlayedFromCombo.setSelectedIndex(
                        LastPlayedTimeOptions.indexForSecondsAgo(
                                filter.lastPlayedFromSecondsAgo() == null
                                        ? 0
                                        : filter.lastPlayedFromSecondsAgo()));
                lastPlayedToCombo.setSelectedIndex(
                        LastPlayedTimeOptions.indexForSecondsAgo(filter.lastPlayedToSecondsAgo()));
            }
            lastPlayedFromIso.setText(filter.lastPlayedFromIso() == null ? "" : filter.lastPlayedFromIso());
            lastPlayedToIso.setText(filter.lastPlayedToIso() == null ? "" : filter.lastPlayedToIso());
            updateLastPlayedModeVisibility();
        } finally {
            suppressEvents = false;
        }
    }

    private static Integer selectedSeconds(JComboBox<LastPlayedTimeOptions.Option> combo) {
        LastPlayedTimeOptions.Option option = (LastPlayedTimeOptions.Option) combo.getSelectedItem();
        if (option == null) {
            return 0;
        }
        return option.secondsAgo();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

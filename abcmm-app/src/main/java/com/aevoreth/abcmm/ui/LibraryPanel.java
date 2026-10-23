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
import java.awt.event.MouseEvent;
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
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.library.LibraryFilter;
import com.aevoreth.abcmm.domain.library.LibrarySong;
import com.aevoreth.abcmm.domain.library.PlayLogRepository;
import com.aevoreth.abcmm.domain.library.SongRepository;
import com.aevoreth.abcmm.domain.library.StatusInfo;
import com.aevoreth.abcmm.domain.prefs.DefaultFilters;
import com.aevoreth.abcmm.domain.prefs.LibraryFilterPreset;
import com.aevoreth.abcmm.domain.setlist.SetlistInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistRepository;

/**
 * Library table with filters/search matching the Python edition terminology.
 */
public final class LibraryPanel extends JPanel {

    private static final String CARD_TABLE = "table";
    private static final String CARD_EMPTY = "empty";

    private final LibraryTableModel tableModel = new LibraryTableModel();
    /**
     * Right-click on a row that is already selected must not collapse a multi-selection.
     * The table UI replaces the selection on mouse press, which happens before the
     * popup trigger on Windows.
     */
    private boolean keepRightClickSelection;
    private final JTable table = new JTable(tableModel) {
        @Override
        protected void processMouseEvent(MouseEvent e) {
            if (SwingUtilities.isRightMouseButton(e)) {
                int id = e.getID();
                if (id == MouseEvent.MOUSE_PRESSED) {
                    int row = rowAtPoint(e.getPoint());
                    keepRightClickSelection = row >= 0 && isRowSelected(row);
                }
                if (keepRightClickSelection && isRightClickSequence(id)) {
                    if (id == MouseEvent.MOUSE_PRESSED && !hasFocus() && isRequestFocusEnabled()) {
                        requestFocusInWindow();
                    }
                    if (e.isPopupTrigger()) {
                        showRowPopup(e);
                    }
                    if (id == MouseEvent.MOUSE_CLICKED) {
                        keepRightClickSelection = false;
                    }
                    return;
                }
            }
            super.processMouseEvent(e);
        }

        private boolean isRightClickSequence(int id) {
            return id == MouseEvent.MOUSE_PRESSED
                    || id == MouseEvent.MOUSE_RELEASED
                    || id == MouseEvent.MOUSE_CLICKED;
        }
    };
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
    private JSplitPane filterSplit;
    private int filterDividerSize = 8;
    private int filterSplitPreferred = 320;
    private boolean suppressFilterSplitEvents;
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
    private final JComboBox<String> presetCombo = new JComboBox<>();
    private final Timer debounceTimer;

    private List<StatusInfo> statuses = List.of();
    private DefaultFilters defaultFilters = DefaultFilters.builtins();
    private List<LibraryFilterPreset> filterPresets = List.of();
    private String loadedPresetName = LibraryFilterPreset.DEFAULT_NAME;
    private DefaultFilters loadedSnapshot = DefaultFilters.builtins();
    private boolean presetModified;
    private boolean suppressPresetEvents;
    private Consumer<LibraryFilter> filterListener = filter -> {
    };
    private Consumer<LibrarySong> playListener = song -> {
    };
    private Consumer<LibrarySong> enqueueListener = song -> {
    };
    private Consumer<List<LibrarySong>> playSelectionListener;
    private Consumer<List<LibrarySong>> enqueueSelectionListener;
    private BiConsumer<List<LibrarySong>, SetlistInfo> addToSetlistListener = (songs, setlist) -> {
    };
    private Consumer<List<LibrarySong>> newSetlistListener = songs -> {
    };
    private Consumer<LibrarySong> editSongListener = song -> {
    };
    private LongConsumer navigateToSetlistListener = setlistId -> {
    };
    private Runnable libraryChangedListener = () -> {
    };
    private Runnable openUserGuideListener = () -> {
    };
    private SetlistRepository setlistRepository;
    private SongRepository songRepository;
    private final SongFieldActions songFields = new SongFieldActions(this);
    private Consumer<Map<String, Object>> headerStateListener = state -> {
    };
    private BiConsumer<DefaultFilters, List<LibraryFilterPreset>> filterPresetsListener = (defaults, presets) -> {
    };
    private Consumer<Integer> filterSplitListener = divider -> {
    };
    private final Timer filterSplitSaveTimer;
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
        filterSplitSaveTimer = new Timer(400, e -> filterSplitListener.accept(filterSplitPreferred));
        filterSplitSaveTimer.setRepeats(false);

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
        buildMoreFiltersPanel();
        moreFiltersScroll.setVisible(false);
        moreFiltersScroll.setMinimumSize(new Dimension(240, 0));
        moreFiltersScroll.setPreferredSize(new Dimension(filterSplitPreferred, 0));
        moreFiltersScroll.setBorder(BorderFactory.createEmptyBorder());
        moreFiltersScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        libraryStack.setMinimumSize(new Dimension(240, 0));
        filterSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, moreFiltersScroll, libraryStack);
        filterSplit.setContinuousLayout(true);
        filterSplit.setResizeWeight(0);
        filterDividerSize = Math.max(4, filterSplit.getDividerSize());
        filterSplit.setDividerSize(0);
        filterSplit.setDividerLocation(0);
        filterSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            if (suppressFilterSplitEvents || filterSplit.getDividerSize() <= 0) {
                return;
            }
            int location = filterSplit.getDividerLocation();
            if (location >= 180) {
                filterSplitPreferred = location;
                filterSplitSaveTimer.restart();
            }
        });
        center.add(filterSplit, BorderLayout.CENTER);
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

    /** Play the first song and append the rest. Used by the row context menu. */
    public void setPlaySelectionListener(Consumer<List<LibrarySong>> playSelectionListener) {
        this.playSelectionListener = playSelectionListener;
    }

    /** Append every song. Used by the row context menu. */
    public void setEnqueueSelectionListener(Consumer<List<LibrarySong>> enqueueSelectionListener) {
        this.enqueueSelectionListener = enqueueSelectionListener;
    }

    public void setAddToSetlistListener(BiConsumer<List<LibrarySong>, SetlistInfo> addToSetlistListener) {
        this.addToSetlistListener = Objects.requireNonNullElse(addToSetlistListener, (songs, setlist) -> {
        });
    }

    /** Create a setlist and add the given songs. Invoked from Add to setlist → New Set... */
    public void setNewSetlistListener(Consumer<List<LibrarySong>> newSetlistListener) {
        this.newSetlistListener = Objects.requireNonNullElse(newSetlistListener, songs -> {
        });
    }

    public void setEditSongListener(Consumer<LibrarySong> editSongListener) {
        this.editSongListener = Objects.requireNonNullElse(editSongListener, song -> {
        });
        songFields.setEditSong(this.editSongListener);
    }

    public void setOpenUserGuideListener(Runnable openUserGuideListener) {
        this.openUserGuideListener = Objects.requireNonNullElse(openUserGuideListener, () -> {
        });
    }

    public void setNavigateToSetlistListener(LongConsumer navigateToSetlistListener) {
        this.navigateToSetlistListener = Objects.requireNonNullElse(navigateToSetlistListener, id -> {
        });
        songFields.setNavigateToSetlist(this.navigateToSetlistListener);
    }

    public void setLibraryChangedListener(Runnable libraryChangedListener) {
        this.libraryChangedListener = Objects.requireNonNullElse(libraryChangedListener, () -> {
        });
        songFields.setOnChanged(this.libraryChangedListener);
    }

    public void setSetlistRepository(SetlistRepository setlistRepository) {
        this.setlistRepository = setlistRepository;
    }

    public void setSongRepository(SongRepository songRepository) {
        this.songRepository = songRepository;
        songFields.setSongRepository(songRepository);
    }

    public void setInstrumentNames(Map<Long, String> instrumentNames) {
        tableModel.setInstrumentNames(instrumentNames);
    }

    public void setPlayLogRepository(PlayLogRepository playLogRepository) {
        songFields.setPlayLogRepository(playLogRepository);
    }

    public void setFilterSplitListener(Consumer<Integer> filterSplitListener) {
        this.filterSplitListener = Objects.requireNonNullElse(filterSplitListener, divider -> {
        });
    }

    public void applyFilterSplit(Object raw) {
        if (raw instanceof Number number && number.intValue() >= 180) {
            filterSplitPreferred = number.intValue();
        }
    }

    public int filterSplitDivider() {
        return filterSplitPreferred;
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
                        applyColumnWidth(columns.getColumn(i), number.intValue());
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
            int width = column.getWidth();
            sizes.add(width > 0 ? width : column.getPreferredWidth());
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

    private void setFiltersPaneVisible(boolean visible) {
        moreFiltersScroll.setVisible(visible);
        if (filterSplit == null) {
            revalidate();
            return;
        }
        suppressFilterSplitEvents = true;
        try {
            if (visible) {
                moreFiltersScroll.setMinimumSize(new Dimension(240, 0));
                filterSplit.setDividerSize(filterDividerSize);
                int width = filterSplit.getWidth();
                int divider = filterSplitPreferred;
                if (width > 0) {
                    int max = Math.max(240, width - filterSplit.getDividerSize() - 240);
                    divider = Math.min(divider, max);
                }
                filterSplit.setDividerLocation(Math.max(240, divider));
            } else {
                if (filterSplit.getDividerSize() > 0 && filterSplit.getDividerLocation() >= 180) {
                    filterSplitPreferred = filterSplit.getDividerLocation();
                    filterSplitSaveTimer.restart();
                }
                moreFiltersScroll.setMinimumSize(new Dimension(0, 0));
                filterSplit.setDividerLocation(0);
                filterSplit.setDividerSize(0);
            }
        } finally {
            suppressFilterSplitEvents = false;
        }
        revalidate();
    }

    private static void applyColumnWidth(TableColumn column, int width) {
        if (column.getMaxWidth() == 0) {
            return;
        }
        int safe = Math.max(column.getMinWidth(), width);
        int max = column.getMaxWidth();
        if (max > 0) {
            safe = Math.min(safe, max);
        }
        column.setPreferredWidth(safe);
        column.setWidth(safe);
    }

    public void setStatuses(List<StatusInfo> statuses) {
        this.statuses = statuses == null ? List.of() : List.copyOf(statuses);
        statusFilter.setItems(this.statuses);
        songFields.setStatuses(this.statuses);
    }

    public void setTranscribers(List<String> transcribers) {
        transcriberFilter.setItems(transcribers);
        refreshPresetModified();
    }

    public void setFilterPresets(DefaultFilters defaults, List<LibraryFilterPreset> presets) {
        this.defaultFilters = defaults == null ? DefaultFilters.builtins() : defaults.copy();
        this.filterPresets = LibraryFilterPreset.sanitize(presets);
        if (!presetStillAvailable(loadedPresetName)) {
            loadedPresetName = LibraryFilterPreset.DEFAULT_NAME;
        }
        rebuildPresetCombo();
    }

    /**
     * Replace the preset list and re-apply the current selection, or Default if that name is gone.
     */
    public void reloadAppliedPreset(DefaultFilters defaults, List<LibraryFilterPreset> presets) {
        String previous = loadedPresetName;
        setFilterPresets(defaults, presets);
        applyPreset(presetStillAvailable(previous) ? previous : LibraryFilterPreset.DEFAULT_NAME);
    }

    public void setFilterPresetsChangedListener(
            BiConsumer<DefaultFilters, List<LibraryFilterPreset>> filterPresetsListener) {
        this.filterPresetsListener = Objects.requireNonNullElse(filterPresetsListener, (defaults, presets) -> {
        });
    }

    public void applyDefaultFilters() {
        applyPreset(LibraryFilterPreset.DEFAULT_NAME);
    }

    public void clearFilters() {
        applyPreset(LibraryFilterPreset.NONE_NAME);
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
                showRowPopup(e);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                showRowPopup(e);
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

    private void showRowPopup(java.awt.event.MouseEvent e) {
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
        LibrarySong clicked = tableModel.songAt(modelRow);
        if (clicked == null) {
            return;
        }
        List<LibrarySong> songs = selectedSongsInViewOrder();
        if (songs.isEmpty()) {
            songs = List.of(clicked);
        }
        rowPopup(songs, clicked).show(table, e.getX(), e.getY());
    }

    /** Selected rows from top to bottom in the current sort. */
    List<LibrarySong> selectedSongsInViewOrder() {
        int[] viewRows = table.getSelectedRows();
        Arrays.sort(viewRows);
        List<LibrarySong> songs = new ArrayList<>();
        for (int viewRow : viewRows) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            LibrarySong song = tableModel.songAt(modelRow);
            if (song != null) {
                songs.add(song);
            }
        }
        return songs;
    }

    JTable libraryTable() {
        return table;
    }

    /**
     * Row context menu. {@code songs} is the selection in view order; {@code clicked}
     * is the row under the pointer (used by Edit).
     */
    JPopupMenu rowPopup(List<LibrarySong> songs, LibrarySong clicked) {
        List<LibrarySong> selected = nonNullSongs(songs);
        JPopupMenu menu = new JPopupMenu();
        JMenuItem play = new JMenuItem("Play");
        play.addActionListener(ev -> playSongs(selected));
        JMenuItem enqueue = new JMenuItem("Add to queue");
        enqueue.addActionListener(ev -> enqueueSongs(selected));
        menu.add(play);
        menu.add(enqueue);
        JMenuItem edit = new JMenuItem("Edit");
        edit.setEnabled(clicked != null);
        edit.addActionListener(ev -> {
            if (clicked != null) {
                editSongListener.accept(clicked);
            }
        });
        menu.add(edit);
        menu.addSeparator();
        menu.add(songFields.ratingMenu(selected));
        menu.add(songFields.statusMenu(selected));
        menu.add(songFields.historyMenu(selected));
        menu.addSeparator();
        menu.add(AddToSetlistMenu.build(
                setlistRepository,
                null,
                setlist -> addToSetlistListener.accept(selected, setlist),
                () -> newSetlistListener.accept(selected)));
        return menu;
    }

    void playSongs(List<LibrarySong> songs) {
        List<LibrarySong> selected = nonNullSongs(songs);
        if (selected.isEmpty()) {
            return;
        }
        if (playSelectionListener != null) {
            playSelectionListener.accept(selected);
            return;
        }
        playListener.accept(selected.get(0));
        for (int i = 1; i < selected.size(); i++) {
            enqueueListener.accept(selected.get(i));
        }
    }

    void enqueueSongs(List<LibrarySong> songs) {
        List<LibrarySong> selected = nonNullSongs(songs);
        if (selected.isEmpty()) {
            return;
        }
        if (enqueueSelectionListener != null) {
            enqueueSelectionListener.accept(selected);
            return;
        }
        for (LibrarySong song : selected) {
            enqueueListener.accept(song);
        }
    }

    private static List<LibrarySong> nonNullSongs(List<LibrarySong> songs) {
        if (songs == null || songs.isEmpty()) {
            return List.of();
        }
        List<LibrarySong> copy = new ArrayList<>();
        for (LibrarySong song : songs) {
            if (song != null) {
                copy.add(song);
            }
        }
        return List.copyOf(copy);
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
            case LibraryTableModel.COL_HISTORY ->
                    songFields.handleHistory(table, song, xInCell, cellWidth);
            case LibraryTableModel.COL_RATING -> songFields.handleRating(song, xInCell, cellWidth);
            case LibraryTableModel.COL_SET -> songFields.handleSet(table, song, screenX, screenY);
            case LibraryTableModel.COL_STATUS -> songFields.handleStatus(table, song, screenX, screenY);
            case LibraryTableModel.COL_EDIT -> editSongListener.accept(song);
            default -> {
            }
        }
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
                        + "Try <b>Clear Filters</b>, choose the <b>None</b> preset, or pick another library filter preset."
                        + "</div></html>",
                JLabel.CENTER);
        message.setFont(message.getFont().deriveFont(Font.PLAIN, 14f));
        JButton openGuide = new JButton("Open User Guide");
        openGuide.addActionListener(e -> openUserGuideListener.run());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER));
        south.add(openGuide);
        empty.add(message, BorderLayout.CENTER);
        empty.add(south, BorderLayout.SOUTH);
        return empty;
    }

    private JPanel buildFilterBar() {
        JPanel mainRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filtersButton.setFocusable(false);
        filtersButton.setHorizontalAlignment(SwingConstants.CENTER);
        filtersButton.setHorizontalTextPosition(SwingConstants.RIGHT);
        filtersButton.setIconTextGap(6);
        filtersButton.setMargin(new Insets(4, 10, 4, 12));
        filtersButton.addActionListener(e -> setFiltersPaneVisible(filtersButton.isSelected()));
        mainRow.add(filtersButton);
        mainRow.add(new JLabel("Title / Composer"));
        TextFieldClearButton.install(titleComposerField);
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

        presetCombo.setPrototypeDisplayValue(LibraryFilterPreset.MODIFIED_LABEL);
        presetCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Object shown = index < 0 && presetModified ? LibraryFilterPreset.MODIFIED_LABEL : value;
                return super.getListCellRendererComponent(list, shown, index, isSelected, cellHasFocus);
            }
        });
        presetCombo.addActionListener(e -> {
            if (suppressPresetEvents) {
                return;
            }
            Object selected = presetCombo.getSelectedItem();
            if (selected != null) {
                applyPreset(String.valueOf(selected));
            }
        });
        presetCombo.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                if (suppressPresetEvents || !presetModified) {
                    return;
                }
                Object selected = presetCombo.getSelectedItem();
                if (selected != null && loadedPresetName.equals(String.valueOf(selected))) {
                    applyPreset(loadedPresetName);
                }
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
        rebuildPresetCombo();

        c.gridy = row++;
        c.gridx = 0;
        JPanel presetRow = new JPanel(new BorderLayout(6, 0));
        JButton savePreset = new JButton("Save To...");
        savePreset.addActionListener(e -> showSavePresetDialog());
        presetRow.add(presetCombo, BorderLayout.CENTER);
        presetRow.add(savePreset, BorderLayout.EAST);
        moreFiltersPanel.add(presetRow, c);

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
        refreshPresetModified();
        filterListener.accept(currentFilter());
    }

    private void applyPreset(String name) {
        String canonical = canonicalPresetName(name);
        LibraryFilter filter;
        if (LibraryFilterPreset.NONE_NAME.equals(canonical)) {
            filter = LibraryFilter.cleared();
        } else {
            filter = filtersFor(canonical).toLibraryFilter();
            filter.setTitleOrComposer(titleComposerField.getText());
        }
        suppressPresetEvents = true;
        try {
            applyFilterState(filter);
            presetCombo.setSelectedItem(canonical);
            loadedPresetName = canonical;
            loadedSnapshot = DefaultFilters.fromLibraryFilter(currentFilter());
            presetModified = false;
        } finally {
            suppressPresetEvents = false;
        }
        presetCombo.repaint();
        notifyFilterChanged();
    }

    private void showSavePresetDialog() {
        List<String> names = new ArrayList<>();
        names.add(LibraryFilterPreset.DEFAULT_NAME);
        for (LibraryFilterPreset preset : filterPresets) {
            names.add(preset.name());
        }
        FilterPresetSaveDialog dialog = new FilterPresetSaveDialog(
                SwingUtilities.getWindowAncestor(this), names);
        dialog.setVisible(true);
        if (dialog.wasSaved()) {
            savePresetAs(dialog.targetName());
        }
    }

    private void savePresetAs(String targetName) {
        DefaultFilters captured = DefaultFilters.fromLibraryFilter(currentFilter());
        if (LibraryFilterPreset.DEFAULT_NAME.equalsIgnoreCase(targetName)) {
            defaultFilters = captured;
            loadedPresetName = LibraryFilterPreset.DEFAULT_NAME;
        } else {
            String normalized = LibraryFilterPreset.normalize(targetName);
            List<LibraryFilterPreset> next = new ArrayList<>(filterPresets);
            int existingIndex = -1;
            for (int i = 0; i < next.size(); i++) {
                if (next.get(i).name().equalsIgnoreCase(normalized)) {
                    existingIndex = i;
                    break;
                }
            }
            if (existingIndex < 0) {
                next.add(new LibraryFilterPreset(normalized, captured));
                loadedPresetName = normalized;
            } else {
                LibraryFilterPreset existing = next.get(existingIndex);
                next.set(existingIndex, existing.withFilters(captured));
                loadedPresetName = existing.name();
            }
            filterPresets = LibraryFilterPreset.sanitize(next);
        }
        loadedSnapshot = captured.copy();
        presetModified = false;
        rebuildPresetCombo();
        filterPresetsListener.accept(defaultFilters.copy(), List.copyOf(filterPresets));
    }

    private void rebuildPresetCombo() {
        suppressPresetEvents = true;
        try {
            presetCombo.removeAllItems();
            presetCombo.addItem(LibraryFilterPreset.DEFAULT_NAME);
            presetCombo.addItem(LibraryFilterPreset.NONE_NAME);
            for (LibraryFilterPreset preset : filterPresets) {
                presetCombo.addItem(preset.name());
            }
            presetCombo.setSelectedItem(loadedPresetName);
            if (presetCombo.getSelectedItem() == null) {
                loadedPresetName = LibraryFilterPreset.DEFAULT_NAME;
                presetCombo.setSelectedItem(loadedPresetName);
            }
        } finally {
            suppressPresetEvents = false;
        }
        presetCombo.repaint();
    }

    private void refreshPresetModified() {
        boolean modified = !DefaultFilters.fromLibraryFilter(currentFilter()).equals(loadedSnapshot);
        if (modified != presetModified) {
            presetModified = modified;
            presetCombo.repaint();
        }
    }

    private boolean presetStillAvailable(String name) {
        if (name == null) {
            return false;
        }
        if (LibraryFilterPreset.DEFAULT_NAME.equalsIgnoreCase(name)
                || LibraryFilterPreset.NONE_NAME.equalsIgnoreCase(name)) {
            return true;
        }
        return findPreset(name) != null;
    }

    private String canonicalPresetName(String name) {
        if (name != null && LibraryFilterPreset.NONE_NAME.equalsIgnoreCase(name.trim())) {
            return LibraryFilterPreset.NONE_NAME;
        }
        if (name == null || LibraryFilterPreset.DEFAULT_NAME.equalsIgnoreCase(name.trim())) {
            return LibraryFilterPreset.DEFAULT_NAME;
        }
        LibraryFilterPreset preset = findPreset(name);
        return preset == null ? LibraryFilterPreset.DEFAULT_NAME : preset.name();
    }

    private DefaultFilters filtersFor(String canonicalName) {
        if (LibraryFilterPreset.DEFAULT_NAME.equals(canonicalName)) {
            return defaultFilters;
        }
        LibraryFilterPreset preset = findPreset(canonicalName);
        return preset == null ? defaultFilters : preset.filters();
    }

    private LibraryFilterPreset findPreset(String name) {
        if (name == null) {
            return null;
        }
        for (LibraryFilterPreset preset : filterPresets) {
            if (preset.name().equalsIgnoreCase(name.trim())) {
                return preset;
            }
        }
        return null;
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

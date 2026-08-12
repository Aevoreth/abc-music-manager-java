package com.aevoreth.abcmm.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;

import com.aevoreth.abcmm.domain.band.BandInfo;
import com.aevoreth.abcmm.domain.band.BandLayoutInfo;
import com.aevoreth.abcmm.domain.band.BandLayoutSlotInfo;
import com.aevoreth.abcmm.domain.band.BandRepository;
import com.aevoreth.abcmm.domain.band.InstrumentInfo;
import com.aevoreth.abcmm.domain.band.LotroInstrumentDefaults;
import com.aevoreth.abcmm.domain.band.PlayerInstrumentInfo;
import com.aevoreth.abcmm.domain.band.PlayerRepository;
import com.aevoreth.abcmm.domain.band.SongLayoutAssignmentInfo;
import com.aevoreth.abcmm.domain.band.SongLayoutInfo;
import com.aevoreth.abcmm.domain.band.SongLayoutRepository;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.scan.AbcPartMetadata;

/**
 * Library song-layout editor: one part-assignment layout per band, persisted on the song.
 */
final class SongLayoutsPanel extends JPanel {

    private static final int UNIT_SIZE = BandLayoutGridPanel.UNIT_SIZE;
    private static final Color CANVAS_BG = Color.BLACK;
    private static final Color GRID_DOT = new Color(0x3A3A3A);
    private static final Color CARD_BORDER = new Color(0x777777);
    private static final Color TEXT = Color.WHITE;
    private static final Color DUP_RED = new Color(0xFF4444);
    private static final Color WARN_ORANGE = new Color(0xD48A3A);
    private static final Color CURRENT_GREEN = new Color(0x4CAF50);
    private static final float MENU_FONT_SIZE = 14f;

    private final BandRepository bandRepository;
    private final PlayerRepository playerRepository;
    private final SongLayoutRepository songLayoutRepository;
    private final long songId;

    private final DefaultListModel<LayoutRow> listModel = new DefaultListModel<>();
    private final JList<LayoutRow> layoutList = new JList<>(listModel);
    private final JButton addButton = new JButton("Add layout…");
    private final JButton deleteButton = new JButton("Delete");
    private final JLabel hintLabel = new JLabel();
    private final AssignmentCanvas canvas = new AssignmentCanvas();
    private final List<AssignmentCard> cards = new ArrayList<>();
    private final List<AbcPartMetadata> parts = new ArrayList<>();
    private final Map<Integer, Long> partToPlayer = new HashMap<>();

    private Long selectedLayoutId;
    private double panX;
    private double panY;
    private boolean panning;
    private Point panStart;

    SongLayoutsPanel(
            BandRepository bandRepository,
            PlayerRepository playerRepository,
            SongLayoutRepository songLayoutRepository,
            long songId) {
        super(new BorderLayout(8, 8));
        this.bandRepository = bandRepository;
        this.playerRepository = playerRepository;
        this.songLayoutRepository = songLayoutRepository;
        this.songId = songId;

        layoutList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        layoutList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value == null ? "" : value.bandName());
            label.setOpaque(true);
            if (isSelected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            } else {
                label.setBackground(list.getBackground());
                label.setForeground(list.getForeground());
            }
            label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            return label;
        });
        layoutList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                LayoutRow row = layoutList.getSelectedValue();
                selectedLayoutId = row == null ? null : row.layout().id();
                deleteButton.setEnabled(row != null);
                reloadGrid();
            }
        });

        addButton.addActionListener(e -> addLayout());
        deleteButton.addActionListener(e -> deleteSelectedLayout());
        deleteButton.setEnabled(false);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(addButton);
        buttons.add(deleteButton);

        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.add(new JLabel("Layouts (one per band)"), BorderLayout.NORTH);
        left.add(new JScrollPane(layoutList), BorderLayout.CENTER);
        left.add(buttons, BorderLayout.SOUTH);
        left.setPreferredSize(new Dimension(200, 240));

        hintLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 4, 2));
        JButton recenter = new JButton("Re-center");
        recenter.addActionListener(e -> fitCardsToView());
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.add(hintLabel, BorderLayout.CENTER);
        toolbar.add(recenter, BorderLayout.EAST);

        canvas.setPreferredSize(new Dimension(480, 280));
        ToolTipManager.sharedInstance().registerComponent(canvas);

        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.setBorder(BorderFactory.createTitledBorder("Part assignments"));
        right.add(toolbar, BorderLayout.NORTH);
        right.add(canvas, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.28);
        add(split, BorderLayout.CENTER);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) && !SwingUtilities.isRightMouseButton(e)) {
                    return;
                }
                AssignmentCard hit = findCardAt(e.getX(), e.getY());
                if (hit != null) {
                    showPartMenu(hit, e.getX(), e.getY());
                    return;
                }
                if (SwingUtilities.isLeftMouseButton(e)) {
                    panning = true;
                    panStart = e.getPoint();
                    canvas.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!panning || panStart == null) {
                    return;
                }
                panX -= (e.getX() - panStart.x) / (double) UNIT_SIZE;
                panY -= (e.getY() - panStart.y) / (double) UNIT_SIZE;
                panStart = e.getPoint();
                canvas.repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (panning) {
                    panning = false;
                    panStart = null;
                    canvas.setCursor(Cursor.getDefaultCursor());
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                AssignmentCard hit = findCardAt(e.getX(), e.getY());
                canvas.setCursor(hit != null
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);

        boolean ready = bandRepository != null && songLayoutRepository != null;
        addButton.setEnabled(ready);
        if (!ready) {
            hintLabel.setText("Band and layout data are not available.");
            canvas.setVisible(false);
        }
    }

    void setParts(List<AbcPartMetadata> songParts) {
        parts.clear();
        if (songParts != null) {
            parts.addAll(songParts);
        }
        reloadList();
    }

    private void reloadList() {
        if (songLayoutRepository == null || bandRepository == null) {
            return;
        }
        Long keepId = selectedLayoutId;
        listModel.clear();
        try {
            Map<Long, String> bandNames = bandNameByLayoutId();
            for (SongLayoutInfo layout : songLayoutRepository.listSongLayouts(songId)) {
                String bandName = bandNames.getOrDefault(
                        layout.bandLayoutId(),
                        layout.name() == null || layout.name().isBlank()
                                ? ("Layout " + layout.id())
                                : layout.name());
                listModel.addElement(new LayoutRow(layout, bandName));
            }
        } catch (LibraryException ex) {
            hintLabel.setText(ex.getMessage());
            return;
        }
        if (listModel.isEmpty()) {
            selectedLayoutId = null;
            deleteButton.setEnabled(false);
            reloadGrid();
            return;
        }
        int select = 0;
        if (keepId != null) {
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.get(i).layout().id() == keepId) {
                    select = i;
                    break;
                }
            }
        }
        layoutList.setSelectedIndex(select);
    }

    private Map<Long, String> bandNameByLayoutId() throws LibraryException {
        Map<Long, String> names = new HashMap<>();
        for (BandInfo band : bandRepository.listBands()) {
            for (BandLayoutInfo layout : bandRepository.listLayouts(band.id())) {
                names.put(layout.id(), band.name());
            }
        }
        return names;
    }

    private void addLayout() {
        if (bandRepository == null || songLayoutRepository == null) {
            return;
        }
        try {
            Set<Long> usedLayoutIds = new HashSet<>();
            for (SongLayoutInfo layout : songLayoutRepository.listSongLayouts(songId)) {
                usedLayoutIds.add(layout.bandLayoutId());
            }
            List<BandInfo> available = new ArrayList<>();
            for (BandInfo band : bandRepository.listBands()) {
                boolean used = false;
                for (BandLayoutInfo layout : bandRepository.listLayouts(band.id())) {
                    if (usedLayoutIds.contains(layout.id())) {
                        used = true;
                        break;
                    }
                }
                if (!used) {
                    available.add(band);
                }
            }
            if (available.isEmpty()) {
                if (bandRepository.listBands().isEmpty()) {
                    JOptionPane.showMessageDialog(
                            this,
                            "Create a band on the Bands tab before adding a song layout.",
                            "Add layout",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(
                            this,
                            "This song already has a layout for every band.",
                            "Add layout",
                            JOptionPane.INFORMATION_MESSAGE);
                }
                return;
            }
            JComboBox<BandInfo> combo = new JComboBox<>(available.toArray(BandInfo[]::new));
            combo.setRenderer(new DefaultListCellRenderer() {
                @Override
                public java.awt.Component getListCellRendererComponent(
                        JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof BandInfo band) {
                        setText(band.name());
                    }
                    return this;
                }
            });
            int result = JOptionPane.showConfirmDialog(
                    this,
                    combo,
                    "Choose a band",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return;
            }
            BandInfo band = (BandInfo) combo.getSelectedItem();
            if (band == null) {
                return;
            }
            BandLayoutInfo bandLayout = bandRepository.getOrCreatePrimaryLayout(band.id());
            SongLayoutInfo created = songLayoutRepository.getOrCreateSongLayout(
                    songId, bandLayout.id(), band.name());
            if (songLayoutRepository.listAssignments(created.id()).isEmpty()) {
                for (BandLayoutSlotInfo slot : bandRepository.listSlots(bandLayout.id())) {
                    songLayoutRepository.setAssignment(created.id(), slot.playerId(), null);
                }
            }
            selectedLayoutId = created.id();
            reloadList();
        } catch (LibraryException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Add layout", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedLayout() {
        LayoutRow row = layoutList.getSelectedValue();
        if (row == null || songLayoutRepository == null) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Delete the layout for " + row.bandName() + "?\n"
                        + "Setlists that already imported this layout keep their own copy.",
                "Delete layout",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            songLayoutRepository.deleteSongLayout(row.layout().id());
            selectedLayoutId = null;
            reloadList();
        } catch (LibraryException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Delete layout", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void reloadGrid() {
        cards.clear();
        partToPlayer.clear();
        if (selectedLayoutId == null || bandRepository == null || songLayoutRepository == null) {
            hintLabel.setText(listModel.isEmpty()
                    ? "Add a layout for a band, then click player cards to assign parts."
                    : "Select a layout to edit part assignments.");
            canvas.setVisible(false);
            revalidate();
            repaint();
            return;
        }
        try {
            SongLayoutInfo layout = null;
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.get(i).layout().id() == selectedLayoutId) {
                    layout = listModel.get(i).layout();
                    break;
                }
            }
            if (layout == null) {
                canvas.setVisible(false);
                return;
            }
            List<BandLayoutSlotInfo> slots = bandRepository.listSlots(layout.bandLayoutId());
            if (slots.isEmpty()) {
                hintLabel.setText("This band layout has no players on the grid.");
                canvas.setVisible(false);
                revalidate();
                repaint();
                return;
            }

            Map<Integer, AbcPartMetadata> partsByNum = new HashMap<>();
            for (AbcPartMetadata part : parts) {
                partsByNum.put(part.partNumber(), part);
            }
            Map<Long, Integer> assigns = new HashMap<>();
            for (SongLayoutAssignmentInfo a : songLayoutRepository.listAssignments(selectedLayoutId)) {
                assigns.put(a.playerId(), a.partNumber());
            }
            Map<Long, Set<Long>> owned = loadOwnedInstruments(slots);
            Map<Long, String> instrumentNames = loadInstrumentNames();

            Map<Integer, Integer> partCounts = new HashMap<>();
            for (Map.Entry<Long, Integer> entry : assigns.entrySet()) {
                if (entry.getValue() != null) {
                    partCounts.merge(entry.getValue(), 1, Integer::sum);
                    partToPlayer.put(entry.getValue(), entry.getKey());
                }
            }
            Set<Integer> duplicated = new HashSet<>();
            for (Map.Entry<Integer, Integer> entry : partCounts.entrySet()) {
                if (entry.getValue() > 1) {
                    duplicated.add(entry.getKey());
                }
            }

            hintLabel.setText("Click a card to assign a part. Changes are saved immediately.");
            canvas.setVisible(true);

            for (BandLayoutSlotInfo slot : slots) {
                Integer eff = assigns.get(slot.playerId());
                boolean partDup = eff != null && duplicated.contains(eff);
                String pn = eff == null ? "—" : String.valueOf(eff);
                String pname = "—";
                String iname = "—";
                boolean instWarn = false;
                if (eff != null && partsByNum.containsKey(eff)) {
                    AbcPartMetadata meta = partsByNum.get(eff);
                    pname = meta.partName() == null || meta.partName().isBlank()
                            ? ("Part " + eff)
                            : meta.partName();
                    if (meta.instrumentId() != null) {
                        iname = LotroInstrumentDefaults.uiName(
                                instrumentNames.getOrDefault(meta.instrumentId(), "—"));
                        if (iname.isBlank()) {
                            iname = "—";
                        }
                        Set<Long> playerOwned = owned.getOrDefault(slot.playerId(), Set.of());
                        instWarn = !playerOwned.contains(meta.instrumentId());
                    }
                }
                cards.add(new AssignmentCard(
                        slot.playerId(),
                        slot.playerName() == null || slot.playerName().isBlank()
                                ? ("#" + slot.playerId())
                                : slot.playerName(),
                        slot.x(),
                        slot.y(),
                        Math.max(1, slot.widthUnits()),
                        Math.max(1, slot.heightUnits()),
                        pn,
                        pname,
                        iname,
                        instWarn,
                        partDup));
            }
            fitCardsToView();
        } catch (LibraryException ex) {
            hintLabel.setText(ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "Failed to load layout."
                    : ex.getMessage());
            canvas.setVisible(false);
        }
        revalidate();
        repaint();
    }

    private void showPartMenu(AssignmentCard card, int x, int y) {
        Integer current;
        try {
            if ("—".equals(card.partNumber()) || card.partNumber().isBlank()) {
                current = null;
            } else {
                current = Integer.parseInt(card.partNumber());
            }
        } catch (NumberFormatException ex) {
            current = null;
        }
        JPopupMenu menu = new JPopupMenu();
        Font menuFont = getFont().deriveFont(Font.PLAIN, MENU_FONT_SIZE);
        JMenuItem none = new JMenuItem("(None)");
        none.setFont(menuFont);
        if (current == null) {
            none.setForeground(CURRENT_GREEN);
        }
        none.addActionListener(e -> applyPart(card.playerId(), null));
        menu.add(none);

        List<AbcPartMetadata> sorted = new ArrayList<>(parts);
        sorted.sort((a, b) -> Integer.compare(a.partNumber(), b.partNumber()));
        Map<Long, String> instrumentNames;
        try {
            instrumentNames = loadInstrumentNames();
        } catch (LibraryException ex) {
            instrumentNames = Map.of();
        }
        for (AbcPartMetadata part : sorted) {
            int pn = part.partNumber();
            String pname = part.partName() == null || part.partName().isBlank()
                    ? ("Part " + pn)
                    : part.partName();
            String iname = "—";
            if (part.instrumentId() != null) {
                iname = LotroInstrumentDefaults.uiName(
                        instrumentNames.getOrDefault(part.instrumentId(), "—"));
                if (iname.isBlank()) {
                    iname = "—";
                }
            }
            JMenuItem item = new JMenuItem("#" + pn + " — " + pname + " — " + iname);
            item.setFont(menuFont);
            Long other = partToPlayer.get(pn);
            boolean taken = other != null && other != card.playerId();
            if (Objects.equals(pn, current)) {
                item.setForeground(CURRENT_GREEN);
            } else if (taken) {
                item.setForeground(DUP_RED);
            }
            item.addActionListener(e -> applyPart(card.playerId(), pn));
            menu.add(item);
        }
        menu.show(canvas, x, y);
    }

    private void applyPart(long playerId, Integer partNumber) {
        if (selectedLayoutId == null || songLayoutRepository == null) {
            return;
        }
        try {
            songLayoutRepository.setAssignment(selectedLayoutId, playerId, partNumber);
            reloadGrid();
        } catch (LibraryException ex) {
            hintLabel.setText(ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "Failed to save assignment."
                    : ex.getMessage());
        }
    }

    private Map<Long, Set<Long>> loadOwnedInstruments(List<BandLayoutSlotInfo> slots)
            throws LibraryException {
        Map<Long, Set<Long>> result = new HashMap<>();
        if (playerRepository == null) {
            return result;
        }
        for (BandLayoutSlotInfo slot : slots) {
            Set<Long> owned = new HashSet<>();
            for (PlayerInstrumentInfo info : playerRepository.listPlayerInstruments(slot.playerId())) {
                if (info.hasInstrument()) {
                    owned.add(info.instrumentId());
                }
            }
            result.put(slot.playerId(), owned);
        }
        return result;
    }

    private Map<Long, String> loadInstrumentNames() throws LibraryException {
        Map<Long, String> names = new HashMap<>();
        if (playerRepository == null) {
            return names;
        }
        for (InstrumentInfo instrument : playerRepository.listInstruments()) {
            names.put(instrument.id(), LotroInstrumentDefaults.uiName(instrument.name()));
        }
        return names;
    }

    private void fitCardsToView() {
        if (cards.isEmpty()) {
            panX = 0;
            panY = 0;
            canvas.repaint();
            return;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (AssignmentCard card : cards) {
            minX = Math.min(minX, card.x());
            maxX = Math.max(maxX, card.x() + card.widthUnits());
            minY = Math.min(minY, card.y());
            maxY = Math.max(maxY, card.y() + card.heightUnits());
        }
        panX = (minX + maxX) / 2.0;
        panY = (minY + maxY) / 2.0;
        canvas.repaint();
    }

    private Point2D.Double logicalToView(double lx, double ly) {
        double cx = canvas.getWidth() / 2.0;
        double cy = canvas.getHeight() / 2.0;
        return new Point2D.Double(
                (lx - panX) * UNIT_SIZE + cx,
                (ly - panY) * UNIT_SIZE + cy);
    }

    private Point2D.Double viewToLogical(double vx, double vy) {
        double cx = canvas.getWidth() / 2.0;
        double cy = canvas.getHeight() / 2.0;
        return new Point2D.Double(
                (vx - cx) / UNIT_SIZE + panX,
                (vy - cy) / UNIT_SIZE + panY);
    }

    private AssignmentCard findCardAt(int px, int py) {
        Point2D.Double logical = viewToLogical(px, py);
        for (int i = cards.size() - 1; i >= 0; i--) {
            AssignmentCard card = cards.get(i);
            if (logical.x >= card.x()
                    && logical.x < card.x() + card.widthUnits()
                    && logical.y >= card.y()
                    && logical.y < card.y() + card.heightUnits()) {
                return card;
            }
        }
        return null;
    }

    private Rectangle cardBounds(AssignmentCard card) {
        Point2D.Double topLeft = logicalToView(card.x(), card.y());
        return new Rectangle(
                (int) Math.round(topLeft.x),
                (int) Math.round(topLeft.y),
                card.widthUnits() * UNIT_SIZE,
                card.heightUnits() * UNIT_SIZE);
    }

    private static void drawFitting(
            Graphics2D g2, Font base, String text, int x, int y, int width, int height, Color color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int size = base.getSize();
        FontMetrics fm;
        Font font = base;
        while (size >= 6) {
            font = base.deriveFont((float) size);
            fm = g2.getFontMetrics(font);
            if (fm.stringWidth(text) <= width) {
                break;
            }
            size--;
        }
        g2.setFont(font);
        g2.setColor(color);
        fm = g2.getFontMetrics();
        int tx = x + Math.max(0, (width - fm.stringWidth(text)) / 2);
        int ty = y + (height + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(text, tx, ty);
    }

    private static Color cardFill() {
        Color bg = UIManager.getColor("Panel.background");
        return bg != null ? bg : new Color(0x2B2B2B);
    }

    private final class AssignmentCanvas extends JPanel {
        AssignmentCanvas() {
            setBackground(CANVAS_BG);
            setOpaque(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g2.setColor(GRID_DOT);
            Point2D.Double topLeft = viewToLogical(0, 0);
            Point2D.Double bottomRight = viewToLogical(getWidth(), getHeight());
            int lxMin = (int) Math.floor(topLeft.x);
            int lxMax = (int) Math.ceil(bottomRight.x) + 1;
            int lyMin = (int) Math.floor(topLeft.y);
            int lyMax = (int) Math.ceil(bottomRight.y) + 1;
            for (int lx = lxMin; lx <= lxMax; lx++) {
                for (int ly = lyMin; ly <= lyMax; ly++) {
                    Point2D.Double view = logicalToView(lx, ly);
                    int vx = (int) Math.round(view.x);
                    int vy = (int) Math.round(view.y);
                    if (vx >= 0 && vx < getWidth() && vy >= 0 && vy < getHeight()) {
                        g2.fillRect(vx, vy, 1, 1);
                    }
                }
            }

            Font baseFont = getFont().deriveFont(Font.PLAIN, 13f);
            final int lineGap = 1;
            Color fill = cardFill();
            for (AssignmentCard card : cards) {
                Rectangle r = cardBounds(card);
                g2.setColor(fill);
                g2.fillRoundRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2, 6, 6);
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(CARD_BORDER);
                g2.drawRoundRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2, 6, 6);

                int margin = 3;
                int innerX = r.x + margin;
                int innerW = r.width - 2 * margin;
                int y = r.y + margin;
                FontMetrics fm = g2.getFontMetrics(baseFont);
                int lineH = fm.getAscent() + fm.getDescent();
                drawFitting(g2, baseFont, card.playerName(), innerX, y, innerW, lineH, TEXT);
                y += lineH + lineGap;

                Font big = baseFont.deriveFont(Font.BOLD, baseFont.getSize2D() + 16f);
                FontMetrics bigFm = g2.getFontMetrics(big);
                int bigH = bigFm.getAscent() + bigFm.getDescent();
                Color partColor = card.partDuplicate() ? DUP_RED : TEXT;
                drawFitting(g2, big, card.partNumber(), innerX, y, innerW, bigH, partColor);
                y += bigH + lineGap;

                Color instColor = card.partDuplicate()
                        ? DUP_RED
                        : (card.instrumentWarning() ? WARN_ORANGE : TEXT);
                drawFitting(g2, baseFont, card.instrumentName(), innerX, y, innerW, lineH, instColor);
                y += lineH + lineGap;

                Font small = baseFont.deriveFont(Math.max(10f, baseFont.getSize2D() - 1f));
                Color nameColor = card.partDuplicate() ? DUP_RED : TEXT;
                int remaining = Math.max(lineH, r.y + r.height - margin - y);
                drawFitting(g2, small, card.partName(), innerX, y, innerW, remaining, nameColor);
            }
            g2.dispose();
        }
    }

    private record LayoutRow(SongLayoutInfo layout, String bandName) {
    }

    private record AssignmentCard(
            long playerId,
            String playerName,
            int x,
            int y,
            int widthUnits,
            int heightUnits,
            String partNumber,
            String partName,
            String instrumentName,
            boolean instrumentWarning,
            boolean partDuplicate) {
    }
}

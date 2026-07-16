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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongConsumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import com.aevoreth.abcmm.domain.band.BandLayoutSlotInfo;
import com.aevoreth.abcmm.domain.band.BandRepository;
import com.aevoreth.abcmm.domain.band.PlayerInfo;
import com.aevoreth.abcmm.domain.band.PlayerRepository;
import com.aevoreth.abcmm.domain.library.LibraryException;

/**
 * Visual grid editor for a band's primary layout.
 * Matches the Python band layout grid: origin-centered viewport, empty-canvas pan,
 * Re-center, and snapped player cards (default 9×7 @ 15px/unit).
 */
public final class BandLayoutGridPanel extends JPanel {

    static final int UNIT_SIZE = 15;
    static final int DEFAULT_WIDTH_UNITS = 9;
    static final int DEFAULT_HEIGHT_UNITS = 7;
    static final int MAX_CARDS = 24;
    static final int X_MIN = -145;
    static final int X_MAX = 145;
    static final int Y_MIN = -105;
    static final int Y_MAX = 105;
    static final int SPAWN_X = -4;
    static final int SPAWN_Y = -3;

    private BandRepository bandRepository;
    private PlayerRepository playerRepository;
    private Long layoutId;
    private Long bandId;
    private final List<BandLayoutSlotInfo> slots = new ArrayList<>();
    private Long selectedPlayerId;
    private final GridCanvas canvas = new GridCanvas();

    /** Logical point shown at the view center (Python {@code _pan_x/_pan_y}). */
    private double panX;
    private double panY;

    private Point dragOrigin;
    private int dragStartX;
    private int dragStartY;
    private int dragCurrentX;
    private int dragCurrentY;
    private boolean draggingCard;
    private boolean panning;
    private Point panStart;
    private LongConsumer editPlayerHandler;

    public BandLayoutGridPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createTitledBorder("Band Layout Grid"));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton addPlayer = new JButton("Add player");
        JButton recenter = new JButton("Re-center");
        JButton deleteSlot = new JButton("Delete selected");
        addPlayer.addActionListener(e -> addPlayerToLayout());
        recenter.addActionListener(e -> fitCardsToView());
        deleteSlot.addActionListener(e -> deleteSelectedSlot());
        toolbar.add(addPlayer);
        toolbar.add(recenter);
        toolbar.add(deleteSlot);
        toolbar.add(new JLabel("Drag card · pan empty · right-click card"));

        canvas.setPreferredSize(new Dimension(480, 320));
        add(toolbar, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showCardContextMenu(e);
                    return;
                }
                if (!SwingUtilities.isLeftMouseButton(e) || layoutId == null) {
                    return;
                }
                BandLayoutSlotInfo hit = findSlotAt(e.getX(), e.getY());
                if (hit == null) {
                    selectedPlayerId = null;
                    draggingCard = false;
                    dragOrigin = null;
                    panning = true;
                    panStart = e.getPoint();
                    canvas.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    canvas.repaint();
                    return;
                }
                selectedPlayerId = hit.playerId();
                dragOrigin = e.getPoint();
                dragStartX = hit.x();
                dragStartY = hit.y();
                dragCurrentX = hit.x();
                dragCurrentY = hit.y();
                draggingCard = false;
                panning = false;
                panStart = null;
                canvas.repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (panning && panStart != null) {
                    double dx = (e.getX() - panStart.x) / (double) UNIT_SIZE;
                    double dy = (e.getY() - panStart.y) / (double) UNIT_SIZE;
                    panX -= dx;
                    panY -= dy;
                    panStart = e.getPoint();
                    canvas.repaint();
                    return;
                }
                if (selectedPlayerId == null || dragOrigin == null) {
                    return;
                }
                int dxUnits = (int) Math.round((e.getX() - dragOrigin.x) / (double) UNIT_SIZE);
                int dyUnits = (int) Math.round((e.getY() - dragOrigin.y) / (double) UNIT_SIZE);
                Point clamped = clampPosition(dragStartX + dxUnits, dragStartY + dyUnits);
                dragCurrentX = clamped.x;
                dragCurrentY = clamped.y;
                draggingCard = dxUnits != 0 || dyUnits != 0;
                canvas.repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showCardContextMenu(e);
                    return;
                }
                if (panning) {
                    panning = false;
                    panStart = null;
                    canvas.setCursor(Cursor.getDefaultCursor());
                    canvas.repaint();
                    return;
                }
                if (selectedPlayerId == null || !draggingCard || bandRepository == null || layoutId == null) {
                    draggingCard = false;
                    dragOrigin = null;
                    canvas.repaint();
                    return;
                }
                BandLayoutSlotInfo slot = findSlotByPlayer(selectedPlayerId);
                if (slot != null
                        && (slot.x() != dragCurrentX || slot.y() != dragCurrentY)) {
                    try {
                        bandRepository.setSlot(
                                layoutId,
                                selectedPlayerId,
                                dragCurrentX,
                                dragCurrentY,
                                slot.widthUnits(),
                                slot.heightUnits());
                        reload();
                    } catch (LibraryException ex) {
                        showError(ex.getMessage());
                        canvas.repaint();
                    }
                }
                draggingCard = false;
                dragOrigin = null;
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);
    }

    public void setEditPlayerHandler(LongConsumer editPlayerHandler) {
        this.editPlayerHandler = editPlayerHandler;
    }

    public void setBandRepository(BandRepository bandRepository) {
        this.bandRepository = bandRepository;
    }

    public void setPlayerRepository(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    /**
     * Sets the layout to edit. Pass {@code bandId} so membership can sync when cards change.
     */
    public void setLayoutId(Long layoutId, Long bandId) {
        this.layoutId = layoutId;
        this.bandId = bandId;
        selectedPlayerId = null;
        panX = 0;
        panY = 0;
        reload();
    }

    public void reload() {
        slots.clear();
        if (bandRepository == null || layoutId == null) {
            canvas.repaint();
            return;
        }
        try {
            slots.addAll(bandRepository.listSlots(layoutId));
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
        canvas.repaint();
    }

    /**
     * Pan so the bounding box of all cards is centered in the view.
     * With no cards, resets pan to the logical origin (0, 0).
     */
    private void fitCardsToView() {
        if (slots.isEmpty()) {
            panX = 0;
            panY = 0;
            canvas.repaint();
            return;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (BandLayoutSlotInfo slot : slots) {
            int x = slot.x();
            int y = slot.y();
            if (draggingCard && selectedPlayerId != null && selectedPlayerId == slot.playerId()) {
                x = dragCurrentX;
                y = dragCurrentY;
            }
            int w = Math.max(1, slot.widthUnits());
            int h = Math.max(1, slot.heightUnits());
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x + w);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y + h);
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

    private static Point clampPosition(int x, int y) {
        return new Point(
                Math.max(X_MIN, Math.min(X_MAX, x)),
                Math.max(Y_MIN, Math.min(Y_MAX, y)));
    }

    private void addPlayerToLayout() {
        if (bandRepository == null || layoutId == null || bandId == null) {
            showError("No band selected.");
            return;
        }
        if (slots.size() >= MAX_CARDS) {
            JOptionPane.showMessageDialog(
                    this,
                    "Maximum " + MAX_CARDS + " cards allowed.",
                    "Add Player",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Set<Long> exclude = new HashSet<>();
        for (BandLayoutSlotInfo slot : slots) {
            exclude.add(slot.playerId());
        }
        PlayerInfo chosen = pickPlayer(exclude, "Add Player");
        if (chosen == null) {
            return;
        }
        Point free = findFreeOrigin(DEFAULT_WIDTH_UNITS, DEFAULT_HEIGHT_UNITS);
        try {
            bandRepository.setSlot(
                    layoutId,
                    chosen.id(),
                    free.x,
                    free.y,
                    DEFAULT_WIDTH_UNITS,
                    DEFAULT_HEIGHT_UNITS);
            bandRepository.syncMembersFromPrimaryLayout(bandId);
            selectedPlayerId = chosen.id();
            reload();
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
    }

    private void changePlayerOnCard(long oldPlayerId) {
        if (bandRepository == null || layoutId == null || bandId == null) {
            showError("No band selected.");
            return;
        }
        Set<Long> exclude = new HashSet<>();
        for (BandLayoutSlotInfo slot : slots) {
            if (slot.playerId() != oldPlayerId) {
                exclude.add(slot.playerId());
            }
        }
        PlayerInfo chosen = pickPlayer(exclude, "Change Player");
        if (chosen == null || chosen.id() == oldPlayerId) {
            return;
        }
        try {
            bandRepository.replacePlayerInBandLayout(layoutId, bandId, oldPlayerId, chosen.id());
            selectedPlayerId = chosen.id();
            reload();
        } catch (LibraryException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    ex.getMessage() == null || ex.getMessage().isBlank()
                            ? "Could not change player."
                            : ex.getMessage(),
                    "Change Player",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private void showCardContextMenu(MouseEvent e) {
        if (layoutId == null) {
            return;
        }
        BandLayoutSlotInfo hit = findSlotAt(e.getX(), e.getY());
        if (hit == null) {
            return;
        }
        selectedPlayerId = hit.playerId();
        canvas.repaint();

        JPopupMenu menu = new JPopupMenu();
        JMenuItem edit = new JMenuItem("Edit");
        JMenuItem changePlayer = new JMenuItem("Change Player");
        JMenuItem delete = new JMenuItem("Delete");
        edit.addActionListener(ev -> {
            if (editPlayerHandler != null) {
                editPlayerHandler.accept(hit.playerId());
            }
        });
        changePlayer.addActionListener(ev -> changePlayerOnCard(hit.playerId()));
        delete.addActionListener(ev -> deleteSelectedSlot());
        menu.add(edit);
        menu.add(changePlayer);
        menu.add(delete);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    /**
     * Returns true if any two cards overlap in logical units (edge-touching is allowed).
     */
    public boolean hasAnyOverlap() {
        for (int i = 0; i < slots.size(); i++) {
            BandLayoutSlotInfo a = slots.get(i);
            int ax = a.x();
            int ay = a.y();
            if (draggingCard && selectedPlayerId != null && selectedPlayerId == a.playerId()) {
                ax = dragCurrentX;
                ay = dragCurrentY;
            }
            int aw = Math.max(1, a.widthUnits());
            int ah = Math.max(1, a.heightUnits());
            for (int j = i + 1; j < slots.size(); j++) {
                BandLayoutSlotInfo b = slots.get(j);
                int bx = b.x();
                int by = b.y();
                if (draggingCard && selectedPlayerId != null && selectedPlayerId == b.playerId()) {
                    bx = dragCurrentX;
                    by = dragCurrentY;
                }
                int bw = Math.max(1, b.widthUnits());
                int bh = Math.max(1, b.heightUnits());
                if (!(ax + aw <= bx || bx + bw <= ax || ay + ah <= by || by + bh <= ay)) {
                    return true;
                }
            }
        }
        return false;
    }

    private PlayerInfo pickPlayer(Set<Long> excludePlayerIds, String dialogTitle) {
        if (playerRepository == null) {
            showError("Player repository is not connected.");
            return null;
        }
        try {
            boolean anyAvailable = false;
            for (PlayerInfo player : playerRepository.listPlayers()) {
                if (!excludePlayerIds.contains(player.id())) {
                    anyAvailable = true;
                    break;
                }
            }
            if (!anyAvailable) {
                JOptionPane.showMessageDialog(
                        this,
                        excludePlayerIds.isEmpty()
                                ? "No players exist yet. Create players on the Players tab first."
                                : "No other players are available for this layout.",
                        dialogTitle,
                        JOptionPane.INFORMATION_MESSAGE);
                return null;
            }
        } catch (LibraryException ex) {
            showError(ex.getMessage());
            return null;
        }
        return new PlayerPickerDialog(
                SwingUtilities.getWindowAncestor(this),
                playerRepository,
                excludePlayerIds,
                dialogTitle)
                .showDialog()
                .orElse(null);
    }

    private void deleteSelectedSlot() {
        if (bandRepository == null || layoutId == null || bandId == null || selectedPlayerId == null) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Remove the selected player from this band layout?",
                "Delete slot",
                JOptionPane.OK_CANCEL_OPTION);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            bandRepository.deleteSlot(layoutId, selectedPlayerId);
            bandRepository.syncMembersFromPrimaryLayout(bandId);
            selectedPlayerId = null;
            reload();
        } catch (LibraryException ex) {
            showError(ex.getMessage());
        }
    }

    private Point findFreeOrigin(int widthUnits, int heightUnits) {
        if (!overlapsAny(SPAWN_X, SPAWN_Y, widthUnits, heightUnits, null)) {
            return new Point(SPAWN_X, SPAWN_Y);
        }
        for (int radius = 1; radius <= 40; radius++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.abs(dx) != radius && Math.abs(dy) != radius) {
                        continue;
                    }
                    int x = SPAWN_X + dx;
                    int y = SPAWN_Y + dy;
                    Point clamped = clampPosition(x, y);
                    if (clamped.x != x || clamped.y != y) {
                        continue;
                    }
                    if (!overlapsAny(x, y, widthUnits, heightUnits, null)) {
                        return new Point(x, y);
                    }
                }
            }
        }
        return new Point(SPAWN_X, SPAWN_Y);
    }

    private boolean overlapsAny(int x, int y, int w, int h, Long ignorePlayerId) {
        Rectangle candidate = new Rectangle(x, y, w, h);
        for (BandLayoutSlotInfo slot : slots) {
            if (ignorePlayerId != null && Objects.equals(slot.playerId(), ignorePlayerId)) {
                continue;
            }
            Rectangle existing = new Rectangle(
                    slot.x(), slot.y(),
                    Math.max(1, slot.widthUnits()), Math.max(1, slot.heightUnits()));
            if (candidate.intersects(existing)) {
                return true;
            }
        }
        return false;
    }

    private BandLayoutSlotInfo findSlotAt(int px, int py) {
        Point2D.Double logical = viewToLogical(px, py);
        for (int i = slots.size() - 1; i >= 0; i--) {
            BandLayoutSlotInfo slot = slots.get(i);
            int x = slot.x();
            int y = slot.y();
            if (draggingCard && selectedPlayerId != null && selectedPlayerId == slot.playerId()) {
                x = dragCurrentX;
                y = dragCurrentY;
            }
            int w = Math.max(1, slot.widthUnits());
            int h = Math.max(1, slot.heightUnits());
            if (logical.x >= x && logical.x < x + w && logical.y >= y && logical.y < y + h) {
                return slot;
            }
        }
        return null;
    }

    private BandLayoutSlotInfo findSlotByPlayer(long playerId) {
        for (BandLayoutSlotInfo slot : slots) {
            if (slot.playerId() == playerId) {
                return slot;
            }
        }
        return null;
    }

    private Rectangle slotBounds(BandLayoutSlotInfo slot) {
        int x = slot.x();
        int y = slot.y();
        if (draggingCard && selectedPlayerId != null && selectedPlayerId == slot.playerId()) {
            x = dragCurrentX;
            y = dragCurrentY;
        }
        Point2D.Double topLeft = logicalToView(x, y);
        return new Rectangle(
                (int) Math.round(topLeft.x),
                (int) Math.round(topLeft.y),
                Math.max(1, slot.widthUnits()) * UNIT_SIZE,
                Math.max(1, slot.heightUnits()) * UNIT_SIZE);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(
                this,
                message == null || message.isBlank() ? "Operation failed." : message,
                "Band layout",
                JOptionPane.ERROR_MESSAGE);
    }

    private final class GridCanvas extends JPanel {
        GridCanvas() {
            setBackground(new Color(0x2B2B2B));
            setOpaque(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Dotted graph-paper grid in logical space (origin at view center when pan is 0).
            g2.setColor(new Color(0x3A3A3A));
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

            Font labelFont = getFont().deriveFont(Font.BOLD, 13f);
            g2.setFont(labelFont);
            FontMetrics labelMetrics = g2.getFontMetrics();
            for (BandLayoutSlotInfo slot : slots) {
                Rectangle r = slotBounds(slot);
                boolean selected = selectedPlayerId != null && selectedPlayerId == slot.playerId();
                g2.setColor(selected ? new Color(0x3D5A80) : new Color(0x4A4A4A));
                g2.fillRoundRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2, 6, 6);
                g2.setStroke(new BasicStroke(selected ? 2f : 1f));
                g2.setColor(selected ? new Color(0x98C1D9) : new Color(0x777777));
                g2.drawRoundRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2, 6, 6);
                g2.setColor(Color.WHITE);
                String name = slot.playerName() == null ? ("#" + slot.playerId()) : slot.playerName();
                int nameX = r.x + (r.width - labelMetrics.stringWidth(name)) / 2;
                int nameY = r.y + 4 + labelMetrics.getAscent();
                g2.drawString(name, nameX, nameY);
            }
            g2.dispose();
        }
    }
}

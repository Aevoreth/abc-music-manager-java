package com.aevoreth.abcmm.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import com.aevoreth.abcmm.domain.band.BandLayoutSlotInfo;
import com.aevoreth.abcmm.domain.band.BandRepository;
import com.aevoreth.abcmm.domain.band.PlayerInfo;
import com.aevoreth.abcmm.domain.band.PlayerRepository;
import com.aevoreth.abcmm.domain.library.LibraryException;

/**
 * Visual grid editor for a band's single layout (unit size ~12px, default 7×5).
 * Placing a card assigns that player as a band member.
 */
public final class BandLayoutGridPanel extends JPanel {

    static final int UNIT_SIZE = 12;
    static final int DEFAULT_WIDTH_UNITS = 7;
    static final int DEFAULT_HEIGHT_UNITS = 5;

    private BandRepository bandRepository;
    private PlayerRepository playerRepository;
    private Long layoutId;
    private Long bandId;
    private final List<BandLayoutSlotInfo> slots = new ArrayList<>();
    private Long selectedPlayerId;
    private final GridCanvas canvas = new GridCanvas();

    private Point dragOrigin;
    private int dragStartX;
    private int dragStartY;
    private int dragCurrentX;
    private int dragCurrentY;
    private boolean dragging;

    public BandLayoutGridPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createTitledBorder("Band Layout Grid"));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton addPlayer = new JButton("Add player");
        JButton deleteSlot = new JButton("Delete selected");
        addPlayer.addActionListener(e -> addPlayerToLayout());
        deleteSlot.addActionListener(e -> deleteSelectedSlot());
        toolbar.add(addPlayer);
        toolbar.add(deleteSlot);
        toolbar.add(new JLabel("Click to select · drag to move"));

        canvas.setPreferredSize(new Dimension(480, 320));
        add(toolbar, BorderLayout.NORTH);
        add(new JScrollPane(canvas), BorderLayout.CENTER);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || layoutId == null) {
                    return;
                }
                BandLayoutSlotInfo hit = findSlotAt(e.getX(), e.getY());
                if (hit == null) {
                    selectedPlayerId = null;
                    canvas.repaint();
                    return;
                }
                selectedPlayerId = hit.playerId();
                dragOrigin = e.getPoint();
                dragStartX = hit.x();
                dragStartY = hit.y();
                dragCurrentX = hit.x();
                dragCurrentY = hit.y();
                dragging = false;
                canvas.repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (selectedPlayerId == null || dragOrigin == null) {
                    return;
                }
                int dxUnits = Math.round((e.getX() - dragOrigin.x) / (float) UNIT_SIZE);
                int dyUnits = Math.round((e.getY() - dragOrigin.y) / (float) UNIT_SIZE);
                dragCurrentX = Math.max(0, dragStartX + dxUnits);
                dragCurrentY = Math.max(0, dragStartY + dyUnits);
                dragging = dxUnits != 0 || dyUnits != 0;
                canvas.repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (selectedPlayerId == null || !dragging || bandRepository == null || layoutId == null) {
                    dragging = false;
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
                dragging = false;
                dragOrigin = null;
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);
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
        canvas.revalidate();
        canvas.repaint();
    }

    private void addPlayerToLayout() {
        if (bandRepository == null || layoutId == null || bandId == null) {
            showError("No band selected.");
            return;
        }
        if (playerRepository == null) {
            showError("Player repository is not connected.");
            return;
        }
        Set<Long> placed = new HashSet<>();
        for (BandLayoutSlotInfo slot : slots) {
            placed.add(slot.playerId());
        }
        List<PlayerInfo> available = new ArrayList<>();
        try {
            for (PlayerInfo player : playerRepository.listPlayers()) {
                if (!placed.contains(player.id())) {
                    available.add(player);
                }
            }
        } catch (LibraryException ex) {
            showError(ex.getMessage());
            return;
        }
        if (available.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    placed.isEmpty()
                            ? "No players exist yet. Create players on the Players tab first."
                            : "All players are already on this band layout.",
                    "Add player",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String[] labels = available.stream()
                .map(player -> player.name() == null || player.name().isBlank()
                        ? ("#" + player.id())
                        : player.name())
                .toArray(String[]::new);
        String chosenLabel = (String) JOptionPane.showInputDialog(
                this,
                "Choose a player:",
                "Add player",
                JOptionPane.QUESTION_MESSAGE,
                null,
                labels,
                labels[0]);
        if (chosenLabel == null) {
            return;
        }
        PlayerInfo chosen = null;
        for (int i = 0; i < labels.length; i++) {
            if (chosenLabel.equals(labels[i])) {
                chosen = available.get(i);
                break;
            }
        }
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
        int maxX = 40;
        int maxY = 30;
        for (int y = 0; y < maxY; y++) {
            for (int x = 0; x < maxX; x++) {
                if (!overlapsAny(x, y, widthUnits, heightUnits, null)) {
                    return new Point(x, y);
                }
            }
        }
        return new Point(0, 0);
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
        for (int i = slots.size() - 1; i >= 0; i--) {
            BandLayoutSlotInfo slot = slots.get(i);
            Rectangle r = slotBounds(slot);
            if (r.contains(px, py)) {
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
        if (dragging && selectedPlayerId != null && selectedPlayerId == slot.playerId()) {
            x = dragCurrentX;
            y = dragCurrentY;
        }
        return new Rectangle(
                x * UNIT_SIZE,
                y * UNIT_SIZE,
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
        public Dimension getPreferredSize() {
            int maxX = 20;
            int maxY = 15;
            for (BandLayoutSlotInfo slot : slots) {
                int x = slot.x();
                int y = slot.y();
                if (dragging && selectedPlayerId != null && selectedPlayerId == slot.playerId()) {
                    x = dragCurrentX;
                    y = dragCurrentY;
                }
                maxX = Math.max(maxX, x + Math.max(1, slot.widthUnits()) + 2);
                maxY = Math.max(maxY, y + Math.max(1, slot.heightUnits()) + 2);
            }
            return new Dimension(maxX * UNIT_SIZE, maxY * UNIT_SIZE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(0x3A3A3A));
            for (int x = 0; x < getWidth(); x += UNIT_SIZE) {
                g2.drawLine(x, 0, x, getHeight());
            }
            for (int y = 0; y < getHeight(); y += UNIT_SIZE) {
                g2.drawLine(0, y, getWidth(), y);
            }

            Font labelFont = getFont().deriveFont(Font.BOLD, 11f);
            g2.setFont(labelFont);
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
                g2.drawString(name, r.x + 6, r.y + 16);
            }
            g2.dispose();
        }
    }
}

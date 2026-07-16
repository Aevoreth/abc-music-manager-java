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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

import com.aevoreth.abcmm.domain.band.BandLayoutSlotInfo;
import com.aevoreth.abcmm.domain.band.BandRepository;
import com.aevoreth.abcmm.domain.band.InstrumentInfo;
import com.aevoreth.abcmm.domain.band.LotroInstrumentDefaults;
import com.aevoreth.abcmm.domain.band.PlayerInstrumentInfo;
import com.aevoreth.abcmm.domain.band.PlayerRepository;
import com.aevoreth.abcmm.domain.band.SongLayoutAssignmentInfo;
import com.aevoreth.abcmm.domain.band.SongLayoutRepository;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.setlist.SetlistBandAssignmentInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistItemInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Setlist part-assignment grid: click a band-layout card to assign a part for the
 * selected song. Matches Python {@code SetlistBandAssignmentPanel} — spatial cards,
 * song-layout baseline + setlist overrides, neighbor headers, and instrument warnings.
 */
public final class SetlistBandAssignmentPanel extends JPanel {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int UNIT_SIZE = BandLayoutGridPanel.UNIT_SIZE;
    private static final Color CANVAS_BG = new Color(0x2B2B2B);
    private static final Color GRID_DOT = new Color(0x3A3A3A);
    private static final Color CARD_FILL = new Color(0x4A4A4A);
    private static final Color CARD_BORDER = new Color(0x777777);
    private static final Color TEXT = Color.WHITE;
    private static final Color TEXT_SECONDARY = new Color(0xB4A8A8);
    private static final Color DUP_RED = new Color(0xFF4444);
    private static final Color WARN_ORANGE = new Color(0xD48A3A);
    private static final Color CURRENT_GREEN = new Color(0x4CAF50);

    private BandRepository bandRepository;
    private PlayerRepository playerRepository;
    private SetlistRepository setlistRepository;
    private SongLayoutRepository songLayoutRepository;
    private Runnable assignmentChangedHandler;

    private final JLabel hintLabel = new JLabel();
    private final AssignmentCanvas canvas = new AssignmentCanvas();
    private final List<AssignmentCard> cards = new ArrayList<>();
    private final Map<Long, String> helpByPlayer = new HashMap<>();
    private final List<SongPartMeta> assignmentParts = new ArrayList<>();
    private final Map<Integer, Long> partToPlayer = new HashMap<>();
    private final Map<Long, Integer> layoutPartByPlayer = new HashMap<>();

    private Long itemId;
    private double panX;
    private double panY;
    private boolean panning;
    private Point panStart;

    public SetlistBandAssignmentPanel() {
        super(new BorderLayout(4, 4));
        setBorder(BorderFactory.createTitledBorder("Part assignments"));

        hintLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 4, 2));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton recenter = new JButton("Re-center");
        recenter.addActionListener(e -> fitCardsToView());
        toolbar.add(recenter);

        canvas.setPreferredSize(new Dimension(480, 220));
        ToolTipManager.sharedInstance().registerComponent(canvas);

        JPanel north = new JPanel(new BorderLayout());
        north.add(hintLabel, BorderLayout.CENTER);
        north.add(toolbar, BorderLayout.EAST);

        add(north, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);

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
                double dx = (e.getX() - panStart.x) / (double) UNIT_SIZE;
                double dy = (e.getY() - panStart.y) / (double) UNIT_SIZE;
                panX -= dx;
                panY -= dy;
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
                if (hit != null) {
                    canvas.setToolTipText(helpByPlayer.getOrDefault(hit.playerId(), null));
                    canvas.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else {
                    canvas.setToolTipText(null);
                    canvas.setCursor(Cursor.getDefaultCursor());
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                canvas.setToolTipText(null);
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);
        clear();
    }

    public void bind(
            BandRepository bands,
            PlayerRepository players,
            SetlistRepository setlists,
            SongLayoutRepository songLayouts) {
        this.bandRepository = bands;
        this.playerRepository = players;
        this.setlistRepository = setlists;
        this.songLayoutRepository = songLayouts;
    }

    public void setAssignmentChangedHandler(Runnable handler) {
        this.assignmentChangedHandler = handler;
    }

    public void clear() {
        cards.clear();
        helpByPlayer.clear();
        assignmentParts.clear();
        partToPlayer.clear();
        layoutPartByPlayer.clear();
        itemId = null;
        panX = 0;
        panY = 0;
        hintLabel.setText("Choose a setlist and song to assign parts.");
        canvas.setVisible(false);
        revalidate();
        repaint();
    }

    /**
     * Refresh cards for the selected setlist item. Pass {@code allItems} (ordered) for
     * neighbor prev/next headers and prior-instrument tooltips.
     */
    public void refresh(
            Long bandLayoutId,
            Long setlistItemId,
            Long songLayoutId,
            String partsJson,
            List<SetlistItemInfo> allItems) {
        cards.clear();
        helpByPlayer.clear();
        assignmentParts.clear();
        partToPlayer.clear();
        layoutPartByPlayer.clear();
        itemId = setlistItemId;

        if (bandLayoutId == null) {
            hintLabel.setText("Choose a band layout for this setlist to assign parts per song.");
            canvas.setVisible(false);
            revalidate();
            repaint();
            return;
        }
        if (setlistItemId == null) {
            hintLabel.setText("Select a song in the set list.");
            canvas.setVisible(false);
            revalidate();
            repaint();
            return;
        }
        if (songLayoutId == null) {
            hintLabel.setText(
                    "Select a song in the set list. Song layout will be created when a band layout is set.");
            canvas.setVisible(false);
            revalidate();
            repaint();
            return;
        }
        if (bandRepository == null || setlistRepository == null || songLayoutRepository == null) {
            hintLabel.setText("Repositories are not connected.");
            canvas.setVisible(false);
            revalidate();
            repaint();
            return;
        }

        try {
            List<BandLayoutSlotInfo> slots = bandRepository.listSlots(bandLayoutId);
            if (slots.isEmpty()) {
                hintLabel.setText("The selected band layout has no players on the grid.");
                canvas.setVisible(false);
                revalidate();
                repaint();
                return;
            }

            List<SongPartMeta> parts = parseParts(partsJson);
            assignmentParts.addAll(parts);
            Map<Integer, SongPartMeta> partsByNum = new HashMap<>();
            for (SongPartMeta part : parts) {
                partsByNum.put(part.partNumber(), part);
            }

            Map<Long, Integer> layoutAssigns = new HashMap<>();
            for (SongLayoutAssignmentInfo a : songLayoutRepository.listAssignments(songLayoutId)) {
                layoutAssigns.put(a.playerId(), a.partNumber());
            }
            layoutPartByPlayer.putAll(layoutAssigns);

            Map<Long, Integer> overrides = new HashMap<>();
            Set<Long> overridePlayers = new HashSet<>();
            for (SetlistBandAssignmentInfo a : setlistRepository.listBandAssignments(setlistItemId)) {
                overrides.put(a.playerId(), a.partNumber());
                overridePlayers.add(a.playerId());
            }

            Map<Long, Set<Long>> playerInstruments = loadOwnedInstruments(slots);
            Map<Long, String> instrumentNames = loadInstrumentNames();
            Map<Long, Set<Long>> equivByInstrument = buildEquivalentInstrumentIds(instrumentNames);

            Map<Long, Integer> effective = new HashMap<>();
            for (BandLayoutSlotInfo slot : slots) {
                Integer part = overridePlayers.contains(slot.playerId())
                        ? overrides.get(slot.playerId())
                        : layoutAssigns.get(slot.playerId());
                effective.put(slot.playerId(), part);
                if (part != null) {
                    partToPlayer.put(part, slot.playerId());
                }
            }

            Map<Integer, Integer> partCounts = new HashMap<>();
            for (Integer pnum : effective.values()) {
                if (pnum != null) {
                    partCounts.merge(pnum, 1, Integer::sum);
                }
            }
            Set<Integer> duplicatedParts = new HashSet<>();
            for (Map.Entry<Integer, Integer> entry : partCounts.entrySet()) {
                if (entry.getValue() > 1) {
                    duplicatedParts.add(entry.getKey());
                }
            }

            List<SetlistItemInfo> setlistRows = allItems == null ? List.of() : allItems;
            int setlistIdx = -1;
            for (int i = 0; i < setlistRows.size(); i++) {
                if (setlistRows.get(i).id() == setlistItemId) {
                    setlistIdx = i;
                    break;
                }
            }
            boolean useHeader = setlistIdx >= 0;
            Map<Long, Map<Long, Integer>> bulkOverrides = new HashMap<>();
            Map<Long, Map<Long, Integer>> layoutCache = new HashMap<>();
            if (useHeader) {
                for (SetlistItemInfo row : setlistRows) {
                    Map<Long, Integer> ov = new HashMap<>();
                    for (SetlistBandAssignmentInfo a : setlistRepository.listBandAssignments(row.id())) {
                        ov.put(a.playerId(), a.partNumber());
                    }
                    bulkOverrides.put(row.id(), ov);
                }
            }

            hintLabel.setText(
                    "Click a card to assign a part for this song. Assignments are saved to the setlist.");
            canvas.setVisible(true);

            for (BandLayoutSlotInfo slot : slots) {
                Integer eff = effective.get(slot.playerId());
                boolean partDup = eff != null && duplicatedParts.contains(eff);
                String pn;
                String pname;
                String iname;
                boolean instWarn = false;
                Long iid = null;
                if (eff != null && partsByNum.containsKey(eff)) {
                    SongPartMeta meta = partsByNum.get(eff);
                    pn = String.valueOf(meta.partNumber());
                    pname = meta.partName().isBlank() ? ("Part " + eff) : meta.partName();
                    iid = meta.instrumentId();
                    iname = iid == null
                            ? "—"
                            : LotroInstrumentDefaults.uiName(
                                    instrumentNames.getOrDefault(iid, "—"));
                    if (iname.isBlank()) {
                        iname = "—";
                    }
                    if (iid != null) {
                        Set<Long> equiv = equivByInstrument.getOrDefault(iid, Set.of(iid));
                        Set<Long> owned = playerInstruments.getOrDefault(slot.playerId(), Set.of());
                        boolean hasInst = false;
                        for (Long eq : equiv) {
                            if (owned.contains(eq)) {
                                hasInst = true;
                                break;
                            }
                        }
                        instWarn = !hasInst;
                    }
                } else {
                    pn = "---";
                    pname = "(Part Name)";
                    iname = "(Made for Instrument)";
                }

                String prevL = "";
                String nextL = "";
                boolean instChanged = false;
                if (useHeader) {
                    SetlistItemInfo rowBefore = setlistIdx > 0 ? setlistRows.get(setlistIdx - 1) : null;
                    SetlistItemInfo rowAfter = setlistIdx + 1 < setlistRows.size()
                            ? setlistRows.get(setlistIdx + 1)
                            : null;
                    if (rowBefore != null) {
                        Integer ppn = effectiveFor(
                                rowBefore, slot.playerId(), bulkOverrides, layoutCache);
                        if (ppn != null) {
                            prevL = String.valueOf(ppn);
                        }
                    }
                    if (rowAfter != null) {
                        Integer npn = effectiveFor(
                                rowAfter, slot.playerId(), bulkOverrides, layoutCache);
                        if (npn != null) {
                            nextL = String.valueOf(npn);
                        }
                    }

                    Long priorIid = null;
                    String priorTitle = "";
                    Integer priorPn = null;
                    for (int j = setlistIdx - 1; j >= 0; j--) {
                        SetlistItemInfo back = setlistRows.get(j);
                        Integer bpn = effectiveFor(
                                back, slot.playerId(), bulkOverrides, layoutCache);
                        if (bpn != null) {
                            priorIid = instrumentIdForPart(back.partsJson(), bpn);
                            priorTitle = back.songTitle();
                            priorPn = bpn;
                            break;
                        }
                    }

                    if (!partDup
                            && eff != null
                            && partsByNum.containsKey(eff)
                            && iid != null
                            && priorIid != null) {
                        instChanged = !instrumentsEquivalent(iid, priorIid, equivByInstrument);
                    }

                    List<String> lines = new ArrayList<>();
                    if (priorPn != null && priorTitle != null && !priorTitle.isBlank()) {
                        String piname = priorIid == null
                                ? "—"
                                : LotroInstrumentDefaults.uiName(
                                        instrumentNames.getOrDefault(priorIid, "—"));
                        if (piname.isBlank()) {
                            piname = "—";
                        }
                        lines.add("Last assignment in this set: \"" + priorTitle
                                + "\" — Part " + priorPn + " — " + piname);
                    } else {
                        lines.add("No earlier assignment in this set for this player.");
                    }
                    if (setlistIdx > 0) {
                        lines.add("Previous song in set: " + (prevL.isEmpty() ? "—" : prevL));
                    }
                    if (setlistIdx + 1 < setlistRows.size()) {
                        lines.add("Next song in set: " + (nextL.isEmpty() ? "—" : nextL));
                    }
                    helpByPlayer.put(slot.playerId(), String.join("\n", lines));
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
                        partDup,
                        useHeader,
                        prevL,
                        nextL,
                        instChanged));
            }

            fitCardsToView();
        } catch (LibraryException ex) {
            hintLabel.setText(ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "Failed to load assignments."
                    : ex.getMessage());
            canvas.setVisible(false);
        }
        revalidate();
        repaint();
    }

    private Integer effectiveFor(
            SetlistItemInfo row,
            long playerId,
            Map<Long, Map<Long, Integer>> bulkOverrides,
            Map<Long, Map<Long, Integer>> layoutCache) throws LibraryException {
        Map<Long, Integer> ov = bulkOverrides.getOrDefault(row.id(), Map.of());
        if (ov.containsKey(playerId)) {
            return ov.get(playerId);
        }
        Long slId = row.songLayoutId();
        if (slId == null) {
            return null;
        }
        Map<Long, Integer> layout = layoutCache.get(slId);
        if (layout == null) {
            layout = new HashMap<>();
            for (SongLayoutAssignmentInfo a : songLayoutRepository.listAssignments(slId)) {
                layout.put(a.playerId(), a.partNumber());
            }
            layoutCache.put(slId, layout);
        }
        return layout.get(playerId);
    }

    private void showPartMenu(AssignmentCard card, int x, int y) {
        Integer current;
        try {
            if ("###".equals(card.partNumber())
                    || "---".equals(card.partNumber())
                    || card.partNumber().isBlank()) {
                current = null;
            } else {
                current = Integer.parseInt(card.partNumber());
            }
        } catch (NumberFormatException ex) {
            current = null;
        }

        JPopupMenu menu = new JPopupMenu();
        String help = helpByPlayer.get(card.playerId());
        if (help != null && !help.isBlank()) {
            JMenuItem head = new JMenuItem("<html><body style='width:280px;color:#b4a8a8'>"
                    + help.replace("\n", "<br>") + "</body></html>");
            head.setEnabled(false);
            menu.add(head);
            menu.addSeparator();
        }

        JMenuItem none = new JMenuItem("(None)");
        if (current == null) {
            none.setForeground(CURRENT_GREEN);
        }
        none.addActionListener(e -> applyPart(card.playerId(), null));
        menu.add(none);

        List<SongPartMeta> sorted = new ArrayList<>(assignmentParts);
        sorted.sort((a, b) -> Integer.compare(a.partNumber(), b.partNumber()));
        Map<Long, String> instrumentNames;
        try {
            instrumentNames = loadInstrumentNames();
        } catch (LibraryException ex) {
            instrumentNames = Map.of();
        }
        for (SongPartMeta part : sorted) {
            int pn = part.partNumber();
            String pname = part.partName().isBlank() ? ("Part " + pn) : part.partName();
            String iname = "—";
            if (part.instrumentId() != null) {
                iname = LotroInstrumentDefaults.uiName(
                        instrumentNames.getOrDefault(part.instrumentId(), "—"));
                if (iname.isBlank()) {
                    iname = "—";
                }
            }
            JMenuItem item = new JMenuItem("#" + pn + " — " + pname + " — " + iname);
            Long other = partToPlayer.get(pn);
            boolean taken = other != null && other != card.playerId();
            if (Objects.equals(pn, current)) {
                item.setForeground(CURRENT_GREEN);
            } else if (taken) {
                item.setForeground(DUP_RED);
            }
            final int partNumber = pn;
            item.addActionListener(e -> applyPart(card.playerId(), partNumber));
            menu.add(item);
        }
        menu.show(canvas, x, y);
    }

    private void applyPart(long playerId, Integer partNumber) {
        if (itemId == null || setlistRepository == null) {
            return;
        }
        try {
            Integer baseline = layoutPartByPlayer.get(playerId);
            if (Objects.equals(partNumber, baseline)) {
                setlistRepository.deleteBandAssignment(itemId, playerId);
            } else {
                setlistRepository.upsertBandAssignment(itemId, playerId, partNumber);
            }
            if (assignmentChangedHandler != null) {
                assignmentChangedHandler.run();
            }
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
        for (InstrumentInfo info : playerRepository.listInstruments()) {
            names.put(info.id(), info.name());
        }
        return names;
    }

    private static Map<Long, Set<Long>> buildEquivalentInstrumentIds(Map<Long, String> names) {
        Map<String, Set<Long>> byName = new HashMap<>();
        for (Map.Entry<Long, String> entry : names.entrySet()) {
            String key = normalizeName(entry.getValue());
            byName.computeIfAbsent(key, k -> new HashSet<>()).add(entry.getKey());
            // Traveler/Traveller spelling variants share equivalence.
            if (key.contains("traveler") || key.contains("traveller")) {
                String alt = key.contains("traveler")
                        ? key.replace("traveler", "traveller")
                        : key.replace("traveller", "traveler");
                byName.computeIfAbsent(alt, k -> new HashSet<>()).add(entry.getKey());
            }
        }
        Map<Long, Set<Long>> result = new HashMap<>();
        for (Map.Entry<Long, String> entry : names.entrySet()) {
            Set<Long> equiv = new HashSet<>();
            String key = normalizeName(entry.getValue());
            if (byName.containsKey(key)) {
                equiv.addAll(byName.get(key));
            }
            if (key.contains("traveler") || key.contains("traveller")) {
                String alt = key.contains("traveler")
                        ? key.replace("traveler", "traveller")
                        : key.replace("traveller", "traveler");
                if (byName.containsKey(alt)) {
                    equiv.addAll(byName.get(alt));
                }
            }
            equiv.add(entry.getKey());
            result.put(entry.getKey(), equiv);
        }
        return result;
    }

    private static boolean instrumentsEquivalent(
            long a, long b, Map<Long, Set<Long>> equivByInstrument) {
        if (a == b) {
            return true;
        }
        Set<Long> equiv = equivByInstrument.get(a);
        return equiv != null && equiv.contains(b);
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static Long instrumentIdForPart(String partsJson, int partNum) {
        for (SongPartMeta part : parseParts(partsJson)) {
            if (part.partNumber() == partNum) {
                return part.instrumentId();
            }
        }
        return null;
    }

    private static List<SongPartMeta> parseParts(String partsJson) {
        if (partsJson == null || partsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = JSON.readTree(partsJson);
            if (!root.isArray()) {
                return List.of();
            }
            List<SongPartMeta> parts = new ArrayList<>();
            for (JsonNode node : root) {
                if (!node.isObject()) {
                    continue;
                }
                int pn = node.path("part_number").asInt(0);
                if (pn <= 0) {
                    continue;
                }
                String name = node.path("part_name").asText("");
                Long iid = null;
                JsonNode idNode = node.get("instrument_id");
                if (idNode != null && !idNode.isNull() && idNode.isNumber()) {
                    iid = idNode.asLong();
                }
                parts.add(new SongPartMeta(pn, name == null ? "" : name.strip(), iid));
            }
            return List.copyOf(parts);
        } catch (Exception ex) {
            return List.of();
        }
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

            Font baseFont = getFont().deriveFont(Font.PLAIN, 11f);
            for (AssignmentCard card : cards) {
                Rectangle r = cardBounds(card);
                g2.setColor(CARD_FILL);
                g2.fillRoundRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2, 6, 6);
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(CARD_BORDER);
                g2.drawRoundRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2, 6, 6);

                int margin = 3;
                int innerX = r.x + margin;
                int innerW = r.width - 2 * margin;
                int y = r.y + margin;
                FontMetrics fm = g2.getFontMetrics(baseFont);
                int lineH = fm.getHeight();

                // Name row (optional neighbor parts)
                if (card.useSetlistHeader()) {
                    int gutter = fm.stringWidth("999") + 6;
                    g2.setFont(baseFont);
                    g2.setColor(TEXT_SECONDARY);
                    g2.drawString(
                            card.neighborPrev(),
                            innerX,
                            y + fm.getAscent());
                    String next = card.neighborNext();
                    g2.drawString(
                            next,
                            innerX + innerW - fm.stringWidth(next),
                            y + fm.getAscent());
                    String name = card.playerName();
                    int centerW = Math.max(1, innerW - 2 * gutter);
                    String elided = elide(fm, name, centerW);
                    g2.setColor(TEXT);
                    int nameX = innerX + gutter + (centerW - fm.stringWidth(elided)) / 2;
                    g2.drawString(elided, nameX, y + fm.getAscent());
                } else {
                    drawFitting(g2, baseFont, card.playerName(), innerX, y, innerW, lineH, TEXT);
                }
                y += lineH + 2;

                // Large part number
                Font big = baseFont.deriveFont(Font.BOLD, baseFont.getSize2D() + 14f);
                FontMetrics bigFm = g2.getFontMetrics(big);
                int bigH = bigFm.getHeight();
                Color partColor = card.partDuplicate()
                        ? DUP_RED
                        : (card.instrumentChanged() ? WARN_ORANGE : TEXT);
                drawFitting(g2, big, card.partNumber(), innerX, y, innerW, bigH, partColor);
                y += bigH + 2;

                // Instrument
                Color instColor = card.partDuplicate()
                        ? DUP_RED
                        : (card.instrumentWarning() ? WARN_ORANGE : TEXT);
                drawFitting(
                        g2, baseFont, card.instrumentName(), innerX, y, innerW, lineH, instColor);
                y += lineH + 2;

                // Part name
                Font small = baseFont.deriveFont(Math.max(8f, baseFont.getSize2D() - 1f));
                Color nameColor = card.partDuplicate() ? DUP_RED : TEXT;
                int remaining = Math.max(lineH, r.y + r.height - margin - y);
                drawFitting(
                        g2, small, card.partName(), innerX, y, innerW, remaining, nameColor);
            }
            g2.dispose();
        }
    }

    private static String elide(FontMetrics fm, String text, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int ellipsisW = fm.stringWidth(ellipsis);
        if (maxWidth <= ellipsisW) {
            return ellipsis;
        }
        for (int i = text.length() - 1; i >= 0; i--) {
            String candidate = text.substring(0, i) + ellipsis;
            if (fm.stringWidth(candidate) <= maxWidth) {
                return candidate;
            }
        }
        return ellipsis;
    }

    private record SongPartMeta(int partNumber, String partName, Long instrumentId) {
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
            boolean partDuplicate,
            boolean useSetlistHeader,
            String neighborPrev,
            String neighborNext,
            boolean instrumentChanged) {
    }
}

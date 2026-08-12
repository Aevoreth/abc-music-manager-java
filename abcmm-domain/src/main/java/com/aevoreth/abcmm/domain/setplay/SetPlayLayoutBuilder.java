package com.aevoreth.abcmm.domain.setplay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.aevoreth.abcmm.domain.band.BandLayoutSlotInfo;
import com.aevoreth.abcmm.domain.band.BandRepository;
import com.aevoreth.abcmm.domain.band.InstrumentInfo;
import com.aevoreth.abcmm.domain.band.LotroInstrumentDefaults;
import com.aevoreth.abcmm.domain.band.PlayerInstrumentInfo;
import com.aevoreth.abcmm.domain.band.PlayerRepository;
import com.aevoreth.abcmm.domain.band.SongLayoutAssignmentInfo;
import com.aevoreth.abcmm.domain.band.SongLayoutRepository;
import com.aevoreth.abcmm.domain.export.ExportPartMeta;
import com.aevoreth.abcmm.domain.export.PartsJsonParser;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.setlist.SetlistBandAssignmentInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistItemInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistRepository;

/**
 * Builds Set Play up-next layout cards. Ports Python {@code build_set_play_layout_cards}.
 */
public final class SetPlayLayoutBuilder {

    private final BandRepository bandRepository;
    private final PlayerRepository playerRepository;
    private final SetlistRepository setlistRepository;
    private final SongLayoutRepository songLayoutRepository;

    public SetPlayLayoutBuilder(
            BandRepository bandRepository,
            PlayerRepository playerRepository,
            SetlistRepository setlistRepository,
            SongLayoutRepository songLayoutRepository) {
        this.bandRepository = Objects.requireNonNull(bandRepository, "bandRepository");
        this.playerRepository = Objects.requireNonNull(playerRepository, "playerRepository");
        this.setlistRepository = Objects.requireNonNull(setlistRepository, "setlistRepository");
        this.songLayoutRepository = Objects.requireNonNull(songLayoutRepository, "songLayoutRepository");
    }

    /**
     * Cards show {@code nextRow} assignments; gutters: current (left), row after next (right).
     * Empty when next is missing or has no song layout.
     */
    public List<SetPlayLayoutCard> build(
            long bandLayoutId,
            SetlistItemInfo nextRow,
            SetlistItemInfo currentRow,
            SetlistItemInfo rightRow,
            List<SetlistItemInfo> setlistRows) throws LibraryException {
        if (nextRow == null || nextRow.songLayoutId() == null) {
            return List.of();
        }

        List<BandLayoutSlotInfo> slots = bandRepository.listSlots(bandLayoutId);
        if (slots.isEmpty()) {
            return List.of();
        }

        Map<Integer, ExportPartMeta> partsByNum = new HashMap<>();
        for (ExportPartMeta part : PartsJsonParser.parse(nextRow.partsJson())) {
            partsByNum.put(part.partNumber(), part);
        }

        Map<Long, Integer> layoutAssigns = loadLayoutAssigns(nextRow.songLayoutId());
        Map<Long, Integer> overrides = loadOverrides(nextRow.id());
        Set<Long> overridePlayers = overrides.keySet();

        Map<Long, Set<Long>> playerInstruments = loadOwnedInstruments(slots);
        Map<Long, String> instrumentNames = loadInstrumentNames();
        Map<Long, Set<Long>> equivByInstrument = buildEquivalentInstrumentIds(instrumentNames);

        Map<Long, Integer> effective = new HashMap<>();
        for (BandLayoutSlotInfo slot : slots) {
            Integer part = overridePlayers.contains(slot.playerId())
                    ? overrides.get(slot.playerId())
                    : layoutAssigns.get(slot.playerId());
            effective.put(slot.playerId(), part);
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

        List<SetlistItemInfo> rows = setlistRows == null ? List.of() : setlistRows;
        int setlistIdx = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id() == nextRow.id()) {
                setlistIdx = i;
                break;
            }
        }
        Map<Long, Map<Long, Integer>> bulkOverrides = new HashMap<>();
        Map<Long, Map<Long, Integer>> layoutCache = new HashMap<>();
        if (setlistIdx >= 0) {
            for (SetlistItemInfo row : rows) {
                bulkOverrides.put(row.id(), loadOverrides(row.id()));
            }
        }

        List<SetPlayLayoutCard> cards = new ArrayList<>();
        for (BandLayoutSlotInfo slot : slots) {
            Integer eff = effective.get(slot.playerId());
            boolean partDup = eff != null && duplicatedParts.contains(eff);
            String pn;
            String pname;
            String iname;
            boolean instWarn = false;
            Long iid = null;
            if (eff != null && partsByNum.containsKey(eff)) {
                ExportPartMeta meta = partsByNum.get(eff);
                pn = String.valueOf(meta.partNumber());
                String rawName = meta.partName() == null ? "" : meta.partName().strip();
                pname = rawName.isBlank() ? ("Part " + eff) : rawName;
                iid = meta.instrumentId();
                iname = iid == null
                        ? "—"
                        : LotroInstrumentDefaults.uiName(
                                instrumentNames.getOrDefault(iid, "—"));
                if (iname == null || iname.isBlank()) {
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

            boolean instChanged = false;
            Long priorIid = null;
            if (currentRow != null) {
                Integer curEff = setlistIdx >= 0
                        ? effectiveFor(currentRow, slot.playerId(), bulkOverrides, layoutCache)
                        : effectiveForItem(currentRow, slot.playerId());
                if (curEff != null) {
                    priorIid = instrumentIdForPart(currentRow.partsJson(), curEff);
                }
            } else if (setlistIdx > 0) {
                for (int j = setlistIdx - 1; j >= 0; j--) {
                    SetlistItemInfo back = rows.get(j);
                    Integer bpn = effectiveFor(back, slot.playerId(), bulkOverrides, layoutCache);
                    if (bpn != null) {
                        priorIid = instrumentIdForPart(back.partsJson(), bpn);
                        break;
                    }
                }
            }
            if (!partDup
                    && eff != null
                    && partsByNum.containsKey(eff)
                    && iid != null
                    && priorIid != null) {
                instChanged = !instrumentsEquivalent(iid, priorIid, equivByInstrument);
            }

            Integer curPn = effectiveForItem(currentRow, slot.playerId());
            Integer rightPn = effectiveForItem(rightRow, slot.playerId());
            String prevL = curPn != null ? String.valueOf(curPn) : "";
            String nextL = rightPn != null ? String.valueOf(rightPn) : "";

            String playerName = slot.playerName() == null || slot.playerName().isBlank()
                    ? ("#" + slot.playerId())
                    : slot.playerName();

            cards.add(new SetPlayLayoutCard(
                    slot.playerId(),
                    playerName,
                    slot.x(),
                    slot.y(),
                    Math.max(1, slot.widthUnits()),
                    Math.max(1, slot.heightUnits()),
                    pn,
                    pname,
                    iname,
                    instWarn,
                    partDup,
                    true,
                    prevL,
                    nextL,
                    instChanged));
        }
        return List.copyOf(cards);
    }

    private Integer effectiveForItem(SetlistItemInfo row, long playerId) throws LibraryException {
        if (row == null || row.songLayoutId() == null) {
            return null;
        }
        Map<Long, Integer> layoutAssigns = loadLayoutAssigns(row.songLayoutId());
        Map<Long, Integer> overrides = loadOverrides(row.id());
        if (overrides.containsKey(playerId)) {
            return overrides.get(playerId);
        }
        return layoutAssigns.get(playerId);
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
            layout = loadLayoutAssigns(slId);
            layoutCache.put(slId, layout);
        }
        return layout.get(playerId);
    }

    private Map<Long, Integer> loadLayoutAssigns(long songLayoutId) throws LibraryException {
        Map<Long, Integer> layoutAssigns = new HashMap<>();
        for (SongLayoutAssignmentInfo a : songLayoutRepository.listAssignments(songLayoutId)) {
            layoutAssigns.put(a.playerId(), a.partNumber());
        }
        return layoutAssigns;
    }

    private Map<Long, Integer> loadOverrides(long setlistItemId) throws LibraryException {
        Map<Long, Integer> overrides = new HashMap<>();
        for (SetlistBandAssignmentInfo a : setlistRepository.listBandAssignments(setlistItemId)) {
            overrides.put(a.playerId(), a.partNumber());
        }
        return overrides;
    }

    private Map<Long, Set<Long>> loadOwnedInstruments(List<BandLayoutSlotInfo> slots)
            throws LibraryException {
        Map<Long, Set<Long>> result = new HashMap<>();
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
        for (ExportPartMeta part : PartsJsonParser.parse(partsJson)) {
            if (part.partNumber() == partNum) {
                return part.instrumentId();
            }
        }
        return null;
    }
}

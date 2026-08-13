package com.aevoreth.abcmm.domain.setplay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.aevoreth.abcmm.domain.band.BandLayoutInfo;
import com.aevoreth.abcmm.domain.band.BandLayoutSlotInfo;
import com.aevoreth.abcmm.domain.band.BandRepository;
import com.aevoreth.abcmm.domain.band.InstrumentInfo;
import com.aevoreth.abcmm.domain.band.PlayerRepository;
import com.aevoreth.abcmm.domain.export.CsvPartSheet;
import com.aevoreth.abcmm.domain.export.ExportPartMeta;
import com.aevoreth.abcmm.domain.export.LayoutExportOrder;
import com.aevoreth.abcmm.domain.export.PartsJsonParser;
import com.aevoreth.abcmm.domain.export.SetExportSettings;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.prefs.SetExportPreferences;
import com.aevoreth.abcmm.domain.setlist.SetlistBandAssignmentInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistItemInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistRepository;

/**
 * Builds a {@link SetPlayPartsSheet} from the loaded setlist using CSV export column
 * order and CSV part-renaming rules.
 */
public final class SetPlayPartsSheetBuilder {

    private SetPlayPartsSheetBuilder() {
    }

    public static SetPlayPartsSheet build(
            SetlistInfo setlist,
            List<SetlistItemInfo> items,
            SetlistRepository setlistRepository,
            BandRepository bandRepository,
            PlayerRepository playerRepository,
            SetExportPreferences exportPrefs) throws LibraryException {
        Objects.requireNonNull(items, "items");
        SetExportSettings settings = new SetExportSettings();
        if (exportPrefs != null) {
            exportPrefs.applyTo(settings);
        }
        Map<Long, String> instrumentNames = new HashMap<>();
        if (playerRepository != null) {
            for (InstrumentInfo instrument : playerRepository.listInstruments()) {
                instrumentNames.put(instrument.id(), instrument.name());
            }
        }
        boolean useInstrument = "instrument".equals(settings.csvPartColumns());
        Long bandLayoutId = setlist == null ? null : setlist.bandLayoutId();

        if (bandLayoutId == null || bandRepository == null) {
            return partNumberSheet(items, settings, instrumentNames, useInstrument);
        }

        BandLayoutInfo layout = null;
        for (var band : bandRepository.listBands()) {
            for (BandLayoutInfo candidate : bandRepository.listLayouts(band.id())) {
                if (candidate.id() == bandLayoutId) {
                    layout = candidate;
                    break;
                }
            }
            if (layout != null) {
                break;
            }
        }
        List<BandLayoutSlotInfo> slots = LayoutExportOrder.listSlotsForExport(
                bandRepository.listSlots(bandLayoutId),
                layout == null
                        ? List.of()
                        : LayoutExportOrder.parseExportColumnOrderJson(layout.exportColumnOrderJson()));
        if (slots.isEmpty()) {
            return partNumberSheet(items, settings, instrumentNames, useInstrument);
        }

        List<SetPlayPartsSheet.Column> columns = new ArrayList<>();
        for (BandLayoutSlotInfo slot : slots) {
            String title = slot.playerName() == null || slot.playerName().isBlank()
                    ? "Player " + slot.playerId()
                    : slot.playerName();
            columns.add(new SetPlayPartsSheet.Column(
                    "p" + slot.playerId(), title, slot.playerId()));
        }

        Map<Long, Set<String>> needed = new LinkedHashMap<>();
        for (BandLayoutSlotInfo slot : slots) {
            needed.put(slot.playerId(), new LinkedHashSet<>());
        }

        List<SetPlayPartsSheet.Row> rows = new ArrayList<>();
        for (SetlistItemInfo item : items) {
            Map<Integer, ExportPartMeta> byNum = new HashMap<>();
            for (ExportPartMeta part : PartsJsonParser.parse(item.partsJson())) {
                byNum.put(part.partNumber(), part);
            }
            Map<Long, Integer> assigns = new HashMap<>();
            if (setlistRepository != null) {
                for (SetlistBandAssignmentInfo a : setlistRepository.listBandAssignments(item.id())) {
                    if (a.partNumber() != null) {
                        assigns.put(a.playerId(), a.partNumber());
                    }
                }
            }
            List<String> cells = new ArrayList<>();
            for (BandLayoutSlotInfo slot : slots) {
                Integer pn = assigns.get(slot.playerId());
                if (pn != null && byNum.containsKey(pn)) {
                    ExportPartMeta part = byNum.get(pn);
                    String label = cellLabel(part, pn, useInstrument, instrumentNames, settings);
                    cells.add(pn + ": " + label);
                    String catalog = catalogName(part, instrumentNames);
                    if (!catalog.isBlank()) {
                        needed.get(slot.playerId()).add(catalog);
                    }
                } else {
                    cells.add("");
                }
            }
            rows.add(new SetPlayPartsSheet.Row(item.id(), cells));
        }

        List<SetPlayPartsSheet.InstrumentsNeeded> appendix = new ArrayList<>();
        for (BandLayoutSlotInfo slot : slots) {
            List<String> names = needed.get(slot.playerId()).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
            String title = slot.playerName() == null || slot.playerName().isBlank()
                    ? "Player " + slot.playerId()
                    : slot.playerName();
            appendix.add(new SetPlayPartsSheet.InstrumentsNeeded(slot.playerId(), title, names));
        }
        return SetPlayPartsSheet.of(columns, rows, appendix);
    }

    private static SetPlayPartsSheet partNumberSheet(
            List<SetlistItemInfo> items,
            SetExportSettings settings,
            Map<Long, String> instrumentNames,
            boolean useInstrument) {
        int maxParts = items.stream().mapToInt(SetlistItemInfo::partCount).max().orElse(0);
        List<SetPlayPartsSheet.Column> columns = new ArrayList<>();
        for (int i = 0; i < maxParts; i++) {
            columns.add(new SetPlayPartsSheet.Column("part" + (i + 1), "Part " + (i + 1), null));
        }
        List<SetPlayPartsSheet.Row> rows = new ArrayList<>();
        for (SetlistItemInfo item : items) {
            Map<Integer, ExportPartMeta> byNum = new HashMap<>();
            for (ExportPartMeta part : PartsJsonParser.parse(item.partsJson())) {
                byNum.put(part.partNumber(), part);
            }
            List<String> cells = new ArrayList<>();
            for (int i = 0; i < maxParts; i++) {
                int pnum = i + 1;
                ExportPartMeta part = byNum.get(pnum);
                if (part == null) {
                    cells.add("");
                    continue;
                }
                String label = cellLabel(part, pnum, useInstrument, instrumentNames, settings);
                cells.add(pnum + ": " + label);
            }
            rows.add(new SetPlayPartsSheet.Row(item.id(), cells));
        }
        return SetPlayPartsSheet.of(columns, rows, List.of());
    }

    private static String cellLabel(
            ExportPartMeta part,
            int partNumber,
            boolean useInstrument,
            Map<Long, String> instrumentNames,
            SetExportSettings settings) {
        String pname = part.partName() == null || part.partName().isBlank()
                ? "Part " + partNumber
                : part.partName().strip();
        if (useInstrument && part.instrumentId() != null) {
            String iname = instrumentNames.get(part.instrumentId());
            if (iname != null && !iname.isBlank()) {
                pname = iname;
            }
        }
        return CsvPartSheet.applyDisplayRenames(pname, settings.csvPartRenameRules());
    }

    private static String catalogName(ExportPartMeta part, Map<Long, String> instrumentNames) {
        if (part.instrumentId() == null) {
            return "";
        }
        String name = instrumentNames.get(part.instrumentId());
        return name == null ? "" : name;
    }
}

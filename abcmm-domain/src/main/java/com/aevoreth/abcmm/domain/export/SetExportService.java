package com.aevoreth.abcmm.domain.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.aevoreth.abcmm.domain.band.BandLayoutInfo;
import com.aevoreth.abcmm.domain.band.BandLayoutSlotInfo;
import com.aevoreth.abcmm.domain.band.BandRepository;
import com.aevoreth.abcmm.domain.band.InstrumentInfo;
import com.aevoreth.abcmm.domain.band.PlayerInfo;
import com.aevoreth.abcmm.domain.band.PlayerRepository;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.library.SongRepository;
import com.aevoreth.abcmm.domain.setlist.SetlistBandAssignmentInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistRepository;

/**
 * Export a setlist to folder and/or zip with optional CSV and relative ABCP playlist.
 */
public final class SetExportService {

    private static final Pattern INVALID_PATH_CHARS = Pattern.compile("[<>:\"/\\\\|?*]");

    private final SetlistRepository setlistRepository;
    private final SongRepository songRepository;
    private final BandRepository bandRepository;
    private final PlayerRepository playerRepository;

    public SetExportService(
            SetlistRepository setlistRepository,
            SongRepository songRepository,
            BandRepository bandRepository,
            PlayerRepository playerRepository) {
        this.setlistRepository = Objects.requireNonNull(setlistRepository, "setlistRepository");
        this.songRepository = Objects.requireNonNull(songRepository, "songRepository");
        this.bandRepository = Objects.requireNonNull(bandRepository, "bandRepository");
        this.playerRepository = Objects.requireNonNull(playerRepository, "playerRepository");
    }

    public static String sanitizeForPath(String s) {
        String cleaned = INVALID_PATH_CHARS.matcher(s == null ? "" : s).replaceAll("").strip();
        return cleaned.isEmpty() ? "untitled" : cleaned;
    }

    /**
     * Export setlist using pre-loaded export items (with CSV metadata).
     *
     * @param playerIdsInOrder optional CSV column order; when non-null and band layout set, persisted
     */
    public void exportSet(
            long setlistId,
            String setlistName,
            Long bandLayoutId,
            List<SetExportItemInfo> items,
            SetExportSettings settings,
            List<Long> playerIdsInOrder,
            Consumer<String> statusCallback) throws SetExportException {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(settings, "settings");
        if (!settings.exportAsFolder() && !settings.exportAsZip()) {
            throw new SetExportException("Select export as folder and/or zip.");
        }
        if (items.isEmpty()) {
            throw new SetExportException("Setlist has no songs to export.");
        }
        if (settings.outputDirectory() == null) {
            throw new SetExportException("Output directory is required.");
        }

        Consumer<String> status = msg -> {
            if (statusCallback != null) {
                statusCallback.accept(msg);
            }
        };

        String setName = sanitizeForPath(
                settings.setName() == null || settings.setName().isBlank()
                        ? (setlistName == null || setlistName.isBlank() ? "Untitled Set" : setlistName)
                        : settings.setName());

        try {
            Files.createDirectories(settings.outputDirectory());
        } catch (IOException ex) {
            throw new SetExportException("Failed to create output directory: " + settings.outputDirectory(), ex);
        }

        Map<Long, Path> filePaths = new LinkedHashMap<>();
        for (SetExportItemInfo item : items) {
            try {
                Optional<Path> path = songRepository.resolvePrimaryAbcPath(item.songId());
                path.ifPresent(p -> filePaths.put(item.songId(), p));
            } catch (LibraryException ex) {
                throw new SetExportException("Failed to resolve primary ABC for song " + item.songId(), ex);
            }
        }

        if (bandLayoutId != null && playerIdsInOrder != null) {
            try {
                BandLayoutInfo layout = findLayout(bandLayoutId);
                bandRepository.updateLayout(
                        bandLayoutId,
                        layout.name(),
                        LayoutExportOrder.toExportColumnOrderJson(playerIdsInOrder));
            } catch (LibraryException ex) {
                throw new SetExportException("Failed to save player column order", ex);
            }
        }

        Path folderPath = settings.outputDirectory().resolve(setName);
        Path zipPath = settings.outputDirectory().resolve(setName + ".zip");

        if (settings.exportAsFolder() && Files.exists(folderPath)) {
            throw new SetExportException("Output folder already exists: " + folderPath);
        }
        if (settings.exportAsZip() && Files.exists(zipPath)) {
            throw new SetExportException("Output zip file already exists: " + zipPath);
        }

        Path copyTo;
        boolean tempStaging = !settings.exportAsFolder();
        try {
            if (settings.exportAsFolder()) {
                Files.createDirectories(folderPath);
                status.accept("Created output folder...");
                copyTo = folderPath;
            } else {
                copyTo = Files.createTempDirectory("set_export_");
                status.accept("Preparing export...");
            }
        } catch (IOException ex) {
            throw new SetExportException("Failed to create staging directory", ex);
        }

        try {
            Map<Long, String> playersById = new HashMap<>();
            for (PlayerInfo player : playerRepository.listPlayers()) {
                playersById.put(player.id(), player.name());
            }
            Map<Long, String> instrumentNames = new HashMap<>();
            for (InstrumentInfo instrument : playerRepository.listInstruments()) {
                instrumentNames.put(instrument.id(), instrument.name());
            }

            Map<String, Integer> usedNames = new HashMap<>();
            List<ExportEntry> exportEntries = new ArrayList<>();

            for (int i = 0; i < items.size(); i++) {
                SetExportItemInfo item = items.get(i);
                Path src = filePaths.get(item.songId());
                if (src == null) {
                    continue;
                }
                String fp = src.toString();
                String base;
                if (settings.renameAbcFiles()) {
                    base = SetFilenameTemplate.formatFilename(
                            settings.filenamePattern(),
                            fp,
                            i,
                            item.title(),
                            item.composers(),
                            item.transcriber(),
                            item.durationSeconds(),
                            item.partCount(),
                            settings.whitespaceReplace(),
                            settings.partCountZeroPadded());
                } else {
                    Path name = src.getFileName();
                    base = name == null ? "song.abc" : name.toString();
                }
                if (usedNames.containsKey(base)) {
                    int n = usedNames.get(base) + 1;
                    usedNames.put(base, n);
                    int dot = base.lastIndexOf('.');
                    String stem = dot >= 0 ? base.substring(0, dot) : base;
                    String ext = dot >= 0 ? base.substring(dot) : "";
                    base = stem + "_" + n + ext;
                } else {
                    usedNames.put(base, 1);
                }
                exportEntries.add(new ExportEntry(i, item, src, base));
            }

            for (int j = 0; j < exportEntries.size(); j++) {
                ExportEntry entry = exportEntries.get(j);
                Path dest = copyTo.resolve(entry.baseName());
                if (settings.renameParts()) {
                    Map<Long, Integer> assigns = loadAssignments(entry.item().itemId(), bandLayoutId);
                    Map<Integer, String> partMap = buildPartTLineMap(
                            entry.item(),
                            filePaths.get(entry.item().songId()).toString(),
                            entry.listIndex(),
                            bandLayoutId,
                            assigns,
                            playersById,
                            instrumentNames,
                            settings);
                    String text = Files.readString(entry.source(), StandardCharsets.UTF_8);
                    String newText = AbcPartTitleRewrite.rewriteAbcPartTLines(text, partMap);
                    Files.writeString(dest, newText, StandardCharsets.UTF_8);
                } else {
                    Files.copy(entry.source(), dest);
                }
                status.accept("Copied ABC " + (j + 1) + " of " + exportEntries.size() + "...");
            }

            if (settings.exportCsvPartSheet()) {
                status.accept("Generating CSV part sheet...");
                writeCsv(
                        items,
                        filePaths,
                        settings,
                        bandLayoutId,
                        copyTo.resolve(setName + ".csv"),
                        playerIdsInOrder,
                        playersById,
                        instrumentNames);
            }

            if (settings.exportAbcpPlaylist() && !exportEntries.isEmpty()) {
                status.accept("Writing ABCP playlist...");
                List<String> relPaths = new ArrayList<>();
                for (ExportEntry entry : exportEntries) {
                    relPaths.add(entry.baseName().replace('\\', '/'));
                }
                AbcpWriter.write(copyTo.resolve(setName + ".abcp"), relPaths);
            }

            if (settings.exportAsZip()) {
                status.accept("Creating zip file...");
                Path zipSource = settings.exportAsFolder() ? folderPath : copyTo;
                zipDirectory(zipSource, zipPath);
            }

            if (tempStaging) {
                deleteRecursive(copyTo);
            }
            status.accept("Export finished.");
        } catch (SetExportException ex) {
            if (tempStaging) {
                deleteRecursiveQuietly(copyTo);
            }
            throw ex;
        } catch (IOException | LibraryException ex) {
            if (tempStaging) {
                deleteRecursiveQuietly(copyTo);
            }
            throw new SetExportException(ex.getMessage() == null ? "Export failed" : ex.getMessage(), ex);
        }
    }

    /**
     * Write standalone ABCP with absolute primary paths in set order.
     */
    public void exportStandaloneAbcp(List<SetExportItemInfo> items, Path outputPath)
            throws SetExportException {
        if (items == null || items.isEmpty()) {
            throw new SetExportException("Setlist has no songs to export.");
        }
        List<String> paths = new ArrayList<>();
        for (SetExportItemInfo item : items) {
            try {
                Optional<Path> path = songRepository.resolvePrimaryAbcPath(item.songId());
                if (path.isPresent()) {
                    paths.add(path.get().toAbsolutePath().normalize().toString());
                }
            } catch (LibraryException ex) {
                throw new SetExportException("Failed to resolve primary ABC for song " + item.songId(), ex);
            }
        }
        if (paths.isEmpty()) {
            throw new SetExportException("No primary ABC files found for this setlist.");
        }
        try {
            AbcpWriter.write(outputPath, paths);
        } catch (IOException ex) {
            throw new SetExportException("Failed to write ABCP: " + outputPath, ex);
        }
    }

    private BandLayoutInfo findLayout(long bandLayoutId) throws LibraryException, SetExportException {
        // listLayouts requires bandId; scan all bands
        for (var band : bandRepository.listBands()) {
            for (BandLayoutInfo layout : bandRepository.listLayouts(band.id())) {
                if (layout.id() == bandLayoutId) {
                    return layout;
                }
            }
        }
        throw new SetExportException("Band layout not found: " + bandLayoutId);
    }

    private Map<Long, Integer> loadAssignments(long itemId, Long bandLayoutId) throws LibraryException {
        Map<Long, Integer> map = new HashMap<>();
        if (bandLayoutId == null) {
            return map;
        }
        for (SetlistBandAssignmentInfo a : setlistRepository.listBandAssignments(itemId)) {
            if (a.partNumber() != null) {
                map.put(a.playerId(), a.partNumber());
            }
        }
        return map;
    }

    private Map<Integer, String> buildPartTLineMap(
            SetExportItemInfo item,
            String filePath,
            int listIndex,
            Long bandLayoutId,
            Map<Long, Integer> assigns,
            Map<Long, String> playersById,
            Map<Long, String> instrumentNames,
            SetExportSettings settings) {
        List<ExportPartMeta> parts = PartsJsonParser.parse(item.partsJson());
        if (parts.isEmpty()) {
            return Map.of();
        }
        Map<Integer, String> numer = SetFilenameTemplate.computePartNumeration(parts);
        Map<Integer, String> out = new LinkedHashMap<>();
        for (ExportPartMeta part : parts) {
            String inst = "";
            if (part.instrumentId() != null) {
                inst = instrumentNames.getOrDefault(part.instrumentId(), "");
            }
            String player = "";
            if (bandLayoutId != null) {
                player = playerNameForPart(assigns, playersById, part.partNumber());
            }
            String newTitle = SetFilenameTemplate.formatPartName(
                    settings.partNamePattern(),
                    filePath,
                    listIndex,
                    item.title(),
                    item.composers(),
                    item.transcriber(),
                    item.durationSeconds(),
                    item.partCount(),
                    inst,
                    part.partName() == null ? "" : part.partName(),
                    part.titleFromT() == null ? "" : part.titleFromT(),
                    String.valueOf(part.partNumber()),
                    player,
                    numer.getOrDefault(part.partNumber(), ""),
                    settings.whitespaceReplace(),
                    settings.partCountZeroPadded());
            out.put(part.partNumber(), newTitle);
        }
        return out;
    }

    private static String playerNameForPart(
            Map<Long, Integer> assigns,
            Map<Long, String> playersById,
            int partNumber) {
        List<Long> candidates = assigns.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() == partNumber)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (candidates.isEmpty()) {
            return "";
        }
        long pid = candidates.get(0);
        return playersById.getOrDefault(pid, "Player " + pid);
    }

    private void writeCsv(
            List<SetExportItemInfo> items,
            Map<Long, Path> filePaths,
            SetExportSettings settings,
            Long bandLayoutId,
            Path outputPath,
            List<Long> playerIdsInOrder,
            Map<Long, String> playersById,
            Map<Long, String> instrumentNames) throws IOException, LibraryException, SetExportException {
        List<String> metadataCols = CsvPartSheet.metadataColumns(settings);
        boolean useInstrument = "instrument".equals(settings.csvPartColumns());

        List<Long> playerIds = new ArrayList<>();
        List<String> playerNames = new ArrayList<>();
        if (bandLayoutId != null) {
            if (playerIdsInOrder != null) {
                playerIds.addAll(playerIdsInOrder);
            } else {
                BandLayoutInfo layout = findLayout(bandLayoutId);
                List<BandLayoutSlotInfo> slots = LayoutExportOrder.listSlotsForExport(
                        bandRepository.listSlots(bandLayoutId),
                        LayoutExportOrder.parseExportColumnOrderJson(layout.exportColumnOrderJson()));
                for (BandLayoutSlotInfo slot : slots) {
                    playerIds.add(slot.playerId());
                }
            }
            for (Long pid : playerIds) {
                playerNames.add(playersById.getOrDefault(pid, "Player " + pid));
            }
        }

        List<String> headers = new ArrayList<>(metadataCols);
        int maxParts = 0;
        if (bandLayoutId != null) {
            headers.addAll(playerNames);
        } else {
            maxParts = items.stream().mapToInt(SetExportItemInfo::partCount).max().orElse(0);
            for (int i = 0; i < maxParts; i++) {
                headers.add("Part " + (i + 1));
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writeCsvRow(writer, headers);

            Map<Long, Map<Long, Integer>> assignmentsByItem = new HashMap<>();
            if (bandLayoutId != null) {
                for (SetExportItemInfo item : items) {
                    assignmentsByItem.put(item.itemId(), loadAssignments(item.itemId(), bandLayoutId));
                }
            }

            for (SetExportItemInfo item : items) {
                Path fp = filePaths.get(item.songId());
                List<String> row = new ArrayList<>();
                for (String col : metadataCols) {
                    row.add(metadataValue(col, item, fp));
                }
                List<ExportPartMeta> parts = PartsJsonParser.parse(item.partsJson());
                Map<Integer, ExportPartMeta> byNum = new HashMap<>();
                for (ExportPartMeta p : parts) {
                    byNum.put(p.partNumber(), p);
                }

                if (bandLayoutId != null) {
                    Map<Long, Integer> assigns = assignmentsByItem.getOrDefault(item.itemId(), Map.of());
                    for (Long pid : playerIds) {
                        Integer pn = assigns.get(pid);
                        if (pn != null && byNum.containsKey(pn)) {
                            ExportPartMeta p = byNum.get(pn);
                            String label = csvPartCellLabel(p, pn, useInstrument, instrumentNames, settings);
                            row.add(pn + ": " + label);
                        } else {
                            row.add("");
                        }
                    }
                } else {
                    for (int i = 0; i < maxParts; i++) {
                        int pnum = i + 1;
                        String cell = partDisplay(byNum.get(pnum), pnum, useInstrument, instrumentNames);
                        row.add(CsvPartSheet.applyDisplayRenames(cell, settings.csvPartRenameRules()));
                    }
                }
                writeCsvRow(writer, row);
            }

            if (bandLayoutId != null && !playerIds.isEmpty()) {
                for (int i = 0; i < 3; i++) {
                    writeCsvRow(writer, blankRow(headers.size()));
                }
                writeCsvRow(writer, List.of("Player Name", "Instruments needed"));

                Map<Long, Set<String>> byPlayer = new LinkedHashMap<>();
                for (Long pid : playerIds) {
                    byPlayer.put(pid, new LinkedHashSet<>());
                }
                for (SetExportItemInfo item : items) {
                    Map<Long, Integer> assigns = assignmentsByItem.getOrDefault(item.itemId(), Map.of());
                    Map<Integer, ExportPartMeta> byNum = new HashMap<>();
                    for (ExportPartMeta p : PartsJsonParser.parse(item.partsJson())) {
                        byNum.put(p.partNumber(), p);
                    }
                    for (Long pid : playerIds) {
                        Integer pn = assigns.get(pid);
                        if (pn != null && byNum.containsKey(pn)) {
                            String label = appendixMadeForCatalogName(byNum.get(pn), instrumentNames);
                            if (!label.isBlank()) {
                                byPlayer.get(pid).add(label);
                            }
                        }
                    }
                }
                for (int i = 0; i < playerIds.size(); i++) {
                    Long pid = playerIds.get(i);
                    List<String> ordered = byPlayer.get(pid).stream()
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .toList();
                    writeCsvRow(writer, List.of(playerNames.get(i), String.join(", ", ordered)));
                }
            }
        }
    }

    private static String csvPartCellLabel(
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

    private static String partDisplay(
            ExportPartMeta part,
            int partNumber,
            boolean useInstrument,
            Map<Long, String> instrumentNames) {
        if (part == null) {
            return "Part " + partNumber;
        }
        if (useInstrument && part.instrumentId() != null) {
            String name = instrumentNames.get(part.instrumentId());
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        if (part.partName() != null && !part.partName().isBlank()) {
            return part.partName().strip();
        }
        return "Part " + partNumber;
    }

    private static String appendixMadeForCatalogName(
            ExportPartMeta part,
            Map<Long, String> instrumentNames) {
        if (part.instrumentId() == null) {
            return "";
        }
        String name = instrumentNames.get(part.instrumentId());
        return name == null ? "" : name;
    }

    private static String metadataValue(String col, SetExportItemInfo item, Path filePath) {
        return switch (col) {
            case "Title" -> item.title() == null ? "" : item.title();
            case "Composers", "Artist" -> item.composers() == null ? "" : item.composers();
            case "Transcriber" -> item.transcriber() == null ? "" : item.transcriber();
            case "Duration" -> CsvPartSheet.formatDuration(item.durationSeconds());
            case "Parts", "Part Count" -> String.valueOf(item.partCount());
            case "File Name" -> {
                if (filePath == null || filePath.getFileName() == null) {
                    yield "";
                }
                String name = filePath.getFileName().toString();
                if (name.toLowerCase(Locale.ROOT).endsWith(".abc")) {
                    yield name.substring(0, name.length() - 4);
                }
                yield name;
            }
            case "Notes" -> item.notes() == null ? "" : item.notes();
            case "Status" -> item.statusName() == null ? "" : item.statusName();
            default -> "";
        };
    }

    private static List<String> blankRow(int size) {
        List<String> row = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            row.add("");
        }
        return row;
    }

    private static void writeCsvRow(BufferedWriter writer, List<String> cells) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escapeCsv(cells.get(i)));
        }
        writer.write(sb.toString());
        writer.newLine();
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        String escaped = value.replace("\"", "\"\"");
        return needsQuotes ? "\"" + escaped + "\"" : escaped;
    }

    private static void zipDirectory(Path sourceDir, Path zipPath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath));
                Stream<Path> walk = Files.walk(sourceDir)) {
            List<Path> files = walk.filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString)).toList();
            for (Path file : files) {
                String arcname = sourceDir.relativize(file).toString().replace('\\', '/');
                zos.putNextEntry(new ZipEntry(arcname));
                Files.copy(file, zos);
                zos.closeEntry();
            }
        }
    }

    private static void deleteRecursive(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path p : paths) {
                Files.deleteIfExists(p);
            }
        }
    }

    private static void deleteRecursiveQuietly(Path root) {
        try {
            deleteRecursive(root);
        } catch (IOException ignored) {
            // best effort
        }
    }

    private record ExportEntry(int listIndex, SetExportItemInfo item, Path source, String baseName) {
    }
}

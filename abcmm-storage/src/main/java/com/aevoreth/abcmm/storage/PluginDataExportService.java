package com.aevoreth.abcmm.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import com.aevoreth.abcmm.domain.export.ExportPartMeta;
import com.aevoreth.abcmm.domain.export.PartsJsonParser;
import com.aevoreth.abcmm.domain.export.PluginDataLuaBuilder;
import com.aevoreth.abcmm.domain.export.PluginDataPathRules;
import com.aevoreth.abcmm.domain.export.PluginDataSongEntry;
import com.aevoreth.abcmm.domain.library.AccountTargetInfo;
import com.aevoreth.abcmm.domain.library.FolderRuleInfo;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.library.SongFileMetadata;
import com.aevoreth.abcmm.domain.library.SongRepository;
import com.aevoreth.abcmm.domain.prefs.LotroPaths;
import com.aevoreth.abcmm.domain.prefs.Preferences;
import com.aevoreth.abcmm.domain.scan.AbcFileMetadata;
import com.aevoreth.abcmm.domain.scan.AbcPartMetadata;

/**
 * Manual Songbook {@code SongbookData.plugindata} export (Python {@code plugindata_writer}).
 *
 * <p>Writes UTF-8 Lua matching the Songbook plugin format. Core table shape aligns with stock
 * Songbook HTA output; use that HTA only as a format reference — do not invoke it.
 */
public final class PluginDataExportService {

    public static final String PLUGINDATA_FILENAME = "SongbookData.plugindata";

    private final SongRepository songRepository;
    private final AbcMetadataParser parser;

    public PluginDataExportService(SongRepository songRepository) {
        this(songRepository, new AbcMetadataParser());
    }

    public PluginDataExportService(SongRepository songRepository, AbcMetadataParser parser) {
        this.songRepository = Objects.requireNonNull(songRepository, "songRepository");
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    /**
     * Build Lua once and write to all enabled account targets.
     *
     * @param logFn optional {@code (message, isError)} progress callback
     * @return success count and error messages
     */
    public ExportResult writeAllTargets(
            Preferences preferences,
            BiConsumer<String, Boolean> logFn) throws LibraryException {
        Objects.requireNonNull(preferences, "preferences");
        BiConsumer<String, Boolean> log = logFn == null ? (m, e) -> { } : logFn;

        List<AccountTargetInfo> targets = songRepository.listAccountTargets().stream()
                .filter(AccountTargetInfo::enabled)
                .toList();

        log.accept("Starting PluginData export...", false);
        log.accept("Found " + targets.size() + " enabled target(s).", false);

        log.accept("Building songbook data from library...", false);
        PluginDataLuaBuilder.BuildResult built = buildLua(preferences);
        log.accept(
                "Built songbook: " + built.songCount() + " songs, " + built.directoryCount()
                        + " directories.",
                false);

        List<String> errors = new ArrayList<>();
        int success = 0;
        for (AccountTargetInfo target : targets) {
            log.accept(
                    "Writing to " + target.accountName() + " (" + target.pluginDataPath() + ")...",
                    false);
            try {
                writeToPath(Path.of(target.pluginDataPath()), built.lua());
                success++;
                log.accept("  OK", false);
            } catch (IOException | RuntimeException ex) {
                String errMsg = target.accountName() + ": " + ex.getMessage();
                errors.add(errMsg);
                log.accept("  Error: " + ex.getMessage(), true);
            }
        }

        if (errors.isEmpty()) {
            log.accept("\nExport complete: " + success + " target(s) written.", false);
        } else {
            log.accept(
                    "\nExport complete: " + success + " succeeded, " + errors.size() + " failed.",
                    false);
        }
        return new ExportResult(success, List.copyOf(errors), built.songCount(), built.directoryCount());
    }

    /**
     * Build PluginData Lua for the current Music / set-export / folder-rule configuration.
     */
    public PluginDataLuaBuilder.BuildResult buildLua(Preferences preferences) throws LibraryException {
        Objects.requireNonNull(preferences, "preferences");
        String lotroRoot = LotroPaths.effectiveLotroRootString(preferences);
        Path musicRoot = LotroPaths.musicRoot(lotroRoot).orElse(null);
        Path setExportDir = resolveSetExportDir(preferences, lotroRoot);

        List<PluginDataPathRules.ExcludeRule> excludeRules = resolveExcludeRules(
                songRepository.listFolderRules(), musicRoot);

        List<Path> abcPaths = collectAbcPaths(musicRoot, setExportDir, excludeRules);
        List<PluginDataSongEntry> songs = new ArrayList<>();
        for (Path abcPath : abcPaths) {
            Optional<PluginDataSongEntry> entry = buildSongEntry(abcPath, lotroRoot);
            entry.ifPresent(songs::add);
        }
        return PluginDataLuaBuilder.build(songs);
    }

    /**
     * Write Lua content to {@code targetDir/SongbookData.plugindata}, creating parents as needed.
     */
    public void writeToPath(Path targetDir, String luaContent) throws IOException {
        Objects.requireNonNull(targetDir, "targetDir");
        Objects.requireNonNull(luaContent, "luaContent");
        Files.createDirectories(targetDir);
        Path outFile = targetDir.resolve(PLUGINDATA_FILENAME);
        Files.writeString(outFile, luaContent, StandardCharsets.UTF_8);
    }

    static Path resolveSetExportDir(Preferences preferences, String lotroRoot) {
        String stored = preferences.setExportDir();
        if (stored == null || stored.isBlank()) {
            return null;
        }
        String resolved = LotroPaths.resolveMusicPath(stored, lotroRoot);
        if (resolved == null || resolved.isBlank()) {
            return null;
        }
        return Path.of(resolved);
    }

    static List<PluginDataPathRules.ExcludeRule> resolveExcludeRules(
            List<FolderRuleInfo> rules, Path musicRoot) {
        List<PluginDataPathRules.ExcludeRule> out = new ArrayList<>();
        if (rules == null) {
            return out;
        }
        for (FolderRuleInfo rule : rules) {
            if (!rule.enabled()) {
                continue;
            }
            String path = rule.path();
            if (path == null || path.isBlank()) {
                continue;
            }
            Path p = Path.of(path.trim());
            String resolved;
            try {
                if (p.isAbsolute()) {
                    resolved = p.toAbsolutePath().normalize().toString();
                } else if (musicRoot != null) {
                    resolved = musicRoot.resolve(p).toAbsolutePath().normalize().toString();
                } else {
                    resolved = path.trim();
                }
            } catch (RuntimeException ex) {
                resolved = path.trim();
            }
            out.add(new PluginDataPathRules.ExcludeRule(resolved, rule.includeInExport()));
        }
        return out;
    }

    static List<Path> collectAbcPaths(
            Path musicRoot,
            Path setExportDir,
            List<PluginDataPathRules.ExcludeRule> excludeRules) {
        Set<String> seen = new LinkedHashSet<>();
        List<Path> out = new ArrayList<>();

        if (musicRoot != null && Files.isDirectory(musicRoot)) {
            try (Stream<Path> walk = Files.walk(musicRoot)) {
                walk.filter(Files::isRegularFile)
                        .filter(PluginDataExportService::isAbcFile)
                        .forEach(f -> {
                            if (PluginDataPathRules.includePathInSongbook(
                                    f, musicRoot, setExportDir, excludeRules)) {
                                addPath(f, seen, out);
                            }
                        });
            } catch (IOException ignored) {
                // skip unreadable Music tree
            }
        }
        if (setExportDir != null && Files.isDirectory(setExportDir)) {
            try (Stream<Path> walk = Files.walk(setExportDir)) {
                walk.filter(Files::isRegularFile)
                        .filter(PluginDataExportService::isAbcFile)
                        .forEach(f -> addPath(f, seen, out));
            } catch (IOException ignored) {
                // skip unreadable set-export tree
            }
        }
        return out;
    }

    private Optional<PluginDataSongEntry> buildSongEntry(Path abcPath, String lotroRoot) {
        Path resolved;
        try {
            resolved = abcPath.toRealPath();
        } catch (IOException ex) {
            try {
                resolved = abcPath.toAbsolutePath().normalize();
            } catch (RuntimeException ex2) {
                return Optional.empty();
            }
        }
        String pathStr = resolved.toString();

        String title;
        String composers;
        String transcriber;
        String partsJson;
        List<ExportPartMeta> parts;

        try {
            Optional<SongFileMetadata> meta = songRepository.findMetadataByFilePath(pathStr);
            if (meta.isEmpty()) {
                // Try non-real-path absolute form (Windows junction / stored path variants)
                meta = songRepository.findMetadataByFilePath(abcPath.toAbsolutePath().normalize().toString());
            }
            if (meta.isPresent()) {
                SongFileMetadata m = meta.get();
                title = m.title();
                composers = m.composers();
                transcriber = m.transcriber() == null ? "" : m.transcriber();
                partsJson = m.partsJson();
                parts = PartsJsonParser.parse(partsJson);
            } else {
                AbcFileMetadata parsed = parser.parse(resolved);
                title = parsed.title();
                composers = parsed.composers();
                transcriber = parsed.transcriber() == null ? "" : parsed.transcriber();
                parts = toExportParts(parsed.parts());
            }
        } catch (LibraryException | IOException | RuntimeException ex) {
            return Optional.empty();
        }

        String rel = LotroPaths.toMusicRelative(pathStr, lotroRoot);
        if (rel == null || rel.isBlank()) {
            rel = pathStr;
        }
        String relPosix = rel.replace('\\', '/');
        if (!relPosix.startsWith("/")) {
            relPosix = "/" + relPosix;
        }

        String dirPart = parentDirAsSongbookPath(relPosix);
        String filename = stem(resolved.getFileName().toString());
        String artist = (composers == null || composers.isBlank()) ? "Unknown" : composers.strip();
        String trans = transcriber == null ? "" : transcriber.strip();

        List<PluginDataSongEntry.Track> tracks = PluginDataLuaBuilder.tracksFromParts(parts, title);
        return Optional.of(new PluginDataSongEntry(dirPart, filename, tracks, trans, artist));
    }

    private static List<ExportPartMeta> toExportParts(List<AbcPartMetadata> parts) {
        if (parts == null || parts.isEmpty()) {
            return List.of();
        }
        List<ExportPartMeta> out = new ArrayList<>(parts.size());
        for (AbcPartMetadata p : parts) {
            out.add(new ExportPartMeta(
                    p.partNumber(),
                    p.partName(),
                    p.titleFromT(),
                    p.instrumentId()));
        }
        return out;
    }

    private static String parentDirAsSongbookPath(String relPosix) {
        int lastSlash = relPosix.lastIndexOf('/');
        String parent = lastSlash <= 0 ? "/" : relPosix.substring(0, lastSlash + 1);
        return PluginDataLuaBuilder.ensureDirPath(parent);
    }

    private static String stem(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return fileName;
        }
        return fileName.substring(0, dot);
    }

    private static boolean isAbcFile(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        return name.toLowerCase(Locale.ROOT).endsWith(".abc");
    }

    private static void addPath(Path path, Set<String> seen, List<Path> out) {
        try {
            String key = path.toRealPath().toString();
            if (seen.add(key)) {
                out.add(path);
            }
        } catch (IOException ex) {
            String key = path.toAbsolutePath().normalize().toString();
            if (seen.add(key)) {
                out.add(path);
            }
        }
    }

    /**
     * Result of writing PluginData to enabled targets.
     */
    public record ExportResult(
            int successCount,
            List<String> errors,
            int songCount,
            int directoryCount) {
        public ExportResult {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }
    }
}

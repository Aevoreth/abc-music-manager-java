package com.aevoreth.abcmm.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.scan.AbcFileMetadata;
import com.aevoreth.abcmm.domain.scan.AbcPartMetadata;
import com.aevoreth.abcmm.domain.scan.DuplicateCandidate;
import com.aevoreth.abcmm.domain.scan.DuplicateDecision;
import com.aevoreth.abcmm.domain.scan.DuplicateResolver;
import com.aevoreth.abcmm.domain.scan.LibraryScanService;
import com.aevoreth.abcmm.domain.scan.ScanProgress;
import com.aevoreth.abcmm.domain.scan.ScanRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Library scanner backed by SQLite. Does not close the given {@link SqliteDatabase}.
 */
public final class SqliteLibraryScanService implements LibraryScanService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, String> INSTRUMENT_SPELLING_VARIANTS = Map.of(
            "traveller's trusty fiddle", "Traveler's Trusty Fiddle");

    private final SqliteDatabase database;
    private final AbcMetadataParser parser = new AbcMetadataParser();

    public SqliteLibraryScanService(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public ScanProgress scan(ScanRequest request, DuplicateResolver resolver, Consumer<ScanProgress> progress)
            throws LibraryException {
        Objects.requireNonNull(request, "request");
        DuplicateResolver effectiveResolver = resolver != null ? resolver : candidate -> DuplicateDecision.SEPARATE;

        try {
            Path musicRoot = request.lotroRoot().resolve("Music");
            List<Path> libraryRoots = new ArrayList<>();
            if (Files.isDirectory(musicRoot)) {
                libraryRoots.add(normalizePath(musicRoot));
            }

            Path setRoot = resolveSetExportDir(request.setExportDir(), musicRoot);
            if (setRoot != null && !isUnderMusicRoot(setRoot, musicRoot)) {
                notifyProgress(progress, new ScanProgress(
                        0, 0, 0, 0,
                        "Warning: set export dir is outside Music — not excluded from scan ("
                                + setRoot + ")"));
                setRoot = null;
            } else if (setRoot != null && !Files.isDirectory(setRoot)) {
                notifyProgress(progress, new ScanProgress(
                        0, 0, 0, 0,
                        "Warning: set export dir not found — not excluded from scan ("
                                + setRoot + ")"));
                setRoot = null;
            }
            List<Path> setRoots = new ArrayList<>();
            if (setRoot != null) {
                setRoots.add(normalizePath(setRoot));
            }

            List<Path> excludePaths = loadExcludePaths(musicRoot, setRoot);

            if (libraryRoots.isEmpty() && setRoots.isEmpty()) {
                int removed = removeMissingSongFiles(Set.of());
                ScanProgress done = new ScanProgress(0, 0, 0, removed, "No library roots to scan");
                notifyProgress(progress, done);
                return done;
            }

            List<Path> rootsToScan = new ArrayList<>(libraryRoots);
            rootsToScan.addAll(setRoots);
            List<Path> files = collectAbcFiles(rootsToScan, excludePaths);

            int filesScanned = 0;
            int songsAdded = 0;
            int songsUpdated = 0;
            Set<String> scannedPaths = new HashSet<>();
            List<DeferredDuplicate> deferred = new ArrayList<>();

            int total = files.size();
            int index = 0;
            for (Path path : files) {
                index++;
                String pathStr = normalizePath(path).toString();
                scannedPaths.add(pathStr);
                PathClass classification = classifyPath(pathStr, libraryRoots, setRoots, excludePaths);

                AbcFileMetadata metadata;
                try {
                    metadata = parser.parse(path, name -> {
                        try {
                            return resolveInstrumentId(name);
                        } catch (SQLException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
                } catch (IOException | RuntimeException ex) {
                    notifyProgress(progress, new ScanProgress(
                            filesScanned, songsAdded, songsUpdated, 0,
                            "Error reading " + path.getFileName() + " (" + index + "/" + total + ")"));
                    continue;
                }

                String mtime = fileMtime(path);
                String fileHash = fileHash(path);

                if (songFileExists(pathStr)) {
                    ensureSongFromParsed(metadata, pathStr, mtime, fileHash, classification, request.defaultStatusId());
                    filesScanned++;
                    songsUpdated++;
                    notifyProgress(progress, new ScanProgress(
                            filesScanned, songsAdded, songsUpdated, 0,
                            "Updated " + path.getFileName() + " (" + index + "/" + total + ")"));
                    continue;
                }

                RenameCandidate rename = findRenameCandidate(pathStr);
                if (rename != null) {
                    relocateSongFile(
                            rename.songId(), rename.oldPath(), pathStr, metadata, mtime, fileHash, classification);
                    filesScanned++;
                    songsUpdated++;
                    notifyProgress(progress, new ScanProgress(
                            filesScanned, songsAdded, songsUpdated, 0,
                            "Renamed to " + path.getFileName() + " (" + index + "/" + total + ")"));
                    continue;
                }

                if (classification.primaryLibrary()) {
                    String normTitle = normalizeTitle(metadata.title());
                    String composers = metadata.composers() == null ? "" : metadata.composers().strip();
                    int partCount = metadata.parts().size();
                    List<Long> existingIds = findSongsByLogicalIdentity(normTitle, composers, partCount);
                    if (!existingIds.isEmpty()) {
                        deferred.add(new DeferredDuplicate(
                                pathStr, metadata, mtime, fileHash, classification, existingIds));
                        notifyProgress(progress, new ScanProgress(
                                filesScanned, songsAdded, songsUpdated, 0,
                                "Duplicate pending " + path.getFileName() + " (" + index + "/" + total + ")"));
                        continue;
                    }
                }

                ensureSongFromParsed(metadata, pathStr, mtime, fileHash, classification, request.defaultStatusId());
                filesScanned++;
                songsAdded++;
                notifyProgress(progress, new ScanProgress(
                        filesScanned, songsAdded, songsUpdated, 0,
                        "Added " + path.getFileName() + " (" + index + "/" + total + ")"));
            }

            for (DeferredDuplicate dup : deferred) {
                Long moveSongId = null;
                String moveOldPath = null;
                for (long sid : dup.existingIds()) {
                    List<String> existingPaths = getFilePathsForSong(sid);
                    List<String> missing = existingPaths.stream()
                            .filter(p -> !scannedPaths.contains(normalizePathString(p)))
                            .toList();
                    if (existingPaths.size() == 1 && missing.size() == 1) {
                        moveSongId = sid;
                        moveOldPath = missing.get(0);
                        break;
                    }
                }
                if (moveSongId != null && moveOldPath != null) {
                    relocateSongFile(
                            moveSongId, moveOldPath, dup.pathStr(), dup.metadata(),
                            dup.mtime(), dup.fileHash(), dup.classification());
                    filesScanned++;
                    songsUpdated++;
                    continue;
                }

                long existingId = dup.existingIds().get(0);
                String existingTitle = loadSongTitle(existingId);
                DuplicateCandidate candidate = new DuplicateCandidate(
                        existingId,
                        existingTitle,
                        dup.pathStr(),
                        dup.metadata().title(),
                        dup.metadata().composers(),
                        dup.metadata().parts().size());
                DuplicateDecision decision = effectiveResolver.resolve(candidate);
                if (decision == null) {
                    decision = DuplicateDecision.KEEP_EXISTING;
                }
                switch (decision) {
                    case KEEP_EXISTING -> {
                        // leave new file unindexed
                    }
                    case KEEP_NEW -> {
                        List<String> paths = getFilePathsForSong(existingId);
                        if (!paths.isEmpty()) {
                            relocateSongFile(
                                    existingId, paths.get(0), dup.pathStr(), dup.metadata(),
                                    dup.mtime(), dup.fileHash(), dup.classification());
                            filesScanned++;
                            songsUpdated++;
                        }
                    }
                    case SEPARATE -> {
                        ensureSongFromParsed(
                                dup.metadata(), dup.pathStr(), dup.mtime(), dup.fileHash(),
                                dup.classification(), request.defaultStatusId());
                        filesScanned++;
                        songsAdded++;
                    }
                }
            }

            int songsRemoved = removeMissingSongFiles(scannedPaths);
            ScanProgress done = new ScanProgress(
                    filesScanned, songsAdded, songsUpdated, songsRemoved,
                    "Scan complete: " + filesScanned + " file(s), "
                            + songsAdded + " added, "
                            + songsUpdated + " updated, "
                            + songsRemoved + " removed");
            notifyProgress(progress, done);
            return done;
        } catch (SQLException | RuntimeException ex) {
            throw new LibraryException("Library scan failed: " + ex.getMessage(), ex);
        }
    }

    private static void notifyProgress(Consumer<ScanProgress> progress, ScanProgress value) {
        if (progress != null) {
            progress.accept(value);
        }
    }

    private static Path resolveSetExportDir(Path setExportDir, Path musicRoot) {
        if (setExportDir == null || musicRoot == null) {
            return null;
        }
        String raw = setExportDir.toString().strip();
        if (raw.isEmpty()) {
            return null;
        }
        Path p = Path.of(raw);
        // Relative values are Music-relative (never process CWD), matching LotroPaths.
        if (!p.isAbsolute()) {
            p = musicRoot.resolve(p);
        }
        try {
            return normalizePath(p);
        } catch (RuntimeException ex) {
            return p;
        }
    }

    private List<Path> loadExcludePaths(Path musicRoot, Path setRoot) throws SQLException {
        List<Path> excludes = new ArrayList<>();
        if (setRoot != null && isUnderMusicRoot(setRoot, musicRoot)) {
            excludes.add(normalizePath(setRoot));
        }
        Connection connection = database.connection();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT path FROM FolderRule WHERE enabled = 1 AND rule_type = 'exclude'");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String path = rs.getString(1);
                if (path == null || path.isBlank()) {
                    continue;
                }
                Path p = Path.of(path.strip());
                try {
                    Path resolved;
                    if (p.isAbsolute()) {
                        resolved = normalizePath(p);
                    } else if (musicRoot != null) {
                        resolved = normalizePath(musicRoot.resolve(p));
                    } else {
                        continue;
                    }
                    // Only Music-tree excludes apply to library scan (Python parity intent).
                    if (isUnderMusicRoot(resolved, musicRoot)) {
                        excludes.add(resolved);
                    }
                } catch (RuntimeException ignored) {
                    // skip unreadable exclude paths
                }
            }
        }
        return excludes;
    }

    private static boolean isUnderMusicRoot(Path path, Path musicRoot) {
        if (path == null || musicRoot == null) {
            return false;
        }
        return pathIsUnder(normalizePath(path).toString(), normalizePath(musicRoot).toString());
    }

    private static List<Path> collectAbcFiles(List<Path> roots, List<Path> excludePaths) {
        List<Path> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".abc"))
                        .forEach(p -> {
                            try {
                                Path normalized = normalizePath(p);
                                String pathStr = normalized.toString();
                                if (pathIsExcluded(pathStr, excludePaths)) {
                                    return;
                                }
                                if (seen.add(pathStr)) {
                                    out.add(normalized);
                                }
                            } catch (RuntimeException ignored) {
                                // skip unreadable paths
                            }
                        });
            } catch (IOException ignored) {
                // skip unreadable roots
            }
        }
        return out;
    }

    private static PathClass classifyPath(
            String path,
            List<Path> libraryRoots,
            List<Path> setRoots,
            List<Path> excludePaths) {
        if (pathIsExcluded(path, excludePaths)) {
            return new PathClass(false, false, true);
        }
        boolean underSet = setRoots.stream().anyMatch(r -> pathIsUnder(path, r.toString()));
        boolean underLib = libraryRoots.stream().anyMatch(r -> pathIsUnder(path, r.toString()));
        if (underSet && !underLib) {
            return new PathClass(false, true, false);
        }
        return new PathClass(true, false, false);
    }

    private static boolean pathIsExcluded(String path, List<Path> excludePaths) {
        for (Path ex : excludePaths) {
            if (pathIsUnder(path, ex.toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean pathIsUnder(String path, String prefix) {
        try {
            Path p = normalizePath(Path.of(path));
            Path pre = normalizePath(Path.of(prefix));
            return p.startsWith(pre);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static Path normalizePath(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static String normalizePathString(String path) {
        try {
            return normalizePath(Path.of(path)).toString();
        } catch (RuntimeException ex) {
            return path == null ? "" : path.strip();
        }
    }

    private static String normalizeTitle(String title) {
        return (title == null ? "" : title).strip().toLowerCase(Locale.ROOT);
    }

    private static String fileMtime(Path path) {
        try {
            double seconds = Files.getLastModifiedTime(path).toMillis() / 1000.0;
            return Double.toString(seconds);
        } catch (IOException ex) {
            return null;
        }
    }

    private static String fileHash(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException ex) {
            return null;
        }
    }

    private boolean songFileExists(String pathStr) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT 1 FROM SongFile WHERE file_path = ?")) {
            statement.setString(1, pathStr);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private RenameCandidate findRenameCandidate(String newPath) throws SQLException {
        Path parent;
        try {
            parent = normalizePath(Path.of(newPath)).getParent();
        } catch (RuntimeException ex) {
            return null;
        }
        if (parent == null) {
            return null;
        }
        List<RenameCandidate> missing = new ArrayList<>();
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT song_id, file_path FROM SongFile");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                long songId = rs.getLong(1);
                String filePath = rs.getString(2);
                try {
                    Path resolved = normalizePath(Path.of(filePath));
                    if (!Objects.equals(resolved.getParent(), parent)) {
                        continue;
                    }
                    if (!Files.isRegularFile(resolved)) {
                        missing.add(new RenameCandidate(songId, filePath));
                    }
                } catch (RuntimeException ignored) {
                    // skip
                }
            }
        }
        return missing.size() == 1 ? missing.get(0) : null;
    }

    private List<Long> findSongsByLogicalIdentity(String normalizedTitle, String composers, int partCount)
            throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                        SELECT id FROM Song
                        WHERE LOWER(TRIM(title)) = ?
                          AND TRIM(composers) = ?
                          AND json_array_length(COALESCE(parts, '[]')) = ?
                        """)) {
            statement.setString(1, normalizedTitle);
            statement.setString(2, composers);
            statement.setInt(3, partCount);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
        }
        return ids;
    }

    private List<String> getFilePathsForSong(long songId) throws SQLException {
        List<String> paths = new ArrayList<>();
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT file_path FROM SongFile WHERE song_id = ?")) {
            statement.setLong(1, songId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    paths.add(rs.getString(1));
                }
            }
        }
        return paths;
    }

    private String loadSongTitle(long songId) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT title FROM Song WHERE id = ?")) {
            statement.setLong(1, songId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    String title = rs.getString(1);
                    return title == null ? "" : title;
                }
            }
        }
        return "";
    }

    private long ensureSongFromParsed(
            AbcFileMetadata metadata,
            String filePath,
            String fileMtime,
            String fileHash,
            PathClass classification,
            Long defaultStatusId) throws SQLException {
        String now = Instant.now().toString();
        String partsJson = partsToJson(metadata);
        Connection connection = database.connection();

        try (PreparedStatement find = connection.prepareStatement(
                "SELECT id, song_id FROM SongFile WHERE file_path = ?")) {
            find.setString(1, filePath);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    long fileId = rs.getLong(1);
                    long songId = rs.getLong(2);
                    updateSongMetadata(songId, metadata, partsJson, now);
                    updateSongFile(
                            fileId, filePath, fileMtime, fileHash, metadata.exportTimestamp(),
                            classification, now, false);
                    return songId;
                }
            }
        }

        Long statusId = resolveDefaultStatusId(defaultStatusId);
        long songId;
        try (PreparedStatement insertSong = connection.prepareStatement(
                """
                        INSERT INTO Song (title, composers, duration_seconds, transcriber, rating, status_id, notes, lyrics,
                           last_played_at, total_plays, parts, created_at, updated_at)
                           VALUES (?, ?, ?, ?, NULL, ?, NULL, NULL, NULL, 0, ?, ?, ?)
                        """,
                Statement.RETURN_GENERATED_KEYS)) {
            insertSong.setString(1, metadata.title());
            insertSong.setString(2, metadata.composers());
            setNullableInt(insertSong, 3, metadata.durationSeconds());
            insertSong.setString(4, metadata.transcriber());
            if (statusId == null) {
                insertSong.setNull(5, Types.INTEGER);
            } else {
                insertSong.setLong(5, statusId);
            }
            insertSong.setString(6, partsJson);
            insertSong.setString(7, now);
            insertSong.setString(8, now);
            insertSong.executeUpdate();
            try (ResultSet keys = insertSong.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Failed to insert Song");
                }
                songId = keys.getLong(1);
            }
        }

        try (PreparedStatement insertFile = connection.prepareStatement(
                """
                        INSERT INTO SongFile (song_id, file_path, file_mtime, file_hash, export_timestamp,
                           is_primary_library, is_set_copy, scan_excluded, created_at, updated_at)
                           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
            insertFile.setLong(1, songId);
            insertFile.setString(2, filePath);
            insertFile.setString(3, fileMtime);
            insertFile.setString(4, fileHash);
            insertFile.setString(5, metadata.exportTimestamp());
            insertFile.setInt(6, classification.primaryLibrary() ? 1 : 0);
            insertFile.setInt(7, classification.setCopy() ? 1 : 0);
            insertFile.setInt(8, classification.scanExcluded() ? 1 : 0);
            insertFile.setString(9, now);
            insertFile.setString(10, now);
            insertFile.executeUpdate();
        }
        return songId;
    }

    private void relocateSongFile(
            long songId,
            String oldPath,
            String newPath,
            AbcFileMetadata metadata,
            String fileMtime,
            String fileHash,
            PathClass classification) throws SQLException {
        String now = Instant.now().toString();
        String partsJson = partsToJson(metadata);
        Connection connection = database.connection();
        Long fileId = null;
        try (PreparedStatement find = connection.prepareStatement(
                "SELECT id FROM SongFile WHERE song_id = ? AND file_path = ?")) {
            find.setLong(1, songId);
            find.setString(2, oldPath);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    fileId = rs.getLong(1);
                }
            }
        }
        if (fileId == null) {
            return;
        }
        updateSongMetadata(songId, metadata, partsJson, now);
        updateSongFile(
                fileId, newPath, fileMtime, fileHash, metadata.exportTimestamp(), classification, now, true);
    }

    private void updateSongMetadata(long songId, AbcFileMetadata metadata, String partsJson, String now)
            throws SQLException {
        try (PreparedStatement update = database.connection().prepareStatement(
                """
                        UPDATE Song SET title = ?, composers = ?, duration_seconds = ?, transcriber = ?,
                           parts = ?, updated_at = ? WHERE id = ?
                        """)) {
            update.setString(1, metadata.title());
            update.setString(2, metadata.composers());
            setNullableInt(update, 3, metadata.durationSeconds());
            update.setString(4, metadata.transcriber());
            update.setString(5, partsJson);
            update.setString(6, now);
            update.setLong(7, songId);
            update.executeUpdate();
        }
    }

    private void updateSongFile(
            long fileId,
            String filePath,
            String fileMtime,
            String fileHash,
            String exportTimestamp,
            PathClass classification,
            String now,
            boolean updatePath) throws SQLException {
        String sql = updatePath
                ? """
                        UPDATE SongFile SET file_path = ?, file_mtime = ?, file_hash = ?, export_timestamp = ?,
                           is_primary_library = ?, is_set_copy = ?, scan_excluded = ?, updated_at = ? WHERE id = ?
                        """
                : """
                        UPDATE SongFile SET file_mtime = ?, file_hash = ?, export_timestamp = ?,
                           is_primary_library = ?, is_set_copy = ?, scan_excluded = ?, updated_at = ? WHERE id = ?
                        """;
        try (PreparedStatement update = database.connection().prepareStatement(sql)) {
            int i = 1;
            if (updatePath) {
                update.setString(i++, filePath);
            }
            update.setString(i++, fileMtime);
            update.setString(i++, fileHash);
            update.setString(i++, exportTimestamp);
            update.setInt(i++, classification.primaryLibrary() ? 1 : 0);
            update.setInt(i++, classification.setCopy() ? 1 : 0);
            update.setInt(i++, classification.scanExcluded() ? 1 : 0);
            update.setString(i++, now);
            update.setLong(i, fileId);
            update.executeUpdate();
        }
    }

    private Long resolveDefaultStatusId(Long preferred) throws SQLException {
        if (preferred != null) {
            try (PreparedStatement statement = database.connection().prepareStatement(
                    "SELECT id FROM Status WHERE id = ?")) {
                statement.setLong(1, preferred);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return preferred;
                    }
                }
            }
        }
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT id FROM Status ORDER BY sort_order ASC, id ASC LIMIT 1");
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return null;
    }

    private long resolveInstrumentId(String name) throws SQLException {
        if (name == null || name.isBlank()) {
            return getOrCreateInstrumentByName("Unknown");
        }
        return getOrCreateInstrumentByName(name.strip());
    }

    private long getOrCreateInstrumentByName(String name) throws SQLException {
        Connection connection = database.connection();
        try (PreparedStatement exact = connection.prepareStatement(
                "SELECT id FROM Instrument WHERE name = ?")) {
            exact.setString(1, name);
            try (ResultSet rs = exact.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        try (PreparedStatement ci = connection.prepareStatement(
                "SELECT id FROM Instrument WHERE LOWER(name) = LOWER(?)")) {
            ci.setString(1, name);
            try (ResultSet rs = ci.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        String canonical = INSTRUMENT_SPELLING_VARIANTS.get(name.toLowerCase(Locale.ROOT));
        if (canonical != null) {
            try (PreparedStatement ci = connection.prepareStatement(
                    "SELECT id FROM Instrument WHERE LOWER(name) = LOWER(?)")) {
                ci.setString(1, canonical);
                try (ResultSet rs = ci.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                }
            }
        }
        try (PreparedStatement all = connection.prepareStatement(
                "SELECT id, alternative_names FROM Instrument");
             ResultSet rs = all.executeQuery()) {
            while (rs.next()) {
                long id = rs.getLong(1);
                String alts = rs.getString(2);
                if (alts == null || alts.isBlank()) {
                    continue;
                }
                for (String alt : alts.split(",")) {
                    if (alt.strip().equalsIgnoreCase(name)) {
                        return id;
                    }
                }
            }
        }
        String now = Instant.now().toString();
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO Instrument (name, alternative_names, created_at, updated_at) VALUES (?, NULL, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, name);
            insert.setString(2, now);
            insert.setString(3, now);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Failed to insert Instrument");
                }
                return keys.getLong(1);
            }
        }
    }

    private String partsToJson(AbcFileMetadata metadata) {
        ArrayNode array = JSON.createArrayNode();
        for (AbcPartMetadata part : metadata.parts()) {
            ObjectNode node = array.addObject();
            node.put("part_number", part.partNumber());
            if (part.partName() == null) {
                node.putNull("part_name");
            } else {
                node.put("part_name", part.partName());
            }
            if (part.instrumentId() == null) {
                node.putNull("instrument_id");
            } else {
                node.put("instrument_id", part.instrumentId());
            }
            if (part.titleFromT() == null) {
                node.putNull("title_from_t");
            } else {
                node.put("title_from_t", part.titleFromT());
            }
        }
        return array.toString();
    }

    private int removeMissingSongFiles(Set<String> currentPaths) throws SQLException {
        Connection connection = database.connection();
        int songsBefore = countSongs();
        if (currentPaths.isEmpty()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM SongFile");
            }
        } else {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TEMP TABLE IF NOT EXISTS _scan_paths (path TEXT PRIMARY KEY)");
                statement.execute("DELETE FROM _scan_paths");
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT OR IGNORE INTO _scan_paths (path) VALUES (?)")) {
                for (String path : currentPaths) {
                    insert.setString(1, path);
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "DELETE FROM SongFile WHERE file_path NOT IN (SELECT path FROM _scan_paths)");
                statement.execute("DROP TABLE IF EXISTS _scan_paths");
            }
        }
        cleanupOrphanedSongs();
        return Math.max(0, songsBefore - countSongs());
    }

    private int countSongs() throws SQLException {
        try (Statement statement = database.connection().createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM Song")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void cleanupOrphanedSongs() throws SQLException {
        Connection connection = database.connection();
        String orphanItems =
                "SELECT id FROM SetlistItem WHERE song_id NOT IN (SELECT song_id FROM SongFile WHERE song_id IS NOT NULL)";
        String orphanLayouts =
                "SELECT id FROM SongLayout WHERE song_id NOT IN (SELECT song_id FROM SongFile WHERE song_id IS NOT NULL)";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "UPDATE Song SET last_setlist_item_id = NULL WHERE last_setlist_item_id IN (" + orphanItems + ")");
            statement.executeUpdate(
                    "DELETE FROM SetlistBandAssignment WHERE setlist_item_id IN (" + orphanItems + ")");
            statement.executeUpdate(
                    "DELETE FROM SetlistItem WHERE song_id NOT IN (SELECT song_id FROM SongFile WHERE song_id IS NOT NULL)");
            statement.executeUpdate(
                    "UPDATE Song SET last_song_layout_id = NULL WHERE last_song_layout_id IN (" + orphanLayouts + ")");
            statement.executeUpdate(
                    "UPDATE SetlistItem SET song_layout_id = NULL WHERE song_layout_id IN (" + orphanLayouts + ")");
            statement.executeUpdate(
                    "DELETE FROM SongLayoutAssignment WHERE song_layout_id IN (" + orphanLayouts + ")");
            statement.executeUpdate(
                    "DELETE FROM SongLayout WHERE id IN (" + orphanLayouts + ")");
            statement.executeUpdate(
                    "DELETE FROM PlayLog WHERE song_id NOT IN (SELECT song_id FROM SongFile WHERE song_id IS NOT NULL)");
            statement.executeUpdate(
                    "DELETE FROM Song WHERE id NOT IN (SELECT song_id FROM SongFile WHERE song_id IS NOT NULL)");
        }
    }

    private static void setNullableInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private record PathClass(boolean primaryLibrary, boolean setCopy, boolean scanExcluded) {
    }

    private record RenameCandidate(long songId, String oldPath) {
    }

    private record DeferredDuplicate(
            String pathStr,
            AbcFileMetadata metadata,
            String mtime,
            String fileHash,
            PathClass classification,
            List<Long> existingIds) {
    }
}

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
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
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
import com.aevoreth.abcmm.domain.scan.CleanupApplyResult;
import com.aevoreth.abcmm.domain.scan.ContentFingerprint;
import com.aevoreth.abcmm.domain.scan.DuplicateAnalysis;
import com.aevoreth.abcmm.domain.scan.DuplicateCleanupPlan;
import com.aevoreth.abcmm.domain.scan.DuplicateCleanupPlanValidator;
import com.aevoreth.abcmm.domain.scan.DuplicateFile;
import com.aevoreth.abcmm.domain.scan.DuplicateGroup;
import com.aevoreth.abcmm.domain.scan.DuplicateGrouping;
import com.aevoreth.abcmm.domain.scan.FileDisposition;
import com.aevoreth.abcmm.domain.scan.FileResolution;
import com.aevoreth.abcmm.domain.scan.FolderDisposition;
import com.aevoreth.abcmm.domain.scan.FolderDuplicateCluster;
import com.aevoreth.abcmm.domain.scan.FolderDuplicateDetector;
import com.aevoreth.abcmm.domain.scan.FolderResolution;
import com.aevoreth.abcmm.domain.scan.LibraryScanService;
import com.aevoreth.abcmm.domain.scan.ScanProgress;
import com.aevoreth.abcmm.domain.scan.ScanRequest;
import com.aevoreth.abcmm.domain.scan.TrashService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Library scanner backed by SQLite. Inventory-first duplicate analysis; does not treat
 * scan order as canonical. Does not close the given {@link SqliteDatabase}.
 */
public final class SqliteLibraryScanService implements LibraryScanService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, String> INSTRUMENT_SPELLING_VARIANTS = Map.of(
            "traveller's trusty fiddle", "Traveler's Trusty Fiddle");

    private final SqliteDatabase database;
    private final AbcMetadataParser parser = new AbcMetadataParser();
    private final TrashService trashService;

    private DuplicateAnalysis lastAnalysis = new DuplicateAnalysis(List.of(), List.of(), 0, 0, null);
    private ScanRequest lastRequest;
    private final Map<String, InventoriedFile> lastInventoryByPath = new LinkedHashMap<>();

    public SqliteLibraryScanService(SqliteDatabase database) {
        this(database, new DesktopTrashService());
    }

    public SqliteLibraryScanService(SqliteDatabase database, TrashService trashService) {
        this.database = Objects.requireNonNull(database, "database");
        this.trashService = Objects.requireNonNull(trashService, "trashService");
    }

    @Override
    public DuplicateAnalysis analyze(ScanRequest request, Consumer<ScanProgress> progress)
            throws LibraryException {
        Objects.requireNonNull(request, "request");
        lastRequest = request;
        lastInventoryByPath.clear();
        try {
            ScanRoots roots = resolveRoots(request, progress);
            if (roots.libraryRoots().isEmpty() && roots.setRoots().isEmpty()) {
                removeMissingSongFiles(Set.of());
                lastAnalysis = new DuplicateAnalysis(List.of(), List.of(), 0, 0, request.defaultStatusId());
                notifyProgress(progress, new ScanProgress(0, 0, 0, 0, "No library roots to scan"));
                return lastAnalysis;
            }

            List<Path> rootsToScan = new ArrayList<>(roots.libraryRoots());
            rootsToScan.addAll(roots.setRoots());
            List<Path> files = collectAbcFiles(rootsToScan, roots.excludePaths());
            files.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));
            // Full inventory paths — never treat a still-present peer as a "moved" file.
            // (e.g. "Main - Copy" sorts before "Main\" and must not steal SongFile rows.)
            Set<String> inventoryPaths = new HashSet<>();
            for (Path path : files) {
                inventoryPaths.add(normalizePath(path).toString());
            }

            int songsUpdated = 0;
            int filesScanned = 0;
            Set<String> scannedPaths = new HashSet<>();
            List<DuplicateGrouping.InventoryPeer> peers = new ArrayList<>();
            List<FolderDuplicateDetector.FolderFileEntry> folderEntries = new ArrayList<>();
            Path musicRoot = roots.musicRoot();

            int total = files.size();
            int index = 0;
            for (Path path : files) {
                index++;
                String pathStr = normalizePath(path).toString();
                scannedPaths.add(pathStr);
                PathClass classification = classifyPath(
                        pathStr, roots.libraryRoots(), roots.setRoots(), roots.excludePaths());

                AbcFileMetadata metadata;
                try {
                    metadata = parseFile(path);
                } catch (IOException | RuntimeException ex) {
                    notifyProgress(progress, new ScanProgress(
                            filesScanned, 0, songsUpdated, 0,
                            "Error reading " + path.getFileName() + " (" + index + "/" + total + ")"));
                    continue;
                }

                String mtime = fileMtime(path);
                String fileHash = fileHash(path);
                String fingerprint = ContentFingerprint.compute(metadata, null);
                Long songId = findSongIdByPath(pathStr);
                boolean indexed = songId != null;

                if (indexed) {
                    ensureSongFromParsed(
                            metadata, pathStr, mtime, fileHash, classification, request.defaultStatusId());
                    filesScanned++;
                    songsUpdated++;
                    notifyProgress(progress, new ScanProgress(
                            filesScanned, 0, songsUpdated, 0,
                            "Updated " + path.getFileName() + " (" + index + "/" + total + ")"));
                } else {
                    RenameCandidate rename = findRenameCandidate(pathStr);
                    if (rename != null) {
                        relocateSongFile(
                                rename.songId(), rename.oldPath(), pathStr, metadata, mtime, fileHash,
                                classification);
                        songId = rename.songId();
                        indexed = true;
                        filesScanned++;
                        songsUpdated++;
                        notifyProgress(progress, new ScanProgress(
                                filesScanned, 0, songsUpdated, 0,
                                "Renamed to " + path.getFileName() + " (" + index + "/" + total + ")"));
                    } else {
                        // Only relocate when the old path is absent from the complete inventory
                        // (true move). Copies that still exist alongside must become duplicate peers.
                        Long moved = tryIdentityMove(
                                metadata, pathStr, mtime, fileHash, classification, inventoryPaths);
                        if (moved != null) {
                            songId = moved;
                            indexed = true;
                            filesScanned++;
                            songsUpdated++;
                            notifyProgress(progress, new ScanProgress(
                                    filesScanned, 0, songsUpdated, 0,
                                    "Moved to " + path.getFileName() + " (" + index + "/" + total + ")"));
                        } else {
                            notifyProgress(progress, new ScanProgress(
                                    filesScanned, 0, songsUpdated, 0,
                                    "Inventoried " + path.getFileName() + " (" + index + "/" + total + ")"));
                        }
                    }
                }

                InventoriedFile inventoried = new InventoriedFile(
                        path, pathStr, metadata, mtime, fileHash, fingerprint, classification, songId, indexed);
                lastInventoryByPath.put(pathStr, inventoried);

                Path libraryRoot = bestLibraryRoot(path, roots.libraryRoots());
                peers.add(new DuplicateGrouping.InventoryPeer(
                        path,
                        metadata,
                        fileHash,
                        fingerprint,
                        songId,
                        indexed,
                        libraryRoot == null ? musicRoot : libraryRoot,
                        classification.primaryLibrary()));

                if (classification.primaryLibrary() && libraryRoot != null) {
                    String identity = DuplicateGrouping.logicalIdentityKey(
                            metadata.title(), metadata.composers(), metadata.parts().size());
                    String relToLibrary;
                    try {
                        relToLibrary = libraryRoot.relativize(path).toString().replace('\\', '/')
                                .toLowerCase(Locale.ROOT);
                    } catch (RuntimeException ex) {
                        relToLibrary = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    }
                    folderEntries.add(new FolderDuplicateDetector.FolderFileEntry(
                            path,
                            relToLibrary,
                            fileHash,
                            identity,
                            metadata.title()));
                }
            }

            // Auto-relocate remaining identity moves already handled per-file above.

            List<DuplicateGrouping.IndexedAssociation> associations = loadIndexedAssociations(musicRoot);
            List<DuplicateGroup> groups = DuplicateGrouping.buildGroups(peers, associations);

            List<FolderDuplicateCluster> clusters = List.of();
            if (musicRoot != null) {
                clusters = FolderDuplicateDetector.detect(musicRoot, folderEntries);
            }

            // Drop file groups fully covered under folders that are exact tree duplicates?
            // Keep both; UI reviews folders first and can demote. Filter file groups under
            // REVIEW_INDIVIDUALLY later at apply time.

            lastAnalysis = new DuplicateAnalysis(
                    groups, clusters, lastInventoryByPath.size(), songsUpdated, request.defaultStatusId());
            notifyProgress(progress, new ScanProgress(
                    filesScanned, 0, songsUpdated, 0,
                    "Analysis complete: " + lastInventoryByPath.size() + " file(s), "
                            + groups.size() + " duplicate group(s), "
                            + clusters.size() + " folder cluster(s)"));
            return lastAnalysis;
        } catch (SQLException | RuntimeException ex) {
            throw new LibraryException("Library analysis failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public CleanupApplyResult apply(DuplicateCleanupPlan plan, Consumer<ScanProgress> progress)
            throws LibraryException {
        Objects.requireNonNull(plan, "plan");
        List<String> validation = DuplicateCleanupPlanValidator.validatePartial(lastAnalysis, plan);
        if (!validation.isEmpty()) {
            throw new LibraryException("Invalid cleanup plan: " + String.join("; ", validation));
        }

        int filesKept = 0;
        int filesKeptSeparate = 0;
        int filesIgnored = 0;
        int filesTrashed = 0;
        int foldersExcluded = 0;
        int foldersRemoved = 0;
        int foldersTrashed = 0;
        List<String> errors = new ArrayList<>();

        try {
            Path musicRoot = lastRequest == null ? null : lastRequest.lotroRoot().resolve("Music");

            // Folder resolutions first
            Set<String> suppressedPathPrefixes = new HashSet<>();
            for (FolderResolution resolution : plan.folderResolutions()) {
                Path folder = normalizePath(resolution.folderPath());
                String folderStr = folder.toString();
                try {
                    switch (resolution.disposition()) {
                        case KEEP_AND_SCAN -> {
                            // no-op
                        }
                        case REVIEW_INDIVIDUALLY -> {
                            // no-op at folder level
                        }
                        case REMOVE_FROM_LIBRARY -> {
                            int removed = deleteSongFilesUnder(folderStr);
                            foldersRemoved++;
                            suppressedPathPrefixes.add(folderStr);
                            notifyProgress(progress, new ScanProgress(
                                    0, 0, 0, removed, "Removed from library: " + folder.getFileName()));
                        }
                        case EXCLUDE_FROM_SCANS -> {
                            String rulePath = musicRelativeRulePath(musicRoot, folder);
                            addExcludeFolderRule(rulePath);
                            deleteSongFilesUnder(folderStr);
                            foldersExcluded++;
                            suppressedPathPrefixes.add(folderStr);
                            notifyProgress(progress, new ScanProgress(
                                    0, 0, 0, 0, "Excluded from scans: " + folder.getFileName()));
                        }
                        case TRASH -> {
                            trashAbcFilesUnder(folder, errors);
                            try {
                                if (Files.isDirectory(folder) && isDirectoryEmptyOfAbc(folder)) {
                                    trashService.moveToTrash(folder);
                                }
                            } catch (Exception ex) {
                                errors.add("Failed to trash folder " + folder + ": " + ex.getMessage());
                            }
                            deleteSongFilesUnder(folderStr);
                            foldersTrashed++;
                            suppressedPathPrefixes.add(folderStr);
                            notifyProgress(progress, new ScanProgress(
                                    0, 0, 0, 0, "Trashed folder: " + folder.getFileName()));
                        }
                    }
                } catch (Exception ex) {
                    errors.add("Folder " + folder + ": " + ex.getMessage());
                }
            }

            Long defaultStatusId = lastAnalysis == null ? null : lastAnalysis.defaultStatusId();
            Connection connection = database.connection();
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                for (FileResolution resolution : plan.fileResolutions()) {
                    Path path = normalizePath(resolution.path());
                    String pathStr = path.toString();
                    if (suppressedPathPrefixes.stream().anyMatch(prefix -> pathHasFolderPrefix(pathStr, prefix))) {
                        continue;
                    }
                    // Skip synthetic / missing indexed stubs that are not real inventory files when ignoring
                    InventoriedFile inventoried = lastInventoryByPath.get(pathStr);
                    try {
                        switch (resolution.disposition()) {
                            case IGNORE -> filesIgnored++;
                            case TRASH -> {
                                if (Files.exists(path)) {
                                    trashService.moveToTrash(path);
                                }
                                deleteSongFileByPath(pathStr);
                                filesTrashed++;
                            }
                            case KEEP_SEPARATE -> {
                                if (inventoried == null) {
                                    if (!Files.isRegularFile(path)) {
                                        errors.add("KEEP_SEPARATE missing file: " + path);
                                        break;
                                    }
                                    AbcFileMetadata metadata = parseFile(path);
                                    ensureSongFromParsed(
                                            metadata, pathStr, fileMtime(path), fileHash(path),
                                            new PathClass(true, false, false), defaultStatusId);
                                } else if (!inventoried.indexed()) {
                                    ensureSongFromParsed(
                                            inventoried.metadata(), pathStr, inventoried.mtime(),
                                            inventoried.fileHash(), inventoried.classification(), defaultStatusId);
                                }
                                filesKeptSeparate++;
                            }
                            case KEEP -> {
                                Long bindSongId = resolution.bindSongId();
                                if (bindSongId == null) {
                                    bindSongId = findSingleSongIdInGroup(resolution.groupId());
                                }
                                AbcFileMetadata metadata = inventoried != null
                                        ? inventoried.metadata()
                                        : parseFile(path);
                                String mtime = inventoried != null ? inventoried.mtime() : fileMtime(path);
                                String hash = inventoried != null ? inventoried.fileHash() : fileHash(path);
                                PathClass classification = inventoried != null
                                        ? inventoried.classification()
                                        : new PathClass(true, false, false);

                                if (bindSongId != null) {
                                    List<String> existingPaths = getFilePathsForSong(bindSongId);
                                    String oldPath = existingPaths.isEmpty() ? null : existingPaths.get(0);
                                    if (oldPath != null && !normalizePathString(oldPath).equals(pathStr)) {
                                        relocateSongFile(
                                                bindSongId, oldPath, pathStr, metadata, mtime, hash, classification);
                                    } else if (oldPath == null) {
                                        // Song exists without file — insert SongFile
                                        insertSongFileOnly(bindSongId, pathStr, mtime, hash, metadata, classification);
                                        updateSongMetadata(bindSongId, metadata, partsToJson(metadata), Instant.now().toString());
                                    } else {
                                        ensureSongFromParsed(
                                                metadata, pathStr, mtime, hash, classification, defaultStatusId);
                                    }
                                } else if (inventoried == null || !inventoried.indexed()) {
                                    ensureSongFromParsed(
                                            metadata, pathStr, mtime, hash, classification, defaultStatusId);
                                }
                                filesKept++;
                            }
                        }
                    } catch (Exception ex) {
                        errors.add(path + ": " + ex.getMessage());
                    }
                }
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }

            String message = String.format(
                    "Cleanup applied: keep=%d separate=%d ignore=%d trash=%d excludeFolders=%d",
                    filesKept, filesKeptSeparate, filesIgnored, filesTrashed, foldersExcluded);
            notifyProgress(progress, new ScanProgress(0, filesKept + filesKeptSeparate, 0, filesTrashed, message));
            return new CleanupApplyResult(
                    filesKept, filesKeptSeparate, filesIgnored, filesTrashed,
                    foldersExcluded, foldersRemoved, foldersTrashed, errors, message);
        } catch (SQLException | RuntimeException ex) {
            throw new LibraryException("Cleanup apply failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public ScanProgress reconcile(ScanRequest request, Consumer<ScanProgress> progress)
            throws LibraryException {
        Objects.requireNonNull(request, "request");
        try {
            ScanRoots roots = resolveRoots(request, progress);
            if (roots.libraryRoots().isEmpty() && roots.setRoots().isEmpty()) {
                int removed = removeMissingSongFiles(Set.of());
                ScanProgress done = new ScanProgress(0, 0, 0, removed, "No library roots to scan");
                notifyProgress(progress, done);
                return done;
            }

            List<Path> rootsToScan = new ArrayList<>(roots.libraryRoots());
            rootsToScan.addAll(roots.setRoots());
            List<Path> files = collectAbcFiles(rootsToScan, roots.excludePaths());
            files.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));

            List<DuplicateGrouping.InventoryPeer> peers = new ArrayList<>();
            Map<String, InventoriedFile> inventory = new LinkedHashMap<>();
            Set<String> scannedPaths = new HashSet<>();
            int songsUpdated = 0;
            int filesScanned = 0;

            int total = files.size();
            int index = 0;
            for (Path path : files) {
                index++;
                String pathStr = normalizePath(path).toString();
                scannedPaths.add(pathStr);
                PathClass classification = classifyPath(
                        pathStr, roots.libraryRoots(), roots.setRoots(), roots.excludePaths());
                AbcFileMetadata metadata;
                try {
                    metadata = parseFile(path);
                } catch (IOException | RuntimeException ex) {
                    continue;
                }
                String mtime = fileMtime(path);
                String fileHash = fileHash(path);
                String fingerprint = ContentFingerprint.compute(metadata, null);
                Long songId = findSongIdByPath(pathStr);
                boolean indexed = songId != null;

                if (indexed) {
                    ensureSongFromParsed(
                            metadata, pathStr, mtime, fileHash, classification, request.defaultStatusId());
                    filesScanned++;
                    songsUpdated++;
                } else {
                    RenameCandidate rename = findRenameCandidate(pathStr);
                    if (rename != null) {
                        relocateSongFile(
                                rename.songId(), rename.oldPath(), pathStr, metadata, mtime, fileHash,
                                classification);
                        songId = rename.songId();
                        indexed = true;
                        filesScanned++;
                        songsUpdated++;
                    }
                }

                inventory.put(pathStr, new InventoriedFile(
                        path, pathStr, metadata, mtime, fileHash, fingerprint, classification, songId, indexed));
                Path libraryRoot = bestLibraryRoot(path, roots.libraryRoots());
                peers.add(new DuplicateGrouping.InventoryPeer(
                        path,
                        metadata,
                        fileHash,
                        fingerprint,
                        songId,
                        indexed,
                        libraryRoot == null ? roots.musicRoot() : libraryRoot,
                        classification.primaryLibrary()));
                notifyProgress(progress, new ScanProgress(
                        filesScanned, 0, songsUpdated, 0,
                        "Reconcile " + path.getFileName() + " (" + index + "/" + total + ")"));
            }

            List<DuplicateGrouping.IndexedAssociation> associations =
                    loadIndexedAssociations(roots.musicRoot());
            List<DuplicateGroup> groups = DuplicateGrouping.buildGroups(peers, associations);
            Set<Path> duplicatePaths = new HashSet<>();
            for (DuplicateGroup group : groups) {
                for (DuplicateFile file : group.files()) {
                    duplicatePaths.add(normalizePath(file.path()));
                }
            }

            int songsAdded = 0;
            for (InventoriedFile item : inventory.values()) {
                if (item.indexed()) {
                    continue;
                }
                if (item.classification().scanExcluded()) {
                    continue;
                }
                // Set-only copies are indexed without duplicate gating
                if (item.classification().setCopy() && !item.classification().primaryLibrary()) {
                    ensureSongFromParsed(
                            item.metadata(), item.pathStr(), item.mtime(), item.fileHash(),
                            item.classification(), request.defaultStatusId());
                    songsAdded++;
                    filesScanned++;
                    continue;
                }
                if (!item.classification().primaryLibrary()) {
                    continue;
                }
                if (duplicatePaths.contains(item.path())) {
                    continue;
                }
                ensureSongFromParsed(
                        item.metadata(), item.pathStr(), item.mtime(), item.fileHash(),
                        item.classification(), request.defaultStatusId());
                songsAdded++;
                filesScanned++;
            }

            int songsRemoved = removeMissingSongFiles(scannedPaths);
            ScanProgress done = new ScanProgress(
                    filesScanned, songsAdded, songsUpdated, songsRemoved,
                    "Reconcile complete: " + filesScanned + " file(s), "
                            + songsAdded + " added, "
                            + songsUpdated + " updated, "
                            + songsRemoved + " removed");
            notifyProgress(progress, done);
            return done;
        } catch (SQLException | RuntimeException ex) {
            throw new LibraryException("Library reconcile failed: " + ex.getMessage(), ex);
        }
    }

    /** Exposed for tests. */
    DuplicateAnalysis lastAnalysis() {
        return lastAnalysis;
    }

    private Long tryIdentityMove(
            AbcFileMetadata metadata,
            String pathStr,
            String mtime,
            String fileHash,
            PathClass classification,
            Set<String> inventoryPaths) throws SQLException {
        String normTitle = normalizeTitle(metadata.title());
        String composers = metadata.composers() == null ? "" : metadata.composers().strip();
        int partCount = metadata.parts().size();
        List<Long> existingIds = findSongsByLogicalIdentity(normTitle, composers, partCount);
        for (long sid : existingIds) {
            List<String> existingPaths = getFilePathsForSong(sid);
            List<String> missing = existingPaths.stream()
                    .filter(p -> !inventoryPaths.contains(normalizePathString(p)))
                    .toList();
            if (existingPaths.size() != 1 || missing.size() != 1) {
                continue;
            }
            String oldPath = missing.get(0);
            // Still on disk ⇒ copy/duplicate, not a move — even if sort order hid it so far.
            try {
                if (Files.isRegularFile(Path.of(oldPath))) {
                    continue;
                }
            } catch (RuntimeException ignored) {
                // treat as missing
            }
            relocateSongFile(sid, oldPath, pathStr, metadata, mtime, fileHash, classification);
            return sid;
        }
        return null;
    }

    /** True when path is the folder or a file/dir strictly inside it (not a sibling like "Main - Copy"). */
    private static boolean pathHasFolderPrefix(String pathStr, String folderPrefix) {
        if (pathStr == null || folderPrefix == null) {
            return false;
        }
        if (pathStr.equals(folderPrefix)) {
            return true;
        }
        return pathStr.startsWith(folderPrefix + "\\") || pathStr.startsWith(folderPrefix + "/");
    }

    private Long findSingleSongIdInGroup(String groupId) {
        if (lastAnalysis == null) {
            return null;
        }
        for (DuplicateGroup group : lastAnalysis.groups()) {
            if (!group.groupId().equals(groupId)) {
                continue;
            }
            List<Long> ids = group.files().stream()
                    .map(DuplicateFile::currentSongId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            return ids.size() == 1 ? ids.get(0) : null;
        }
        return null;
    }

    private ScanRoots resolveRoots(ScanRequest request, Consumer<ScanProgress> progress) throws SQLException {
        Path musicRoot = request.lotroRoot().resolve("Music");
        List<Path> libraryRoots = new ArrayList<>();
        if (Files.isDirectory(musicRoot)) {
            libraryRoots.add(normalizePath(musicRoot));
            musicRoot = normalizePath(musicRoot);
        } else {
            musicRoot = null;
        }

        Path setRoot = resolveSetExportDir(request.setExportDir(), musicRoot == null
                ? request.lotroRoot().resolve("Music")
                : musicRoot);
        Path musicForExclude = musicRoot == null ? request.lotroRoot().resolve("Music") : musicRoot;
        if (setRoot != null && !isUnderMusicRoot(setRoot, musicForExclude)) {
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
        List<Path> excludePaths = loadExcludePaths(musicForExclude, setRoot);
        return new ScanRoots(musicRoot, libraryRoots, setRoots, excludePaths);
    }

    private AbcFileMetadata parseFile(Path path) throws IOException {
        return parser.parse(path, name -> {
            try {
                return resolveInstrumentId(name);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private List<DuplicateGrouping.IndexedAssociation> loadIndexedAssociations(Path libraryRoot)
            throws SQLException {
        List<DuplicateGrouping.IndexedAssociation> out = new ArrayList<>();
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                        SELECT s.id, sf.file_path, s.title, s.composers,
                               json_array_length(COALESCE(s.parts, '[]'))
                        FROM Song s
                        JOIN SongFile sf ON sf.song_id = s.id
                        WHERE sf.is_primary_library = 1 AND sf.scan_excluded = 0
                        """);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                long songId = rs.getLong(1);
                String filePath = rs.getString(2);
                String title = rs.getString(3);
                String composers = rs.getString(4);
                int parts = rs.getInt(5);
                Path path;
                try {
                    path = normalizePath(Path.of(filePath));
                } catch (RuntimeException ex) {
                    continue;
                }
                out.add(new DuplicateGrouping.IndexedAssociation(
                        songId, path, title, composers, parts, libraryRoot));
            }
        }
        return out;
    }

    private void addExcludeFolderRule(String path) throws SQLException {
        String now = Instant.now().toString();
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                        INSERT INTO FolderRule (rule_type, path, enabled, include_in_export, created_at, updated_at)
                        VALUES ('exclude', ?, 1, 0, ?, ?)
                        """)) {
            statement.setString(1, path);
            statement.setString(2, now);
            statement.setString(3, now);
            statement.executeUpdate();
        }
    }

    private static String musicRelativeRulePath(Path musicRoot, Path folder) {
        if (musicRoot != null) {
            try {
                Path rel = normalizePath(musicRoot).relativize(normalizePath(folder));
                return rel.toString().replace('\\', '/');
            } catch (RuntimeException ignored) {
                // fall through
            }
        }
        return normalizePath(folder).toString();
    }

    private int deleteSongFilesUnder(String folderPrefix) throws SQLException {
        int songsBefore = countSongs();
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT id, file_path FROM SongFile");
             ResultSet rs = statement.executeQuery()) {
            List<Long> ids = new ArrayList<>();
            while (rs.next()) {
                String filePath = rs.getString(2);
                if (filePath != null && pathHasFolderPrefix(filePath, folderPrefix)) {
                    ids.add(rs.getLong(1));
                }
            }
            try (PreparedStatement delete = database.connection().prepareStatement(
                    "DELETE FROM SongFile WHERE id = ?")) {
                for (Long id : ids) {
                    delete.setLong(1, id);
                    delete.executeUpdate();
                }
            }
        }
        cleanupOrphanedSongs();
        return Math.max(0, songsBefore - countSongs());
    }

    private void deleteSongFileByPath(String pathStr) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "DELETE FROM SongFile WHERE file_path = ?")) {
            statement.setString(1, pathStr);
            statement.executeUpdate();
        }
        cleanupOrphanedSongs();
    }

    private void trashAbcFilesUnder(Path folder, List<String> errors) {
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            List<Path> abcFiles = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".abc"))
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (Path abc : abcFiles) {
                try {
                    trashService.moveToTrash(abc);
                } catch (Exception ex) {
                    errors.add("Failed to trash " + abc + ": " + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            errors.add("Failed to walk " + folder + ": " + ex.getMessage());
        }
    }

    private static boolean isDirectoryEmptyOfAbc(Path folder) throws IOException {
        try (Stream<Path> walk = Files.walk(folder)) {
            return walk.filter(Files::isRegularFile)
                    .noneMatch(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".abc"));
        }
    }

    private void insertSongFileOnly(
            long songId,
            String filePath,
            String fileMtime,
            String fileHash,
            AbcFileMetadata metadata,
            PathClass classification) throws SQLException {
        String now = Instant.now().toString();
        try (PreparedStatement insertFile = database.connection().prepareStatement(
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
    }

    private Long findSongIdByPath(String pathStr) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT song_id FROM SongFile WHERE file_path = ?")) {
            statement.setString(1, pathStr);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return null;
    }

    private static Path bestLibraryRoot(Path path, List<Path> libraryRoots) {
        Path best = null;
        int bestLen = -1;
        Path normalized = normalizePath(path);
        for (Path root : libraryRoots) {
            Path nr = normalizePath(root);
            if (normalized.startsWith(nr) && nr.toString().length() > bestLen) {
                best = nr;
                bestLen = nr.toString().length();
            }
        }
        return best;
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
            if (part.madeFor() == null) {
                node.putNull("made_for");
            } else {
                node.put("made_for", part.madeFor());
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

    private record InventoriedFile(
            Path path,
            String pathStr,
            AbcFileMetadata metadata,
            String mtime,
            String fileHash,
            String fingerprint,
            PathClass classification,
            Long songId,
            boolean indexed) {
    }

    private record ScanRoots(
            Path musicRoot,
            List<Path> libraryRoots,
            List<Path> setRoots,
            List<Path> excludePaths) {
    }
}

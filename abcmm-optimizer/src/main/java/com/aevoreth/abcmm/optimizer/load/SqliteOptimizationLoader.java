package com.aevoreth.abcmm.optimizer.load;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.aevoreth.abcmm.domain.band.BandLayoutSlotInfo;
import com.aevoreth.abcmm.domain.optimizer.BandOptimizationProfile;
import com.aevoreth.abcmm.domain.optimizer.OccurrencePreferences;
import com.aevoreth.abcmm.domain.band.InstrumentInfo;
import com.aevoreth.abcmm.domain.band.PlayerInfo;
import com.aevoreth.abcmm.domain.band.PlayerInstrumentInfo;
import com.aevoreth.abcmm.domain.band.SongLayoutAssignmentInfo;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.setlist.SetlistBandAssignmentInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistItemInfo;
import com.aevoreth.abcmm.optimizer.AssignmentFeasibility;
import com.aevoreth.abcmm.optimizer.Diagnostic;
import com.aevoreth.abcmm.optimizer.Diagnostics;
import com.aevoreth.abcmm.optimizer.EffectiveAssignmentResolver;
import com.aevoreth.abcmm.optimizer.EffectiveAssignmentResolver.ItemSources;
import com.aevoreth.abcmm.optimizer.EffectiveAssignmentResolver.PlayerPart;
import com.aevoreth.abcmm.optimizer.FeasibilityReport;
import com.aevoreth.abcmm.optimizer.LoadOutcome;
import com.aevoreth.abcmm.optimizer.LoadRequest;
import com.aevoreth.abcmm.optimizer.OptimizationProblem;
import com.aevoreth.abcmm.optimizer.OptimizationRules;
import com.aevoreth.abcmm.optimizer.OptimizationRulesMapper;
import com.aevoreth.abcmm.optimizer.Performer;
import com.aevoreth.abcmm.optimizer.PhysicalInstrumentEquivalence;
import com.aevoreth.abcmm.optimizer.RequiredPart;
import com.aevoreth.abcmm.optimizer.RequiredPartResolver;
import com.aevoreth.abcmm.optimizer.SongOccurrence;
import com.aevoreth.abcmm.storage.SqliteBandRepository;
import com.aevoreth.abcmm.storage.SqliteDatabase;
import com.aevoreth.abcmm.storage.SqliteOptimizationProfileRepository;
import com.aevoreth.abcmm.storage.SqlitePlayerRepository;
import com.aevoreth.abcmm.storage.SqliteSetlistRepository;
import com.aevoreth.abcmm.storage.SqliteSongLayoutRepository;

/**
 * Builds an {@link OptimizationProblem} from an existing database without migrating or writing.
 * Opens {@link SqliteDatabase#openReadOnly(java.nio.file.Path)} only.
 */
public final class SqliteOptimizationLoader {

    public LoadOutcome load(LoadRequest request) {
        if (!Files.isRegularFile(request.database())) {
            return failed(request, Diagnostic.integrity(
                    "missing_database",
                    request.database().toString()));
        }
        try (SqliteDatabase database = SqliteDatabase.openReadOnly(request.database())) {
            return load(database, request);
        } catch (LibraryException ex) {
            return failed(request, Diagnostic.integrity(failureCode(ex), failureDetail(ex)));
        }
    }

    /**
     * Performers who are not on the layout and own at least one required instrument.
     * Ordered by player id. Names are not returned.
     */
    public List<Performer> loadEligibleExtras(Path database, OptimizationProblem problem) throws LibraryException {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(problem, "problem");
        try (SqliteDatabase sqlite = SqliteDatabase.openReadOnly(database)) {
            SqlitePlayerRepository players = new SqlitePlayerRepository(sqlite);
            Set<Long> layout = new HashSet<>();
            for (Performer performer : problem.performers()) {
                layout.add(performer.playerId());
            }
            Set<Long> required = new HashSet<>();
            for (SongOccurrence occurrence : problem.occurrences()) {
                for (RequiredPart part : occurrence.parts()) {
                    if (part.instrumentId() != null) {
                        required.add(part.instrumentId());
                    }
                }
            }
            List<Performer> extras = new ArrayList<>();
            for (PlayerInfo info : players.listPlayers()) {
                if (info.id() <= 0 || layout.contains(info.id())) {
                    continue;
                }
                Set<Long> owned = new HashSet<>();
                for (PlayerInstrumentInfo instrument : players.listPlayerInstruments(info.id())) {
                    if (instrument.hasInstrument()) {
                        owned.add(instrument.instrumentId());
                    }
                }
                if (!ownsRequired(owned, required, problem.equivalentInstrumentIds())) {
                    continue;
                }
                extras.add(new Performer(info.id(), owned));
            }
            extras.sort(Comparator.comparingLong(Performer::playerId));
            return extras;
        }
    }

    private static boolean ownsRequired(
            Set<Long> owned,
            Set<Long> required,
            Map<Long, Set<Long>> equivalence) {
        for (long instrument : owned) {
            for (long needed : required) {
                if (PhysicalInstrumentEquivalence.sameInstrument(instrument, needed, equivalence)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static LoadOutcome load(SqliteDatabase database, LoadRequest request) throws LibraryException {
        SqlitePlayerRepository players = new SqlitePlayerRepository(database);
        SqliteBandRepository bands = new SqliteBandRepository(database);
        SqliteSetlistRepository setlists = new SqliteSetlistRepository(database);
        SqliteSongLayoutRepository songLayouts = new SqliteSongLayoutRepository(database);

        Map<Long, String> instrumentNames = loadInstrumentNames(database, players);
        SetlistInfo setlist = findSetlist(setlists, request.setlistId());
        if (setlist == null) {
            return failed(request, Diagnostic.integrity("setlist_not_found", ""));
        }
        if (setlist.bandLayoutId() == null) {
            return failed(request, Diagnostic.integrity("missing_band_layout", ""));
        }

        List<Long> playerIds = layoutPlayerIds(bands.listSlots(setlist.bandLayoutId()));
        List<Performer> performers = new ArrayList<>();
        for (long playerId : playerIds) {
            Set<Long> owned = new HashSet<>();
            for (PlayerInstrumentInfo info : players.listPlayerInstruments(playerId)) {
                if (info.hasInstrument()) {
                    owned.add(info.instrumentId());
                }
            }
            performers.add(new Performer(playerId, owned));
        }

        List<Diagnostic> loadDiagnostics = new ArrayList<>();
        List<SongOccurrence> occurrences = new ArrayList<>();
        List<ItemSources> sources = new ArrayList<>();
        Map<Long, Map<Integer, Long>> instrumentsByItem = new LinkedHashMap<>();
        List<SetlistItemInfo> items = new ArrayList<>(listItems(database, setlists, setlist.id()));
        items.sort(Comparator.comparingInt(SetlistItemInfo::position).thenComparingLong(SetlistItemInfo::id));
        for (SetlistItemInfo item : items) {
            List<RequiredPart> parts = resolveParts(item, instrumentNames, loadDiagnostics);
            occurrences.add(new SongOccurrence(item.id(), item.songId(), item.position(), parts));
            instrumentsByItem.put(item.id(), instrumentIds(parts));
            sources.add(new ItemSources(
                    item.id(),
                    overrideRows(setlists.listBandAssignments(item.id())),
                    layoutRows(item, songLayouts)));
        }

        EffectiveAssignmentResolver.Result resolved = EffectiveAssignmentResolver.resolve(
                playerIds, sources, instrumentsByItem);
        SqliteOptimizationProfileRepository profiles = new SqliteOptimizationProfileRepository(database);
        Long bandId = bandIdForLayout(database, setlist.bandLayoutId());
        BandOptimizationProfile profile = bandId == null
                ? BandOptimizationProfile.defaults()
                : profiles.getBandProfile(bandId);
        OccurrencePreferences preferences = profiles.listOccurrencePreferences(setlist.id());
        OptimizationRules rules = OptimizationRulesMapper.toRules(profile, preferences);
        OptimizationProblem problem = new OptimizationProblem(
                setlist.id(),
                setlist.locked(),
                setlist.bandLayoutId(),
                performers,
                occurrences,
                resolved.assignments(),
                OptimizationRulesMapper.toPins(profiles.listPins(setlist.id())),
                PhysicalInstrumentEquivalence.equivalentIds(instrumentNames));
        List<Diagnostic> diagnostics = new ArrayList<>(loadDiagnostics);
        diagnostics.addAll(resolved.diagnostics());
        diagnostics.addAll(AssignmentFeasibility.check(problem, request.players()).diagnostics());
        return new LoadOutcome(
                request.setlistId(),
                request.seed(),
                request.budgetSeconds(),
                request.players(),
                problem,
                new FeasibilityReport(diagnostics),
                rules,
                profile.searchBudget());
    }

    private static Long bandIdForLayout(SqliteDatabase database, long layoutId) throws LibraryException {
        String sql = "SELECT band_id FROM BandLayout WHERE id = ?";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, layoutId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return rs.getLong("band_id");
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to read band layout", ex);
        }
    }

    /**
     * {@link com.aevoreth.abcmm.domain.setlist.SetlistRepository#listItems(long)} uses
     * {@code json_array_length}, which aborts the whole query when one song's parts text
     * is malformed. Fall back to a read that keeps every item and lets part parsing report it.
     */
    private static List<SetlistItemInfo> listItems(
            SqliteDatabase database,
            SqliteSetlistRepository setlists,
            long setlistId) throws LibraryException {
        try {
            return setlists.listItems(setlistId);
        } catch (LibraryException ex) {
            if (!failureDetail(ex).toLowerCase(Locale.ROOT).contains("malformed json")) {
                throw ex;
            }
            return listItemsWithoutJsonFunctions(database, setlistId);
        }
    }

    private static List<SetlistItemInfo> listItemsWithoutJsonFunctions(SqliteDatabase database, long setlistId)
            throws LibraryException {
        String sql = """
                SELECT si.id, si.setlist_id, si.song_id, si.position,
                       si.override_change_duration_seconds, si.song_layout_id, s.parts
                FROM SetlistItem si
                JOIN Song s ON s.id = si.song_id
                WHERE si.setlist_id = ?
                ORDER BY si.position
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, setlistId);
            try (ResultSet rs = statement.executeQuery()) {
                List<SetlistItemInfo> items = new ArrayList<>();
                while (rs.next()) {
                    Integer override = rs.getObject("override_change_duration_seconds") == null
                            ? null
                            : rs.getInt("override_change_duration_seconds");
                    Long songLayoutId = rs.getObject("song_layout_id") == null
                            ? null
                            : rs.getLong("song_layout_id");
                    String parts = rs.getString("parts");
                    items.add(new SetlistItemInfo(
                            rs.getLong("id"),
                            rs.getLong("setlist_id"),
                            rs.getLong("song_id"),
                            "",
                            "",
                            null,
                            jsonArrayLength(parts),
                            parts,
                            rs.getInt("position"),
                            override,
                            songLayoutId));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to list setlist items", ex);
        }
    }

    private static int jsonArrayLength(String json) {
        if (json == null) {
            return 0;
        }
        String trimmed = json.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '[') {
            return 0;
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        int count = 0;
        boolean elementOpen = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                if (depth == 1 && !elementOpen) {
                    count++;
                    elementOpen = true;
                }
                continue;
            }
            if (c == '{' || c == '[') {
                if (depth == 1 && !elementOpen) {
                    count++;
                    elementOpen = true;
                }
                depth++;
                continue;
            }
            if (c == '}' || c == ']') {
                depth = Math.max(0, depth - 1);
                if (depth == 0) {
                    break;
                }
                continue;
            }
            if (depth == 1 && c == ',') {
                elementOpen = false;
                continue;
            }
            if (depth == 1 && !elementOpen && !Character.isWhitespace(c)) {
                count++;
                elementOpen = true;
            }
        }
        return count;
    }

    private static List<RequiredPart> resolveParts(
            SetlistItemInfo item,
            Map<Long, String> instrumentNames,
            List<Diagnostic> diagnostics) {
        if (RequiredPartResolver.isUnparsable(item.partsJson())) {
            diagnostics.add(Diagnostic.integrity(
                    "unparsable_parts",
                    Diagnostics.detail("item", item.id())));
            return List.of();
        }
        List<RequiredPart> parts = RequiredPartResolver.resolve(item.partsJson(), instrumentNames);
        if (parts.size() != item.partCount()) {
            diagnostics.add(Diagnostic.integrity(
                    "part_count_mismatch",
                    Diagnostics.detail(
                            "item", item.id(),
                            "expected", item.partCount(),
                            "parsed", parts.size())));
        }
        return parts;
    }

    private static Map<Integer, Long> instrumentIds(List<RequiredPart> parts) {
        Map<Integer, Long> byPart = new LinkedHashMap<>();
        for (RequiredPart part : parts) {
            if (!byPart.containsKey(part.partNumber())) {
                byPart.put(part.partNumber(), part.instrumentId());
            }
        }
        return byPart;
    }

    private static List<PlayerPart> overrideRows(List<SetlistBandAssignmentInfo> rows) {
        List<PlayerPart> parts = new ArrayList<>();
        for (SetlistBandAssignmentInfo row : rows) {
            parts.add(new PlayerPart(row.playerId(), row.partNumber()));
        }
        return parts;
    }

    private static List<PlayerPart> layoutRows(SetlistItemInfo item, SqliteSongLayoutRepository songLayouts)
            throws LibraryException {
        if (item.songLayoutId() == null) {
            return List.of();
        }
        List<PlayerPart> parts = new ArrayList<>();
        for (SongLayoutAssignmentInfo row : songLayouts.listAssignments(item.songLayoutId())) {
            parts.add(new PlayerPart(row.playerId(), row.partNumber()));
        }
        return parts;
    }

    private static List<Long> layoutPlayerIds(List<BandLayoutSlotInfo> slots) {
        List<Long> playerIds = new ArrayList<>();
        for (BandLayoutSlotInfo slot : slots) {
            if (!playerIds.contains(slot.playerId())) {
                playerIds.add(slot.playerId());
            }
        }
        playerIds.sort(Comparator.naturalOrder());
        return playerIds;
    }

    private static SetlistInfo findSetlist(SqliteSetlistRepository setlists, long setlistId)
            throws LibraryException {
        for (SetlistInfo setlist : setlists.listSetlists()) {
            if (setlist.id() == setlistId) {
                return setlist;
            }
        }
        return null;
    }

    /**
     * {@code PlayerRepository.listInstruments()} returns only the fixed player catalog.
     * Alias rows such as a separate Traveller spelling are still real instruments, so the
     * full {@code Instrument} table is merged in for physical equivalence.
     */
    private static Map<Long, String> loadInstrumentNames(
            SqliteDatabase database,
            SqlitePlayerRepository players) throws LibraryException {
        Map<Long, String> names = new LinkedHashMap<>();
        for (InstrumentInfo info : players.listInstruments()) {
            names.put(info.id(), info.name());
        }
        try (Statement statement = database.connection().createStatement();
             ResultSet rs = statement.executeQuery("SELECT id, name FROM Instrument")) {
            while (rs.next()) {
                long id = rs.getLong("id");
                String name = rs.getString("name");
                names.putIfAbsent(id, name == null ? "" : name);
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to list instrument names", ex);
        }
        return names;
    }

    private static LoadOutcome failed(LoadRequest request, Diagnostic diagnostic) {
        return new LoadOutcome(
                request.setlistId(),
                request.seed(),
                request.budgetSeconds(),
                request.players(),
                null,
                new FeasibilityReport(List.of(diagnostic)),
                null,
                null);
    }

    private static String failureCode(LibraryException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("schema")) {
            return "schema_mismatch";
        }
        if (message.contains("not found")) {
            return "missing_database";
        }
        return "database_error";
    }

    private static String failureDetail(LibraryException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        Throwable cause = ex.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return message + " (" + cause.getMessage() + ")";
        }
        return message;
    }
}

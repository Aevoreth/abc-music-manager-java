package com.aevoreth.abcmm.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.optimizer.BandOptimizationProfile;
import com.aevoreth.abcmm.domain.optimizer.InstrumentAffinityWeight;
import com.aevoreth.abcmm.domain.optimizer.ItemKeepWeight;
import com.aevoreth.abcmm.domain.optimizer.OccurrencePreferences;
import com.aevoreth.abcmm.domain.optimizer.OptimizationProfileRepository;
import com.aevoreth.abcmm.domain.optimizer.PartKeepWeight;
import com.aevoreth.abcmm.domain.optimizer.PartPreferenceWeight;
import com.aevoreth.abcmm.domain.optimizer.PlayerSwitchWeight;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * JDBC implementation of {@link OptimizationProfileRepository}.
 * Does not close the shared database. Lifecycle helpers are package-private
 * and do not commit when the caller already holds a transaction.
 */
public final class SqliteOptimizationProfileRepository implements OptimizationProfileRepository {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Set<String> SETTINGS_FIELDS = Set.of(
            "smoothWeight",
            "totalWeight",
            "roleWeight",
            "keepWeight",
            "targetChanges",
            "defaultSwitchMultiplier");

    private final SqliteDatabase database;

    public SqliteOptimizationProfileRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public BandOptimizationProfile getBandProfile(long bandId) throws LibraryException {
        requireRow("SELECT 1 FROM Band WHERE id = ?", bandId, "Band not found: " + bandId);
        String sql = """
                SELECT settings_version, settings_json
                FROM BandOptimizationProfile
                WHERE band_id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, bandId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return BandOptimizationProfile.defaults();
                }
                int version = rs.getInt("settings_version");
                if (version != BandOptimizationProfile.CURRENT_SETTINGS_VERSION) {
                    throw new LibraryException("Unsupported optimization profile version " + version);
                }
                BandOptimizationProfile scalars = readSettings(rs.getString("settings_json"));
                return new BandOptimizationProfile(
                        scalars.settingsVersion(),
                        scalars.smoothWeight(),
                        scalars.totalWeight(),
                        scalars.roleWeight(),
                        scalars.keepWeight(),
                        scalars.targetChanges(),
                        scalars.defaultSwitchMultiplier(),
                        listSwitchWeights(bandId),
                        listAffinities(bandId));
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to read optimization profile", ex);
        }
    }

    @Override
    public void saveBandProfile(long bandId, BandOptimizationProfile profile) throws LibraryException {
        Objects.requireNonNull(profile, "profile");
        if (profile.settingsVersion() != BandOptimizationProfile.CURRENT_SETTINGS_VERSION) {
            throw new LibraryException(
                    "Unsupported optimization profile version " + profile.settingsVersion());
        }
        inTransaction(() -> {
            requireRow("SELECT 1 FROM Band WHERE id = ?", bandId, "Band not found: " + bandId);
            for (PlayerSwitchWeight weight : profile.playerSwitchWeights()) {
                requireRow(
                        "SELECT 1 FROM Player WHERE id = ?",
                        weight.playerId(),
                        "Player not found: " + weight.playerId());
            }
            for (InstrumentAffinityWeight weight : profile.instrumentAffinities()) {
                requireRow(
                        "SELECT 1 FROM Player WHERE id = ?",
                        weight.playerId(),
                        "Player not found: " + weight.playerId());
                requireRow(
                        "SELECT 1 FROM Instrument WHERE id = ?",
                        weight.instrumentId(),
                        "Instrument not found: " + weight.instrumentId());
            }
            writeProfileRow(bandId, profile);
            executeUpdate("DELETE FROM BandPlayerSwitchWeight WHERE band_id = ?", bandId);
            executeUpdate("DELETE FROM BandInstrumentAffinity WHERE band_id = ?", bandId);
            insertSwitchWeights(bandId, profile.playerSwitchWeights());
            insertAffinities(bandId, profile.instrumentAffinities());
        });
    }

    @Override
    public OccurrencePreferences listOccurrencePreferences(long setlistId) throws LibraryException {
        requireRow("SELECT 1 FROM Setlist WHERE id = ?", setlistId, "Setlist not found: " + setlistId);
        try {
            return new OccurrencePreferences(
                    listPartPreferences(setlistId),
                    listItemKeeps(setlistId),
                    listPartKeeps(setlistId));
        } catch (SQLException ex) {
            throw new LibraryException("Failed to read occurrence preferences", ex);
        }
    }

    @Override
    public void replaceOccurrencePreferences(long setlistId, OccurrencePreferences preferences)
            throws LibraryException {
        Objects.requireNonNull(preferences, "preferences");
        inTransaction(() -> {
            requireRow("SELECT 1 FROM Setlist WHERE id = ?", setlistId, "Setlist not found: " + setlistId);
            Set<Long> itemIds = listItemIds(setlistId);
            for (PartPreferenceWeight weight : preferences.partPreferences()) {
                requireItem(itemIds, setlistId, weight.setlistItemId());
                requireRow(
                        "SELECT 1 FROM Player WHERE id = ?",
                        weight.playerId(),
                        "Player not found: " + weight.playerId());
            }
            for (ItemKeepWeight weight : preferences.itemKeepWeights()) {
                requireItem(itemIds, setlistId, weight.setlistItemId());
            }
            for (PartKeepWeight weight : preferences.partKeepWeights()) {
                requireItem(itemIds, setlistId, weight.setlistItemId());
            }
            deleteOccurrenceForSetlist(setlistId);
            insertPartPreferences(preferences.partPreferences());
            insertItemKeeps(preferences.itemKeepWeights());
            insertPartKeeps(preferences.partKeepWeights());
        });
    }

    /**
     * Copy a saved profile and its player-scoped rows. No profile row means the
     * copy also has none and reads as the code default. Occurrence rows stay put.
     */
    void copyBandProfile(long sourceBandId, long newBandId) throws SQLException {
        if (!rowExists("SELECT 1 FROM BandOptimizationProfile WHERE band_id = ?", sourceBandId)) {
            return;
        }
        String now = SqliteTimestamps.now();
        String profileSql = """
                INSERT INTO BandOptimizationProfile
                    (band_id, settings_version, settings_json, created_at, updated_at)
                SELECT ?, settings_version, settings_json, ?, ?
                FROM BandOptimizationProfile
                WHERE band_id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(profileSql)) {
            statement.setLong(1, newBandId);
            statement.setString(2, now);
            statement.setString(3, now);
            statement.setLong(4, sourceBandId);
            statement.executeUpdate();
        }
        String switchSql = """
                INSERT INTO BandPlayerSwitchWeight (band_id, player_id, multiplier)
                SELECT ?, player_id, multiplier
                FROM BandPlayerSwitchWeight
                WHERE band_id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(switchSql)) {
            statement.setLong(1, newBandId);
            statement.setLong(2, sourceBandId);
            statement.executeUpdate();
        }
        String affinitySql = """
                INSERT INTO BandInstrumentAffinity (band_id, player_id, instrument_id, miss_penalty)
                SELECT ?, player_id, instrument_id, miss_penalty
                FROM BandInstrumentAffinity
                WHERE band_id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(affinitySql)) {
            statement.setLong(1, newBandId);
            statement.setLong(2, sourceBandId);
            statement.executeUpdate();
        }
    }

    void deleteBandScoped(long bandId) throws SQLException {
        executeUpdate("DELETE FROM BandPlayerSwitchWeight WHERE band_id = ?", bandId);
        executeUpdate("DELETE FROM BandInstrumentAffinity WHERE band_id = ?", bandId);
        executeUpdate("DELETE FROM BandOptimizationProfile WHERE band_id = ?", bandId);
    }

    void deletePlayerReferences(long playerId) throws SQLException {
        executeUpdate("DELETE FROM BandPlayerSwitchWeight WHERE player_id = ?", playerId);
        executeUpdate("DELETE FROM BandInstrumentAffinity WHERE player_id = ?", playerId);
        executeUpdate("DELETE FROM SetlistPartPreference WHERE player_id = ?", playerId);
    }

    /**
     * Move this band's player-scoped rows onto {@code newPlayerId}. An existing
     * destination switch weight or affinity is kept and the source row is dropped.
     * Part preferences change player only on setlists that use {@code bandLayoutId}.
     */
    void transferPlayer(long bandId, long bandLayoutId, long oldPlayerId, long newPlayerId)
            throws SQLException {
        if (rowExists(
                "SELECT 1 FROM BandPlayerSwitchWeight WHERE band_id = ? AND player_id = ?",
                bandId,
                newPlayerId)) {
            executeUpdate(
                    "DELETE FROM BandPlayerSwitchWeight WHERE band_id = ? AND player_id = ?",
                    bandId,
                    oldPlayerId);
        } else {
            executeUpdate(
                    """
                    UPDATE BandPlayerSwitchWeight
                    SET player_id = ?
                    WHERE band_id = ? AND player_id = ?
                    """,
                    newPlayerId,
                    bandId,
                    oldPlayerId);
        }

        Set<Long> keptInstruments = new HashSet<>();
        String destinationSql = """
                SELECT instrument_id FROM BandInstrumentAffinity
                WHERE band_id = ? AND player_id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(destinationSql)) {
            statement.setLong(1, bandId);
            statement.setLong(2, newPlayerId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    keptInstruments.add(rs.getLong(1));
                }
            }
        }
        String sourceSql = """
                SELECT instrument_id FROM BandInstrumentAffinity
                WHERE band_id = ? AND player_id = ?
                """;
        List<Long> sourceInstruments = new ArrayList<>();
        try (PreparedStatement statement = database.connection().prepareStatement(sourceSql)) {
            statement.setLong(1, bandId);
            statement.setLong(2, oldPlayerId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    sourceInstruments.add(rs.getLong(1));
                }
            }
        }
        for (Long instrumentId : sourceInstruments) {
            if (keptInstruments.contains(instrumentId)) {
                executeUpdate(
                        """
                        DELETE FROM BandInstrumentAffinity
                        WHERE band_id = ? AND player_id = ? AND instrument_id = ?
                        """,
                        bandId,
                        oldPlayerId,
                        instrumentId);
            } else {
                executeUpdate(
                        """
                        UPDATE BandInstrumentAffinity
                        SET player_id = ?
                        WHERE band_id = ? AND player_id = ? AND instrument_id = ?
                        """,
                        newPlayerId,
                        bandId,
                        oldPlayerId,
                        instrumentId);
            }
        }

        executeUpdate(
                """
                UPDATE SetlistPartPreference
                SET player_id = ?
                WHERE player_id = ?
                  AND setlist_item_id IN (
                      SELECT si.id
                      FROM SetlistItem si
                      JOIN Setlist s ON s.id = si.setlist_id
                      WHERE s.band_layout_id = ?
                  )
                """,
                newPlayerId,
                oldPlayerId,
                bandLayoutId);
    }

    void copyOccurrencePreferences(long sourceItemId, long newItemId) throws SQLException {
        String partSql = """
                INSERT INTO SetlistPartPreference (setlist_item_id, part_number, player_id, penalty)
                SELECT ?, part_number, player_id, penalty
                FROM SetlistPartPreference
                WHERE setlist_item_id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(partSql)) {
            statement.setLong(1, newItemId);
            statement.setLong(2, sourceItemId);
            statement.executeUpdate();
        }
        String itemSql = """
                INSERT INTO SetlistItemKeepWeight (setlist_item_id, multiplier)
                SELECT ?, multiplier FROM SetlistItemKeepWeight WHERE setlist_item_id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(itemSql)) {
            statement.setLong(1, newItemId);
            statement.setLong(2, sourceItemId);
            statement.executeUpdate();
        }
        String keepSql = """
                INSERT INTO SetlistPartKeepWeight (setlist_item_id, part_number, multiplier)
                SELECT ?, part_number, multiplier
                FROM SetlistPartKeepWeight
                WHERE setlist_item_id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(keepSql)) {
            statement.setLong(1, newItemId);
            statement.setLong(2, sourceItemId);
            statement.executeUpdate();
        }
    }

    void deleteOccurrenceForItem(long itemId) throws SQLException {
        executeUpdate("DELETE FROM SetlistPartPreference WHERE setlist_item_id = ?", itemId);
        executeUpdate("DELETE FROM SetlistItemKeepWeight WHERE setlist_item_id = ?", itemId);
        executeUpdate("DELETE FROM SetlistPartKeepWeight WHERE setlist_item_id = ?", itemId);
    }

    void deleteOccurrenceForSetlist(long setlistId) throws SQLException {
        String itemIds = "SELECT id FROM SetlistItem WHERE setlist_id = ?";
        executeUpdate(
                "DELETE FROM SetlistPartPreference WHERE setlist_item_id IN (" + itemIds + ")",
                setlistId);
        executeUpdate(
                "DELETE FROM SetlistItemKeepWeight WHERE setlist_item_id IN (" + itemIds + ")",
                setlistId);
        executeUpdate(
                "DELETE FROM SetlistPartKeepWeight WHERE setlist_item_id IN (" + itemIds + ")",
                setlistId);
    }

    /**
     * Occurrence rows for setlist items whose song has no file. Used when a library
     * scan drops those items, before the item row itself is deleted.
     */
    void deleteOccurrenceForOrphanItems() throws SQLException {
        String orphanItems =
                "SELECT id FROM SetlistItem WHERE song_id NOT IN "
                        + "(SELECT song_id FROM SongFile WHERE song_id IS NOT NULL)";
        try (Statement statement = database.connection().createStatement()) {
            statement.executeUpdate(
                    "DELETE FROM SetlistPartPreference WHERE setlist_item_id IN (" + orphanItems + ")");
            statement.executeUpdate(
                    "DELETE FROM SetlistItemKeepWeight WHERE setlist_item_id IN (" + orphanItems + ")");
            statement.executeUpdate(
                    "DELETE FROM SetlistPartKeepWeight WHERE setlist_item_id IN (" + orphanItems + ")");
        }
    }

    private void writeProfileRow(long bandId, BandOptimizationProfile profile)
            throws SQLException, LibraryException {
        String json = writeSettings(profile);
        String now = SqliteTimestamps.now();
        if (rowExists("SELECT 1 FROM BandOptimizationProfile WHERE band_id = ?", bandId)) {
            String sql = """
                    UPDATE BandOptimizationProfile
                    SET settings_version = ?, settings_json = ?, updated_at = ?
                    WHERE band_id = ?
                    """;
            try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
                statement.setInt(1, profile.settingsVersion());
                statement.setString(2, json);
                statement.setString(3, now);
                statement.setLong(4, bandId);
                statement.executeUpdate();
            }
            return;
        }
        String sql = """
                INSERT INTO BandOptimizationProfile
                    (band_id, settings_version, settings_json, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, bandId);
            statement.setInt(2, profile.settingsVersion());
            statement.setString(3, json);
            statement.setString(4, now);
            statement.setString(5, now);
            statement.executeUpdate();
        }
    }

    private void insertSwitchWeights(long bandId, List<PlayerSwitchWeight> weights) throws SQLException {
        String sql = """
                INSERT INTO BandPlayerSwitchWeight (band_id, player_id, multiplier)
                VALUES (?, ?, ?)
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            for (PlayerSwitchWeight weight : weights) {
                statement.setLong(1, bandId);
                statement.setLong(2, weight.playerId());
                statement.setDouble(3, weight.multiplier());
                statement.executeUpdate();
            }
        }
    }

    private void insertAffinities(long bandId, List<InstrumentAffinityWeight> weights) throws SQLException {
        String sql = """
                INSERT INTO BandInstrumentAffinity
                    (band_id, player_id, instrument_id, miss_penalty)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            for (InstrumentAffinityWeight weight : weights) {
                statement.setLong(1, bandId);
                statement.setLong(2, weight.playerId());
                statement.setLong(3, weight.instrumentId());
                statement.setDouble(4, weight.missPenalty());
                statement.executeUpdate();
            }
        }
    }

    private List<PlayerSwitchWeight> listSwitchWeights(long bandId) throws SQLException {
        String sql = """
                SELECT player_id, multiplier
                FROM BandPlayerSwitchWeight
                WHERE band_id = ?
                ORDER BY player_id
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, bandId);
            try (ResultSet rs = statement.executeQuery()) {
                List<PlayerSwitchWeight> weights = new ArrayList<>();
                while (rs.next()) {
                    weights.add(new PlayerSwitchWeight(rs.getLong("player_id"), rs.getDouble("multiplier")));
                }
                return weights;
            }
        }
    }

    private List<InstrumentAffinityWeight> listAffinities(long bandId) throws SQLException {
        String sql = """
                SELECT player_id, instrument_id, miss_penalty
                FROM BandInstrumentAffinity
                WHERE band_id = ?
                ORDER BY player_id, instrument_id
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, bandId);
            try (ResultSet rs = statement.executeQuery()) {
                List<InstrumentAffinityWeight> weights = new ArrayList<>();
                while (rs.next()) {
                    weights.add(new InstrumentAffinityWeight(
                            rs.getLong("player_id"),
                            rs.getLong("instrument_id"),
                            rs.getDouble("miss_penalty")));
                }
                return weights;
            }
        }
    }

    private List<PartPreferenceWeight> listPartPreferences(long setlistId) throws SQLException {
        String sql = """
                SELECT p.setlist_item_id, p.part_number, p.player_id, p.penalty
                FROM SetlistPartPreference p
                JOIN SetlistItem si ON si.id = p.setlist_item_id
                WHERE si.setlist_id = ?
                ORDER BY p.setlist_item_id, p.part_number
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, setlistId);
            try (ResultSet rs = statement.executeQuery()) {
                List<PartPreferenceWeight> weights = new ArrayList<>();
                while (rs.next()) {
                    weights.add(new PartPreferenceWeight(
                            rs.getLong("setlist_item_id"),
                            rs.getInt("part_number"),
                            rs.getLong("player_id"),
                            rs.getDouble("penalty")));
                }
                return weights;
            }
        }
    }

    private List<ItemKeepWeight> listItemKeeps(long setlistId) throws SQLException {
        String sql = """
                SELECT k.setlist_item_id, k.multiplier
                FROM SetlistItemKeepWeight k
                JOIN SetlistItem si ON si.id = k.setlist_item_id
                WHERE si.setlist_id = ?
                ORDER BY k.setlist_item_id
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, setlistId);
            try (ResultSet rs = statement.executeQuery()) {
                List<ItemKeepWeight> weights = new ArrayList<>();
                while (rs.next()) {
                    weights.add(new ItemKeepWeight(rs.getLong("setlist_item_id"), rs.getDouble("multiplier")));
                }
                return weights;
            }
        }
    }

    private List<PartKeepWeight> listPartKeeps(long setlistId) throws SQLException {
        String sql = """
                SELECT k.setlist_item_id, k.part_number, k.multiplier
                FROM SetlistPartKeepWeight k
                JOIN SetlistItem si ON si.id = k.setlist_item_id
                WHERE si.setlist_id = ?
                ORDER BY k.setlist_item_id, k.part_number
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, setlistId);
            try (ResultSet rs = statement.executeQuery()) {
                List<PartKeepWeight> weights = new ArrayList<>();
                while (rs.next()) {
                    weights.add(new PartKeepWeight(
                            rs.getLong("setlist_item_id"),
                            rs.getInt("part_number"),
                            rs.getDouble("multiplier")));
                }
                return weights;
            }
        }
    }

    private void insertPartPreferences(List<PartPreferenceWeight> weights) throws SQLException {
        String sql = """
                INSERT INTO SetlistPartPreference (setlist_item_id, part_number, player_id, penalty)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            for (PartPreferenceWeight weight : weights) {
                statement.setLong(1, weight.setlistItemId());
                statement.setInt(2, weight.partNumber());
                statement.setLong(3, weight.playerId());
                statement.setDouble(4, weight.penalty());
                statement.executeUpdate();
            }
        }
    }

    private void insertItemKeeps(List<ItemKeepWeight> weights) throws SQLException {
        String sql = """
                INSERT INTO SetlistItemKeepWeight (setlist_item_id, multiplier) VALUES (?, ?)
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            for (ItemKeepWeight weight : weights) {
                statement.setLong(1, weight.setlistItemId());
                statement.setDouble(2, weight.multiplier());
                statement.executeUpdate();
            }
        }
    }

    private void insertPartKeeps(List<PartKeepWeight> weights) throws SQLException {
        String sql = """
                INSERT INTO SetlistPartKeepWeight (setlist_item_id, part_number, multiplier)
                VALUES (?, ?, ?)
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            for (PartKeepWeight weight : weights) {
                statement.setLong(1, weight.setlistItemId());
                statement.setInt(2, weight.partNumber());
                statement.setDouble(3, weight.multiplier());
                statement.executeUpdate();
            }
        }
    }

    private Set<Long> listItemIds(long setlistId) throws SQLException {
        Set<Long> ids = new HashSet<>();
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT id FROM SetlistItem WHERE setlist_id = ?")) {
            statement.setLong(1, setlistId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
        }
        return ids;
    }

    private static void requireItem(Set<Long> itemIds, long setlistId, long itemId) throws LibraryException {
        if (!itemIds.contains(itemId)) {
            throw new LibraryException("Setlist item " + itemId + " is not in setlist " + setlistId);
        }
    }

    private void requireRow(String sql, long id, String message) throws LibraryException {
        try {
            if (!rowExists(sql, id)) {
                throw new LibraryException(message);
            }
        } catch (SQLException ex) {
            throw new LibraryException("Failed to read optimizer profile", ex);
        }
    }

    private boolean rowExists(String sql, long id) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean rowExists(String sql, long first, long second) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, first);
            statement.setLong(2, second);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void executeUpdate(String sql, long id) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    private void executeUpdate(String sql, long first, long second) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, first);
            statement.setLong(2, second);
            statement.executeUpdate();
        }
    }

    private void executeUpdate(String sql, long first, long second, long third) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, first);
            statement.setLong(2, second);
            statement.setLong(3, third);
            statement.executeUpdate();
        }
    }

    private void executeUpdate(String sql, long first, long second, long third, long fourth) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, first);
            statement.setLong(2, second);
            statement.setLong(3, third);
            statement.setLong(4, fourth);
            statement.executeUpdate();
        }
    }

    private void inTransaction(SqlWork work) throws LibraryException {
        Connection connection = database.connection();
        boolean previousAutoCommit;
        try {
            previousAutoCommit = connection.getAutoCommit();
            if (!previousAutoCommit) {
                work.run();
                return;
            }
            connection.setAutoCommit(false);
        } catch (SQLException ex) {
            throw new LibraryException("Failed to update optimizer profile", ex);
        }
        try {
            work.run();
            connection.commit();
        } catch (LibraryException ex) {
            rollback(connection);
            throw ex;
        } catch (SQLException ex) {
            rollback(connection);
            throw new LibraryException("Failed to update optimizer profile", ex);
        } catch (RuntimeException ex) {
            rollback(connection);
            throw ex;
        } finally {
            try {
                connection.setAutoCommit(previousAutoCommit);
            } catch (SQLException ignored) {
                // best-effort
            }
        }
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // best-effort
        }
    }

    private static String writeSettings(BandOptimizationProfile profile) throws LibraryException {
        ObjectNode node = JSON.createObjectNode();
        node.put("smoothWeight", profile.smoothWeight());
        node.put("totalWeight", profile.totalWeight());
        node.put("roleWeight", profile.roleWeight());
        node.put("keepWeight", profile.keepWeight());
        node.put("targetChanges", profile.targetChanges());
        node.put("defaultSwitchMultiplier", profile.defaultSwitchMultiplier());
        try {
            return JSON.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new LibraryException("Failed to write optimization profile", ex);
        }
    }

    private static BandOptimizationProfile readSettings(String json) throws LibraryException {
        if (json == null || json.isBlank()) {
            throw new LibraryException("Optimization profile settings are empty");
        }
        JsonNode node;
        try {
            node = JSON.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new LibraryException("Optimization profile settings are not JSON", ex);
        }
        if (!node.isObject()) {
            throw new LibraryException("Optimization profile settings are not an object");
        }
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String name = fields.next();
            if (!SETTINGS_FIELDS.contains(name)) {
                throw new LibraryException("Unknown optimization profile field " + name);
            }
        }
        for (String required : SETTINGS_FIELDS) {
            if (!node.has(required)) {
                throw new LibraryException("Missing optimization profile field " + required);
            }
        }
        JsonNode target = node.get("targetChanges");
        if (!target.isIntegralNumber() || target.intValue() < 0) {
            throw new LibraryException("Invalid optimization profile field targetChanges");
        }
        return new BandOptimizationProfile(
                BandOptimizationProfile.CURRENT_SETTINGS_VERSION,
                readWeight(node, "smoothWeight"),
                readWeight(node, "totalWeight"),
                readWeight(node, "roleWeight"),
                readWeight(node, "keepWeight"),
                target.intValue(),
                readWeight(node, "defaultSwitchMultiplier"),
                List.of(),
                List.of());
    }

    private static double readWeight(JsonNode node, String field) throws LibraryException {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new LibraryException("Invalid optimization profile field " + field);
        }
        double number = value.doubleValue();
        if (!Double.isFinite(number) || number < 0.0d) {
            throw new LibraryException("Invalid optimization profile field " + field);
        }
        return number;
    }

    @FunctionalInterface
    private interface SqlWork {
        void run() throws SQLException, LibraryException;
    }
}

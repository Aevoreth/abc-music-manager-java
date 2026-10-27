package com.aevoreth.abcmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.optimizer.BandOptimizationProfile;
import com.aevoreth.abcmm.domain.optimizer.InstrumentAffinityWeight;
import com.aevoreth.abcmm.domain.optimizer.ItemKeepWeight;
import com.aevoreth.abcmm.domain.optimizer.OccurrencePreferences;
import com.aevoreth.abcmm.domain.optimizer.PartKeepWeight;
import com.aevoreth.abcmm.domain.optimizer.AssignmentPin;
import com.aevoreth.abcmm.domain.optimizer.PartPreferenceWeight;
import com.aevoreth.abcmm.domain.optimizer.PlayerSwitchWeight;
import com.aevoreth.abcmm.domain.optimizer.SearchBudgetPreset;
import com.aevoreth.abcmm.domain.setlist.SetlistItemInfo;

class SqliteOptimizationProfileRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTripIsolatesBandsAndLeavesDefaultsUnsaved() throws Exception {
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(tempDir.resolve("profiles.sqlite"))) {
            SqliteBandRepository bands = new SqliteBandRepository(database);
            SqlitePlayerRepository players = new SqlitePlayerRepository(database);
            SqliteOptimizationProfileRepository profiles = new SqliteOptimizationProfileRepository(database);
            long first = bands.addBand("First", null);
            long second = bands.addBand("Second", null);
            long playerA = players.addPlayer("Ada", null, null);
            long playerB = players.addPlayer("Bea", null, null);
            long instrumentA = instrumentId(database, 0);
            long instrumentB = instrumentId(database, 1);

            assertEquals(BandOptimizationProfile.defaults(), profiles.getBandProfile(first));
            assertEquals(0, count(database, "BandOptimizationProfile"));

            BandOptimizationProfile saved = new BandOptimizationProfile(
                    1,
                    0.0d,
                    4.0d,
                    1.0d,
                    2.0d,
                    3,
                    1.0d,
                    List.of(new PlayerSwitchWeight(playerB, 2.5d), new PlayerSwitchWeight(playerA, 0.0d)),
                    List.of(new InstrumentAffinityWeight(playerA, instrumentB, 1.5d)),
                    SearchBudgetPreset.NORMAL);
            profiles.saveBandProfile(first, saved);
            assertEquals(saved, profiles.getBandProfile(first));
            assertEquals(BandOptimizationProfile.defaults(), profiles.getBandProfile(second));
            assertEquals(1, count(database, "BandOptimizationProfile"));

            profiles.saveBandProfile(second, new BandOptimizationProfile(
                    1, 9.0d, 1.0d, 1.0d, 1.0d, 2, 1.0d, List.of(), List.of(), SearchBudgetPreset.NORMAL));
            assertEquals(0.0d, profiles.getBandProfile(first).smoothWeight());
            assertEquals(9.0d, profiles.getBandProfile(second).smoothWeight());
            assertEquals(playerA, profiles.getBandProfile(first).playerSwitchWeights().get(0).playerId());
            assertEquals(instrumentB, profiles.getBandProfile(first).instrumentAffinities().get(0).instrumentId());
        }
    }

    @Test
    void rejectsUnknownDocumentVersionUnknownFieldsAndMissingPlayers() throws Exception {
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(tempDir.resolve("reject.sqlite"))) {
            SqliteBandRepository bands = new SqliteBandRepository(database);
            SqliteOptimizationProfileRepository profiles = new SqliteOptimizationProfileRepository(database);
            long bandId = bands.addBand("Band", null);
            String now = SqliteTimestamps.now();
            try (PreparedStatement statement = database.connection().prepareStatement(
                    """
                    INSERT INTO BandOptimizationProfile
                        (band_id, settings_version, settings_json, created_at, updated_at)
                    VALUES (?, 99, '{}', ?, ?)
                    """)) {
                statement.setLong(1, bandId);
                statement.setString(2, now);
                statement.setString(3, now);
                statement.executeUpdate();
            }
            LibraryException version = assertThrows(LibraryException.class, () -> profiles.getBandProfile(bandId));
            assertTrue(version.getMessage().contains("Unsupported optimization profile version 99"));

            try (PreparedStatement statement = database.connection().prepareStatement(
                    """
                    UPDATE BandOptimizationProfile
                    SET settings_version = 1, settings_json = ?
                    WHERE band_id = ?
                    """)) {
                statement.setString(1, """
                        {"smoothWeight":4,"totalWeight":1,"roleWeight":1,"keepWeight":1,
                         "targetChanges":2,"defaultSwitchMultiplier":1,"extra":true}
                        """);
                statement.setLong(2, bandId);
                statement.executeUpdate();
            }
            LibraryException field = assertThrows(LibraryException.class, () -> profiles.getBandProfile(bandId));
            assertTrue(field.getMessage().contains("Unknown optimization profile field extra"));

            LibraryException missing = assertThrows(
                    LibraryException.class,
                    () -> profiles.saveBandProfile(bandId, new BandOptimizationProfile(
                            1,
                            4.0d,
                            1.0d,
                            1.0d,
                            1.0d,
                            2,
                            1.0d,
                            List.of(new PlayerSwitchWeight(999_999L, 1.0d)),
                            List.of(),
                            SearchBudgetPreset.NORMAL)));
            assertTrue(missing.getMessage().contains("Player not found"));
            assertEquals(0, count(database, "BandPlayerSwitchWeight"));
            try (Statement statement = database.connection().createStatement();
                 ResultSet rs = statement.executeQuery(
                         "SELECT settings_version, settings_json FROM BandOptimizationProfile WHERE band_id = "
                                 + bandId)) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
                assertTrue(rs.getString(2).contains("extra"));
            }
        }
    }

    @Test
    void duplicateBandCopiesProfileAndDuplicateSetlistCopiesOccurrences() throws Exception {
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(tempDir.resolve("copy.sqlite"))) {
            SqliteBandRepository bands = new SqliteBandRepository(database);
            SqlitePlayerRepository players = new SqlitePlayerRepository(database);
            SqliteSetlistRepository setlists = new SqliteSetlistRepository(database);
            SqliteOptimizationProfileRepository profiles = new SqliteOptimizationProfileRepository(database);
            long bandId = bands.addBand("Band", null);
            long playerId = players.addPlayer("Ada", null, null);
            profiles.saveBandProfile(bandId, new BandOptimizationProfile(
                    1,
                    6.0d,
                    1.0d,
                    1.0d,
                    1.0d,
                    2,
                    1.0d,
                    List.of(new PlayerSwitchWeight(playerId, 3.0d)),
                    List.of(),
                    SearchBudgetPreset.THOROUGH));
            long songId = insertSong(database, "Alpha");
            long setlistId = setlists.addSetlist("Friday", null);
            long itemId = setlists.addItem(setlistId, songId, 0, null, null);
            profiles.replaceOccurrencePreferences(setlistId, preferences(itemId, playerId, 4.0d));

            long copyBandId = bands.duplicateBand(bandId);
            BandOptimizationProfile copy = profiles.getBandProfile(copyBandId);
            assertEquals(6.0d, copy.smoothWeight());
            assertEquals(SearchBudgetPreset.THOROUGH, copy.searchBudget());
            assertEquals(3.0d, copy.playerSwitchWeights().get(0).multiplier());
            assertEquals(playerId, copy.playerSwitchWeights().get(0).playerId());
            assertEquals(1, count(database, "SetlistPartPreference"));

            long copySetlistId = setlists.duplicateSetlist(
                    setlistId, "Friday copy", null, false, null, null, null, null, null, null);
            List<SetlistItemInfo> copiedItems = setlists.listItems(copySetlistId);
            assertEquals(1, copiedItems.size());
            OccurrencePreferences copied = profiles.listOccurrencePreferences(copySetlistId);
            assertEquals(copiedItems.get(0).id(), copied.partPreferences().get(0).setlistItemId());
            assertEquals(4.0d, copied.partPreferences().get(0).penalty());
            assertEquals(1.5d, copied.itemKeepWeights().get(0).multiplier());
            assertEquals(2.5d, copied.partKeepWeights().get(0).multiplier());
            assertEquals(itemId, profiles.listOccurrencePreferences(setlistId).partPreferences().get(0).setlistItemId());

            long targetId = setlists.addSetlist("Saturday", null);
            long otherSong = insertSong(database, "Beta");
            setlists.addItem(targetId, otherSong, 0, null, null);
            setlists.mergeSetlistSongs(targetId, setlistId, false);
            assertEquals(OccurrencePreferences.empty(), profiles.listOccurrencePreferences(targetId));
            assertEquals(1, profiles.listOccurrencePreferences(setlistId).partPreferences().size());
        }
    }

    @Test
    void replacePlayerKeepsDestinationRowsAndLeavesOtherLayouts() throws Exception {
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(tempDir.resolve("replace.sqlite"))) {
            SqliteBandRepository bands = new SqliteBandRepository(database);
            SqlitePlayerRepository players = new SqlitePlayerRepository(database);
            SqliteSetlistRepository setlists = new SqliteSetlistRepository(database);
            SqliteOptimizationProfileRepository profiles = new SqliteOptimizationProfileRepository(database);
            long bandId = bands.addBand("Band", null);
            long layoutId = bands.getOrCreatePrimaryLayout(bandId).id();
            long otherBandId = bands.addBand("Other", null);
            long otherLayoutId = bands.getOrCreatePrimaryLayout(otherBandId).id();
            long oldPlayer = players.addPlayer("Ada", null, null);
            long newPlayer = players.addPlayer("Bea", null, null);
            bands.setSlot(layoutId, oldPlayer, 0, 0, 9, 7);
            long instrumentA = instrumentId(database, 0);
            long instrumentB = instrumentId(database, 1);
            profiles.saveBandProfile(bandId, new BandOptimizationProfile(
                    1,
                    4.0d,
                    1.0d,
                    1.0d,
                    1.0d,
                    2,
                    1.0d,
                    List.of(new PlayerSwitchWeight(oldPlayer, 2.0d), new PlayerSwitchWeight(newPlayer, 5.0d)),
                    List.of(
                            new InstrumentAffinityWeight(oldPlayer, instrumentA, 3.0d),
                            new InstrumentAffinityWeight(oldPlayer, instrumentB, 4.0d),
                            new InstrumentAffinityWeight(newPlayer, instrumentA, 9.0d)),
                    SearchBudgetPreset.NORMAL));

            long songId = insertSong(database, "Alpha");
            long setlistId = setlists.addSetlist("Here", null);
            setlists.updateSetlist(
                    setlistId, "Here", layoutId, null, 0, false, null, null, null, null, null, null);
            long itemId = setlists.addItem(setlistId, songId, 0, null, null);
            profiles.replaceOccurrencePreferences(setlistId, new OccurrencePreferences(
                    List.of(
                            new PartPreferenceWeight(itemId, 1, oldPlayer, 3.0d),
                            new PartPreferenceWeight(itemId, 2, newPlayer, 9.0d)),
                    List.of(),
                    List.of()));

            long otherSong = insertSong(database, "Beta");
            long otherSetlistId = setlists.addSetlist("Elsewhere", null);
            setlists.updateSetlist(
                    otherSetlistId, "Elsewhere", otherLayoutId, null, 0, false, null, null, null, null, null, null);
            long otherItemId = setlists.addItem(otherSetlistId, otherSong, 0, null, null);
            profiles.replaceOccurrencePreferences(otherSetlistId, new OccurrencePreferences(
                    List.of(new PartPreferenceWeight(otherItemId, 1, oldPlayer, 7.0d)),
                    List.of(),
                    List.of()));

            bands.replacePlayerInBandLayout(layoutId, bandId, oldPlayer, newPlayer);

            BandOptimizationProfile profile = profiles.getBandProfile(bandId);
            assertEquals(List.of(new PlayerSwitchWeight(newPlayer, 5.0d)), profile.playerSwitchWeights());
            assertEquals(
                    List.of(
                            new InstrumentAffinityWeight(newPlayer, instrumentA, 9.0d),
                            new InstrumentAffinityWeight(newPlayer, instrumentB, 4.0d)),
                    profile.instrumentAffinities());
            assertEquals(
                    List.of(
                            new PartPreferenceWeight(itemId, 1, newPlayer, 3.0d),
                            new PartPreferenceWeight(itemId, 2, newPlayer, 9.0d)),
                    profiles.listOccurrencePreferences(setlistId).partPreferences());
            assertEquals(
                    List.of(new PartPreferenceWeight(otherItemId, 1, oldPlayer, 7.0d)),
                    profiles.listOccurrencePreferences(otherSetlistId).partPreferences());
        }
    }

    @Test
    void deletesRemoveOnlyTheReferencedRows() throws Exception {
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(tempDir.resolve("delete.sqlite"))) {
            SqliteBandRepository bands = new SqliteBandRepository(database);
            SqlitePlayerRepository players = new SqlitePlayerRepository(database);
            SqliteSetlistRepository setlists = new SqliteSetlistRepository(database);
            SqliteOptimizationProfileRepository profiles = new SqliteOptimizationProfileRepository(database);
            long bandId = bands.addBand("Band", null);
            long otherBandId = bands.addBand("Other", null);
            long oldPlayer = players.addPlayer("Ada", null, null);
            long keptPlayer = players.addPlayer("Bea", null, null);
            long instrumentId = instrumentId(database, 0);
            profiles.saveBandProfile(bandId, new BandOptimizationProfile(
                    1,
                    4.0d,
                    1.0d,
                    1.0d,
                    1.0d,
                    2,
                    1.0d,
                    List.of(new PlayerSwitchWeight(oldPlayer, 2.0d), new PlayerSwitchWeight(keptPlayer, 3.0d)),
                    List.of(new InstrumentAffinityWeight(oldPlayer, instrumentId, 1.0d)),
                    SearchBudgetPreset.NORMAL));
            profiles.saveBandProfile(otherBandId, BandOptimizationProfile.defaults());

            long songA = insertSong(database, "Alpha");
            long songB = insertSong(database, "Beta");
            long orphanSong = insertSong(database, "Orphan");
            insertSongFile(database, songA);
            insertSongFile(database, songB);
            long setlistId = setlists.addSetlist("Friday", null);
            long keptItem = setlists.addItem(setlistId, songA, 0, null, null);
            long removedItem = setlists.addItem(setlistId, songB, 1, null, null);
            long orphanItem = setlists.addItem(setlistId, orphanSong, 2, null, null);
            profiles.replaceOccurrencePreferences(setlistId, new OccurrencePreferences(
                    List.of(
                            new PartPreferenceWeight(keptItem, 1, keptPlayer, 1.0d),
                            new PartPreferenceWeight(removedItem, 1, oldPlayer, 2.0d),
                            new PartPreferenceWeight(orphanItem, 1, keptPlayer, 3.0d)),
                    List.of(
                            new ItemKeepWeight(keptItem, 1.0d),
                            new ItemKeepWeight(removedItem, 1.0d),
                            new ItemKeepWeight(orphanItem, 1.0d)),
                    List.of(
                            new PartKeepWeight(keptItem, 1, 1.0d),
                            new PartKeepWeight(removedItem, 1, 1.0d),
                            new PartKeepWeight(orphanItem, 1, 1.0d))));

            players.deletePlayer(oldPlayer);
            BandOptimizationProfile afterPlayer = profiles.getBandProfile(bandId);
            assertEquals(List.of(new PlayerSwitchWeight(keptPlayer, 3.0d)), afterPlayer.playerSwitchWeights());
            assertEquals(List.of(), afterPlayer.instrumentAffinities());
            assertEquals(
                    List.of(
                            new PartPreferenceWeight(keptItem, 1, keptPlayer, 1.0d),
                            new PartPreferenceWeight(orphanItem, 1, keptPlayer, 3.0d)),
                    profiles.listOccurrencePreferences(setlistId).partPreferences());
            assertEquals(3, profiles.listOccurrencePreferences(setlistId).itemKeepWeights().size());

            setlists.removeItem(removedItem);
            assertEquals(
                    List.of(keptItem, orphanItem),
                    profiles.listOccurrencePreferences(setlistId).itemKeepWeights().stream()
                            .map(ItemKeepWeight::setlistItemId)
                            .toList());

            profiles.deleteOccurrenceForOrphanItems();
            assertEquals(
                    List.of(new ItemKeepWeight(keptItem, 1.0d)),
                    profiles.listOccurrencePreferences(setlistId).itemKeepWeights());
            assertEquals(1, setlists.listItems(setlistId).stream().filter(item -> item.id() == orphanItem).count());

            bands.deleteBand(bandId);
            assertEquals(BandOptimizationProfile.defaults(), profiles.getBandProfile(otherBandId));
            assertThrows(LibraryException.class, () -> profiles.getBandProfile(bandId));
            assertEquals(1, profiles.listOccurrencePreferences(setlistId).partPreferences().size());

            setlists.deleteSetlist(setlistId);
            assertEquals(0, count(database, "SetlistPartPreference"));
            assertEquals(0, count(database, "SetlistItemKeepWeight"));
            assertEquals(0, count(database, "SetlistPartKeepWeight"));
        }
    }

    @Test
    void missingSearchBudgetReadsAsNormalAndUnknownBudgetIsRejected() throws Exception {
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(tempDir.resolve("budget.sqlite"))) {
            SqliteBandRepository bands = new SqliteBandRepository(database);
            SqliteOptimizationProfileRepository profiles = new SqliteOptimizationProfileRepository(database);
            long bandId = bands.addBand("Band", null);
            String now = SqliteTimestamps.now();
            String json = """
                    {"smoothWeight":4,"totalWeight":1,"roleWeight":1,"keepWeight":1,
                     "targetChanges":2,"defaultSwitchMultiplier":1}
                    """;
            try (PreparedStatement statement = database.connection().prepareStatement(
                    """
                    INSERT INTO BandOptimizationProfile
                        (band_id, settings_version, settings_json, created_at, updated_at)
                    VALUES (?, 1, ?, ?, ?)
                    """)) {
                statement.setLong(1, bandId);
                statement.setString(2, json);
                statement.setString(3, now);
                statement.setString(4, now);
                statement.executeUpdate();
            }
            assertEquals(SearchBudgetPreset.NORMAL, profiles.getBandProfile(bandId).searchBudget());

            try (PreparedStatement statement = database.connection().prepareStatement(
                    "UPDATE BandOptimizationProfile SET settings_json = ? WHERE band_id = ?")) {
                statement.setString(1, json.replace("}", ",\"searchBudget\":\"FOREVER\"}"));
                statement.setLong(2, bandId);
                statement.executeUpdate();
            }
            LibraryException invalid = assertThrows(LibraryException.class, () -> profiles.getBandProfile(bandId));
            assertTrue(invalid.getMessage().contains("searchBudget"));
        }
    }

    @Test
    void pinsFollowTheOccurrenceAndALockedSetlistRejectsEdits() throws Exception {
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(tempDir.resolve("pins.sqlite"))) {
            SqliteBandRepository bands = new SqliteBandRepository(database);
            SqlitePlayerRepository players = new SqlitePlayerRepository(database);
            SqliteSetlistRepository setlists = new SqliteSetlistRepository(database);
            SqliteOptimizationProfileRepository profiles = new SqliteOptimizationProfileRepository(database);
            long bandId = bands.addBand("Band", null);
            long layoutId = bands.getOrCreatePrimaryLayout(bandId).id();
            long otherBandId = bands.addBand("Other", null);
            long otherLayoutId = bands.getOrCreatePrimaryLayout(otherBandId).id();
            long ada = players.addPlayer("Ada", null, null);
            long bea = players.addPlayer("Bea", null, null);
            bands.setSlot(layoutId, ada, 0, 0, 7, 5);

            long songId = insertSong(database, "Alpha");
            long setlistId = setlists.addSetlist("Friday", null);
            setlists.updateSetlist(
                    setlistId, "Friday", layoutId, null, 0, false, null, null, null, null, null, null);
            long first = setlists.addItem(setlistId, songId, 0, null, null);
            long second = setlists.addItem(setlistId, songId, 1, null, null);
            profiles.replacePins(setlistId, List.of(
                    new AssignmentPin(second, 1, bea),
                    new AssignmentPin(first, 2, ada)));
            assertEquals(
                    List.of(new AssignmentPin(first, 2, ada), new AssignmentPin(second, 1, bea)),
                    profiles.listPins(setlistId));
            LibraryException duplicate = assertThrows(
                    LibraryException.class,
                    () -> profiles.replacePins(setlistId, List.of(
                            new AssignmentPin(first, 2, ada),
                            new AssignmentPin(first, 2, bea))));
            assertTrue(duplicate.getMessage().contains("Duplicate"));
            assertEquals(ada, profiles.listPins(setlistId).get(0).playerId());

            long copySetlistId = setlists.duplicateSetlist(
                    setlistId, "Friday copy", layoutId, false, null, null, null, null, null, null);
            List<SetlistItemInfo> copiedItems = setlists.listItems(copySetlistId);
            assertEquals(
                    List.of(
                            new AssignmentPin(copiedItems.get(0).id(), 2, ada),
                            new AssignmentPin(copiedItems.get(1).id(), 1, bea)),
                    profiles.listPins(copySetlistId));
            assertEquals(first, profiles.listPins(setlistId).get(0).setlistItemId());

            long targetId = setlists.addSetlist("Saturday", null);
            long otherSong = insertSong(database, "Beta");
            setlists.addItem(targetId, otherSong, 0, null, null);
            setlists.mergeSetlistSongs(targetId, setlistId, false);
            assertEquals(List.of(), profiles.listPins(targetId));

            long copyBandId = bands.duplicateBand(bandId);
            assertEquals(4, count(database, "SetlistAssignmentPin"));
            assertEquals(List.of(), profiles.listPins(targetId));
            assertTrue(copyBandId > 0);

            long elsewhereId = setlists.addSetlist("Elsewhere", null);
            setlists.updateSetlist(
                    elsewhereId, "Elsewhere", otherLayoutId, null, 0, false, null, null, null, null, null, null);
            long elsewhereItem = setlists.addItem(elsewhereId, songId, 0, null, null);
            profiles.replacePins(elsewhereId, List.of(new AssignmentPin(elsewhereItem, 1, ada)));

            bands.replacePlayerInBandLayout(layoutId, bandId, ada, bea);
            assertEquals(bea, profiles.listPins(setlistId).get(0).playerId());
            assertEquals(ada, profiles.listPins(elsewhereId).get(0).playerId());

            setlists.updateSetlist(
                    setlistId, "Friday", layoutId, null, 0, true, null, null, null, null, null, null);
            LibraryException lockedPins = assertThrows(
                    LibraryException.class,
                    () -> profiles.replacePins(setlistId, List.of()));
            assertTrue(lockedPins.getMessage().contains("Unlock"));
            LibraryException lockedPreferences = assertThrows(
                    LibraryException.class,
                    () -> profiles.replaceOccurrencePreferences(setlistId, OccurrencePreferences.empty()));
            assertTrue(lockedPreferences.getMessage().contains("Unlock"));
            assertEquals(2, profiles.listPins(setlistId).size());
            profiles.saveBandProfile(bandId, BandOptimizationProfile.defaults());

            setlists.updateSetlist(
                    setlistId, "Friday", layoutId, null, 0, false, null, null, null, null, null, null);
            long keptItem = setlists.addItem(setlistId, otherSong, 2, null, null);
            profiles.replacePins(setlistId, List.of(
                    new AssignmentPin(first, 2, bea),
                    new AssignmentPin(second, 1, ada),
                    new AssignmentPin(keptItem, 1, ada)));
            players.deletePlayer(bea);
            assertEquals(
                    List.of(new AssignmentPin(second, 1, ada), new AssignmentPin(keptItem, 1, ada)),
                    profiles.listPins(setlistId));

            setlists.removeItem(second);
            assertEquals(List.of(new AssignmentPin(keptItem, 1, ada)), profiles.listPins(setlistId));

            long orphanSong = insertSong(database, "Orphan");
            insertSongFile(database, songId);
            insertSongFile(database, otherSong);
            long orphanItem = setlists.addItem(setlistId, orphanSong, 3, null, null);
            profiles.replacePins(setlistId, List.of(
                    new AssignmentPin(keptItem, 1, ada),
                    new AssignmentPin(orphanItem, 1, ada)));
            profiles.deleteOccurrenceForOrphanItems();
            assertEquals(List.of(new AssignmentPin(keptItem, 1, ada)), profiles.listPins(setlistId));

            bands.deleteBand(bandId);
            assertEquals(List.of(new AssignmentPin(keptItem, 1, ada)), profiles.listPins(setlistId));
            setlists.deleteSetlist(setlistId);
            assertEquals(1, count(database, "SetlistAssignmentPin"));
            setlists.deleteSetlist(copySetlistId);
            setlists.deleteSetlist(elsewhereId);
            assertEquals(0, count(database, "SetlistAssignmentPin"));
        }
    }

    private static OccurrencePreferences preferences(long itemId, long playerId, double penalty) {
        return new OccurrencePreferences(
                List.of(new PartPreferenceWeight(itemId, 1, playerId, penalty)),
                List.of(new ItemKeepWeight(itemId, 1.5d)),
                List.of(new PartKeepWeight(itemId, 1, 2.5d)));
    }

    private static long instrumentId(SqliteDatabase database, int offset) throws Exception {
        try (Statement statement = database.connection().createStatement();
             ResultSet rs = statement.executeQuery("SELECT id FROM Instrument ORDER BY id")) {
            for (int i = 0; i <= offset; i++) {
                assertTrue(rs.next());
            }
            return rs.getLong(1);
        }
    }

    private static int count(SqliteDatabase database, String table) throws Exception {
        try (Statement statement = database.connection().createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private static long insertSong(SqliteDatabase database, String title) throws Exception {
        String now = SqliteTimestamps.now();
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                INSERT INTO Song (title, composers, total_plays, parts, created_at, updated_at)
                VALUES (?, 'Composer', 0, '[]', ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, title);
            statement.setString(2, now);
            statement.setString(3, now);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    private static void insertSongFile(SqliteDatabase database, long songId) throws Exception {
        String now = SqliteTimestamps.now();
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                INSERT INTO SongFile (song_id, file_path, is_primary_library, is_set_copy, scan_excluded,
                                      created_at, updated_at)
                VALUES (?, ?, 1, 0, 0, ?, ?)
                """)) {
            statement.setLong(1, songId);
            statement.setString(2, "/music/" + songId + ".abc");
            statement.setString(3, now);
            statement.setString(4, now);
            statement.executeUpdate();
        }
    }
}

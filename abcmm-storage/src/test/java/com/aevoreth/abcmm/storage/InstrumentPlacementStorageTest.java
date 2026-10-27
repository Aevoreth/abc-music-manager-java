package com.aevoreth.abcmm.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.aevoreth.abcmm.domain.optimizer.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

class InstrumentPlacementStorageTest {
    @TempDir Path directory;

    private BandOptimizationProfile profile(long instrument, long preferred, long avoided) {
        return new BandOptimizationProfile(
                1,
                4,
                1,
                1,
                1,
                2,
                1,
                List.of(),
                List.of(),
                SearchBudgetPreset.NORMAL,
                List.of(
                        new InstrumentPlacementRule(
                                instrument, List.of(preferred), List.of(avoided), 10)));
    }

    @Test
    void placementSurvivesReopeningCopyingReplacementAndDeletion() throws Exception {
        Path path = directory.resolve("placement.sqlite");
        long band, copy, layout, preferred, avoided, replacement, instrument;
        try (var database = SqliteDatabase.openOrCreate(path)) {
            var bands = new SqliteBandRepository(database);
            var players = new SqlitePlayerRepository(database);
            var profiles = new SqliteOptimizationProfileRepository(database);
            band = bands.addBand("Band", null);
            layout = bands.getOrCreatePrimaryLayout(band).id();
            preferred = players.addPlayer("Back", null, null);
            avoided = players.addPlayer("Front", null, null);
            replacement = players.addPlayer("Replacement", null, null);
            instrument = players.listInstruments().get(0).id();
            profiles.saveBandProfile(band, profile(instrument, preferred, avoided));
            copy = bands.duplicateBand(band);
            assertEquals(profile(instrument, preferred, avoided), profiles.getBandProfile(copy));
            profiles.transferPlayer(band, layout, preferred, replacement);
            assertEquals(
                    List.of(replacement),
                    profiles.getBandProfile(band)
                            .instrumentPlacements()
                            .get(0)
                            .preferredPlayerIds());
            assertEquals(
                    List.of(preferred),
                    profiles.getBandProfile(copy)
                            .instrumentPlacements()
                            .get(0)
                            .preferredPlayerIds());
            // Destination's existing choice wins when a replacement would conflict.
            profiles.transferPlayer(band, layout, replacement, avoided);
            var rule = profiles.getBandProfile(band).instrumentPlacements().get(0);
            assertTrue(rule.preferredPlayerIds().isEmpty());
            assertEquals(List.of(avoided), rule.avoidedPlayerIds());
            profiles.deletePlayerReferences(avoided);
        }
        try (var database = SqliteDatabase.openOrCreate(path)) {
            var profiles = new SqliteOptimizationProfileRepository(database);
            assertTrue(
                    profiles.getBandProfile(band)
                            .instrumentPlacements()
                            .get(0)
                            .avoidedPlayerIds()
                            .isEmpty());
            assertEquals(
                    List.of(preferred),
                    profiles.getBandProfile(copy)
                            .instrumentPlacements()
                            .get(0)
                            .preferredPlayerIds());
            assertTrue(
                    profiles.getBandProfile(copy)
                            .instrumentPlacements()
                            .get(0)
                            .avoidedPlayerIds()
                            .isEmpty());
        }
    }

    @Test
    void malformedPlacementJsonIsRejectedWithoutChangingTheStoredDocument() throws Exception {
        try (var database = SqliteDatabase.openOrCreate(directory.resolve("malformed.sqlite"))) {
            var bands = new SqliteBandRepository(database);
            var profiles = new SqliteOptimizationProfileRepository(database);
            long band = bands.addBand("Band", null);
            profiles.saveBandProfile(band, BandOptimizationProfile.defaults());
            try (var statement =
                    database.connection()
                            .prepareStatement(
                                    "UPDATE BandOptimizationProfile SET settings_json ="
                                        + " json_set(settings_json, '$.instrumentPlacements',"
                                        + " json(?)) WHERE band_id = ?")) {
                statement.setString(
                        1,
                        "[{\"instrumentId\":\"not an"
                            + " id\",\"preferredPlayerIds\":[],\"avoidedPlayerIds\":[],\"missPenalty\":10}]");
                statement.setLong(2, band);
                statement.executeUpdate();
            }
            assertThrows(
                    com.aevoreth.abcmm.domain.library.LibraryException.class,
                    () -> profiles.getBandProfile(band));
        }
    }

    @Test
    void olderProfilesWithoutPlacementReadAsEmptyAndOverlappingChoicesAreRejected()
            throws Exception {
        try (var database = SqliteDatabase.openOrCreate(directory.resolve("legacy.sqlite"))) {
            var bands = new SqliteBandRepository(database);
            var profiles = new SqliteOptimizationProfileRepository(database);
            long band = bands.addBand("Legacy", null);
            profiles.saveBandProfile(band, BandOptimizationProfile.defaults());
            assertTrue(profiles.getBandProfile(band).instrumentPlacements().isEmpty());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new InstrumentPlacementRule(1, List.of(1L), List.of(1L), 10));
        }
    }
}

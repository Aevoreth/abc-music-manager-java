package com.aevoreth.abcmm.optimizer;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.storage.SqliteBandRepository;
import com.aevoreth.abcmm.storage.SqliteDatabase;
import com.aevoreth.abcmm.storage.SqlitePlayerRepository;
import com.aevoreth.abcmm.storage.SqliteSetlistRepository;
import com.aevoreth.abcmm.storage.SqliteSongLayoutRepository;
import com.aevoreth.abcmm.storage.SqliteTimestamps;

/**
 * Sanitized temporary library. Not a user database and not committed.
 */
final class SyntheticLibrary implements AutoCloseable {

    final Path path;
    final SqliteDatabase database;
    final SqliteBandRepository bands;
    final SqlitePlayerRepository players;
    final SqliteSongLayoutRepository songLayouts;
    final SqliteSetlistRepository setlists;

    final long layoutId;
    final long alice;
    final long bob;
    final long cara;
    final long fiddle;
    final long harp;
    final long lute;
    final long traveler;
    final long traveller;

    long tuneId;
    long tuneLayoutId;
    long feasibleId;
    long nullOverrideId;
    long aliasId;
    long unownedId;
    long doubleBookedId;
    long unknownInstrumentId;
    long duplicatePartId;
    long madeForId;
    long unparsableId;
    long partCountMismatchId;
    long duplicateRowId;
    long missingLayoutId;

    private SyntheticLibrary(
            Path path,
            SqliteDatabase database,
            long layoutId,
            long alice,
            long bob,
            long cara,
            long fiddle,
            long harp,
            long lute,
            long traveler,
            long traveller) {
        this.path = path;
        this.database = database;
        this.bands = new SqliteBandRepository(database);
        this.players = new SqlitePlayerRepository(database);
        this.songLayouts = new SqliteSongLayoutRepository(database);
        this.setlists = new SqliteSetlistRepository(database);
        this.layoutId = layoutId;
        this.alice = alice;
        this.bob = bob;
        this.cara = cara;
        this.fiddle = fiddle;
        this.harp = harp;
        this.lute = lute;
        this.traveler = traveler;
        this.traveller = traveller;
    }

    static SyntheticLibrary create(Path path) throws Exception {
        SqliteDatabase database = SqliteDatabase.openOrCreate(path);
        try {
            SqliteBandRepository bands = new SqliteBandRepository(database);
            SqlitePlayerRepository players = new SqlitePlayerRepository(database);
            long bandId = bands.addBand("Fixture Band", null);
            long layoutId = bands.getOrCreatePrimaryLayout(bandId).id();
            long alice = players.addPlayer("Alice", null, null);
            long bob = players.addPlayer("Bob", null, null);
            long cara = players.addPlayer("Cara", null, null);
            bands.setSlot(layoutId, alice, 0, 0, 7, 5);
            bands.setSlot(layoutId, bob, 8, 0, 7, 5);

            long fiddle = instrumentId(database, "Basic Fiddle");
            long harp = instrumentId(database, "Basic Harp");
            long lute = instrumentId(database, "Basic Lute");
            long traveler = instrumentId(database, "Traveler's Trusty Fiddle");
            long traveller = insertInstrument(database, "Traveller's Trusty Fiddle");

            players.setPlayerInstrument(alice, fiddle, true, false, null);
            players.setPlayerInstrument(alice, harp, true, false, null);
            players.setPlayerInstrument(alice, lute, true, false, null);
            players.setPlayerInstrument(alice, traveler, true, false, null);
            players.setPlayerInstrument(bob, fiddle, true, false, null);
            players.setPlayerInstrument(bob, harp, true, false, null);
            players.setPlayerInstrument(bob, lute, false, true, null);

            SyntheticLibrary library = new SyntheticLibrary(
                    path, database, layoutId, alice, bob, cara, fiddle, harp, lute, traveler, traveller);
            library.seedAll();
            return library;
        } catch (Exception ex) {
            database.close();
            throw ex;
        }
    }

    private void seedAll() throws Exception {
        tuneId = insertSong(parts(part(1, fiddle, null), part(2, harp, null)));
        tuneLayoutId = songLayouts.getOrCreateSongLayout(tuneId, layoutId, "Tune").id();
        songLayouts.setAssignment(tuneLayoutId, alice, 1);
        songLayouts.setAssignment(tuneLayoutId, bob, 2);

        feasibleId = newSetlist("Feasible Set", true);
        long first = setlists.addItem(feasibleId, tuneId, 0, null, tuneLayoutId);
        setlists.upsertBandAssignment(first, cara, 1);
        long second = setlists.addItem(feasibleId, tuneId, 1, null, tuneLayoutId);
        setlists.upsertBandAssignment(second, alice, 2);
        setlists.upsertBandAssignment(second, bob, 1);

        nullOverrideId = newSetlist("Null Override", true);
        long nulled = setlists.addItem(nullOverrideId, tuneId, 0, null, tuneLayoutId);
        setlists.upsertBandAssignment(nulled, alice, null);

        long aliasSong = insertSong(parts(part(1, traveller, null)));
        long aliasLayout = songLayouts.getOrCreateSongLayout(aliasSong, layoutId, "Alias").id();
        songLayouts.setAssignment(aliasLayout, alice, 1);
        aliasId = newSetlist("Alias", false);
        setlists.addItem(aliasId, aliasSong, 0, null, aliasLayout);

        long luteSong = insertSong(parts(part(1, lute, null)));
        long luteLayout = songLayouts.getOrCreateSongLayout(luteSong, layoutId, "Lute").id();
        songLayouts.setAssignment(luteLayout, bob, 1);
        unownedId = newSetlist("Unowned", false);
        setlists.addItem(unownedId, luteSong, 0, null, luteLayout);

        doubleBookedId = newSetlist("Double", false);
        long doubled = setlists.addItem(doubleBookedId, tuneId, 0, null, tuneLayoutId);
        setlists.upsertBandAssignment(doubled, alice, 1);
        setlists.upsertBandAssignment(doubled, bob, 1);

        unknownInstrumentId = newSetlist("Unknown", false);
        long unknownSong = insertSong(parts(part(1, 999_999L, null)));
        setlists.addItem(unknownInstrumentId, unknownSong, 0, null, null);

        duplicatePartId = newSetlist("Duplicate Part", false);
        long duplicateSong = insertSong(parts(part(1, fiddle, null), part(1, fiddle, null)));
        long duplicateLayout = songLayouts.getOrCreateSongLayout(duplicateSong, layoutId, "Duplicate").id();
        songLayouts.setAssignment(duplicateLayout, alice, 1);
        setlists.addItem(duplicatePartId, duplicateSong, 0, null, duplicateLayout);

        madeForId = newSetlist("Made For", false);
        long madeForSong = insertSong(parts(part(1, null, "Basic Lute")));
        long madeForLayout = songLayouts.getOrCreateSongLayout(madeForSong, layoutId, "Made For").id();
        songLayouts.setAssignment(madeForLayout, alice, 1);
        setlists.addItem(madeForId, madeForSong, 0, null, madeForLayout);

        unparsableId = newSetlist("Unparsable", false);
        long unparsableSong = insertSong("not-json");
        setlists.addItem(unparsableId, unparsableSong, 0, null, null);

        partCountMismatchId = newSetlist("Mismatch", false);
        long mismatchSong = insertSong(parts(
                "{\"part_number\":0,\"part_name\":\"Skip\",\"instrument_id\":" + fiddle + "}",
                part(1, fiddle, null)));
        long mismatchLayout = songLayouts.getOrCreateSongLayout(mismatchSong, layoutId, "Mismatch").id();
        songLayouts.setAssignment(mismatchLayout, alice, 1);
        setlists.addItem(partCountMismatchId, mismatchSong, 0, null, mismatchLayout);

        duplicateRowId = newSetlist("Duplicate Row", false);
        long duplicateItem = setlists.addItem(duplicateRowId, tuneId, 0, null, tuneLayoutId);
        insertAssignmentRow(duplicateItem, alice, 1);
        insertAssignmentRow(duplicateItem, alice, 1);

        missingLayoutId = setlists.addSetlist("No Layout", null);
    }

    private long newSetlist(String name, boolean locked) throws LibraryException {
        long id = setlists.addSetlist(name, null);
        setlists.updateSetlist(id, name, layoutId, null, 0, locked, null, null, null, null, null, null);
        return id;
    }

    private long insertSong(String partsJson) throws Exception {
        String now = SqliteTimestamps.now();
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                INSERT INTO Song (title, composers, duration_seconds, transcriber, rating, status_id,
                   notes, lyrics, last_played_at, total_plays, parts, created_at, updated_at)
                VALUES (?, ?, ?, NULL, NULL, NULL, NULL, NULL, NULL, 0, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, "Fixture Tune");
            statement.setString(2, "Fixture Composer");
            statement.setInt(3, 60);
            statement.setString(4, partsJson);
            statement.setString(5, now);
            statement.setString(6, now);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("song id");
                }
                return keys.getLong(1);
            }
        }
    }

    private void insertAssignmentRow(long itemId, long playerId, int partNumber) throws Exception {
        String now = SqliteTimestamps.now();
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                INSERT INTO SetlistBandAssignment
                (setlist_item_id, player_id, part_number, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, itemId);
            statement.setLong(2, playerId);
            statement.setInt(3, partNumber);
            statement.setString(4, now);
            statement.setString(5, now);
            statement.executeUpdate();
        }
    }

    private static long instrumentId(SqliteDatabase database, String name) throws Exception {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT id FROM Instrument WHERE name = ?")) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("missing instrument " + name);
                }
                return rs.getLong(1);
            }
        }
    }

    private static long insertInstrument(SqliteDatabase database, String name) throws Exception {
        String now = SqliteTimestamps.now();
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                INSERT INTO Instrument (name, alternative_names, created_at, updated_at)
                VALUES (?, NULL, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setString(2, now);
            statement.setString(3, now);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException(name);
                }
                return keys.getLong(1);
            }
        }
    }

    private static String parts(String... objects) {
        return "[" + String.join(",", objects) + "]";
    }

    private static String part(int partNumber, Long instrumentId, String madeFor) {
        String instrument = instrumentId == null ? "null" : instrumentId.toString();
        String made = madeFor == null ? "null" : "\"" + madeFor + "\"";
        return "{\"part_number\":" + partNumber
                + ",\"part_name\":\"P" + partNumber
                + "\",\"instrument_id\":" + instrument
                + ",\"made_for\":" + made + "}";
    }

    @Override
    public void close() {
        database.close();
    }
}

package com.aevoreth.abcmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.domain.band.SongLayoutInfo;
import com.aevoreth.abcmm.domain.library.LibraryException;
import com.aevoreth.abcmm.domain.optimizer.AssignmentOverride;
import com.aevoreth.abcmm.domain.optimizer.AssignmentPin;
import com.aevoreth.abcmm.domain.optimizer.OptimizationApplyResult;
import com.aevoreth.abcmm.domain.optimizer.OptimizationJournal;
import com.aevoreth.abcmm.domain.optimizer.OptimizationRefusal;
import com.aevoreth.abcmm.domain.optimizer.OptimizationRevision;
import com.aevoreth.abcmm.domain.optimizer.OptimizationUndoResult;
import com.aevoreth.abcmm.domain.setlist.SetlistBandAssignmentInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistItemInfo;

class SqliteOptimizationJournalTest {

    @TempDir
    Path tempDir;

    @Test
    void sqlFailureRollsBackAssignmentsAndHistory() throws Exception {
        try (Fixture fixture = Fixture.open(tempDir.resolve("rollback.sqlite"))) {
            fixture.setlists.upsertBandAssignment(fixture.firstItemId, fixture.ada, 1);
            OptimizationRevision before = fixture.journal.revision(fixture.setlistId);
            // Abort the second insert after the first has run. The transaction must roll both back.
            try (Statement trigger = fixture.database.connection().createStatement()) {
                trigger.execute("""
                        CREATE TEMP TRIGGER fail_second_override
                        BEFORE INSERT ON SetlistBandAssignment
                        WHEN NEW.player_id = 999999
                        BEGIN
                            SELECT RAISE(ABORT, 'forced failure');
                        END
                        """);
            }
            List<AssignmentOverride> desired = List.of(
                    new AssignmentOverride(fixture.firstItemId, fixture.ada, 2),
                    new AssignmentOverride(fixture.firstItemId, 999_999L, 1));

            assertThrows(LibraryException.class, () -> fixture.journal.apply(
                    fixture.setlistId, before, desired, "{\"smoothWeight\":4}"));

            assertEquals(before, fixture.journal.revision(fixture.setlistId));
            assertEquals(List.of(new AssignmentOverride(fixture.firstItemId, fixture.ada, 1)), overrides(fixture));
            assertEquals(0, countHistory(fixture.database, fixture.setlistId));
            OptimizationUndoResult undo = fixture.journal.undoLatest(fixture.setlistId);
            assertFalse(undo.undone());
            assertTrue(undo.refusals().contains(OptimizationRefusal.NOTHING_TO_UNDO));
        }
    }

    @Test
    void undoAfterRestartRestoresExactRows() throws Exception {
        Path db = tempDir.resolve("restart.sqlite");
        long setlistId;
        long songId;
        long songLayoutId;
        long firstItemId;
        long secondItemId;
        String layoutBefore;
        String partsBefore;
        try (Fixture fixture = Fixture.open(db)) {
            setlistId = fixture.setlistId;
            songId = fixture.songId;
            songLayoutId = fixture.songLayoutId;
            firstItemId = fixture.firstItemId;
            secondItemId = fixture.secondItemId;
            fixture.setlists.upsertBandAssignment(firstItemId, fixture.ada, null);
            insertAssignment(fixture.database, firstItemId, fixture.cara, 3);
            insertAssignment(fixture.database, firstItemId, fixture.cara, 3);
            fixture.setlists.upsertBandAssignment(secondItemId, fixture.ada, 1);
            setLastSetlistItem(fixture.database, songId, firstItemId);
            layoutBefore = layoutRows(fixture.database, songLayoutId);
            partsBefore = songParts(fixture.database, songId);

            OptimizationRevision before = fixture.journal.revision(setlistId);
            List<AssignmentOverride> desired = List.of(
                    new AssignmentOverride(firstItemId, fixture.ada, 2),
                    new AssignmentOverride(secondItemId, fixture.ada, 4));
            OptimizationApplyResult applied = fixture.journal.apply(setlistId, before, desired, "{}");
            assertTrue(applied.applied(), applied.message());
            assertEquals(desiredRows(desired), overrides(fixture));
            assertEquals(partsBefore, songParts(fixture.database, songId));
            assertEquals(firstItemId, lastSetlistItem(fixture.database, songId));
            assertEquals(layoutBefore, layoutRows(fixture.database, songLayoutId));
        }

        try (SqliteDatabase database = SqliteDatabase.openOrCreate(db)) {
            SqliteOptimizationJournal journal = new SqliteOptimizationJournal(database);
            SqliteSetlistRepository setlists = new SqliteSetlistRepository(database);
            OptimizationUndoResult undone = journal.undoLatest(setlistId);
            assertTrue(undone.undone(), undone.message());
            assertEquals(UNDONE, status(database, undone.operationId()));
            assertEquals(
                    List.of(
                            new AssignmentOverride(firstItemId, fixturePlayer(database, "Ada"), null),
                            new AssignmentOverride(firstItemId, fixturePlayer(database, "Cara"), 3),
                            new AssignmentOverride(firstItemId, fixturePlayer(database, "Cara"), 3),
                            new AssignmentOverride(secondItemId, fixturePlayer(database, "Ada"), 1)),
                    overrides(setlists, setlistId));
            assertFalse(overrides(setlists, setlistId).contains(
                    new AssignmentOverride(firstItemId, fixturePlayer(database, "Bob"), 2)));
            assertEquals(layoutBefore, layoutRows(database, songLayoutId));
            assertEquals(partsBefore, songParts(database, songId));
            assertEquals(firstItemId, lastSetlistItem(database, songId));
            assertTrue(journal.undoLatest(setlistId).refusals().contains(OptimizationRefusal.NOTHING_TO_UNDO));
        }
    }

    @Test
    void conflictsRefuseUndoAndRepeatedApplyUndoesOneStep() throws Exception {
        try (Fixture fixture = Fixture.open(tempDir.resolve("conflict.sqlite"))) {
            fixture.setlists.upsertBandAssignment(fixture.firstItemId, fixture.ada, 1);
            fixture.setlists.upsertBandAssignment(fixture.secondItemId, fixture.ada, 2);
            OptimizationRevision original = fixture.journal.revision(fixture.setlistId);
            fixture.setlists.upsertBandAssignment(fixture.firstItemId, fixture.ada, 9);
            OptimizationApplyResult stale = fixture.journal.apply(
                    fixture.setlistId,
                    original,
                    List.of(new AssignmentOverride(fixture.firstItemId, fixture.ada, 2)),
                    "{}");
            assertFalse(stale.applied());
            assertTrue(stale.refusals().contains(OptimizationRefusal.STALE_REVISION));
            assertTrue(stale.refusals().contains(OptimizationRefusal.CONFLICT_ASSIGNMENTS));
            assertFalse(stale.message().isBlank());
            fixture.setlists.upsertBandAssignment(fixture.firstItemId, fixture.ada, 1);

            List<AssignmentOverride> firstDesired = List.of(
                    new AssignmentOverride(fixture.firstItemId, fixture.ada, 1),
                    new AssignmentOverride(fixture.secondItemId, fixture.ada, 4));
            OptimizationApplyResult first = fixture.journal.apply(
                    fixture.setlistId, fixture.journal.revision(fixture.setlistId), firstDesired, "{}");
            assertTrue(first.applied(), first.message());
            assertEquals(desiredRows(firstDesired), overrides(fixture));

            fixture.setlists.upsertBandAssignment(fixture.secondItemId, fixture.ada, 3);
            assertRefusal(fixture.journal.undoLatest(fixture.setlistId), OptimizationRefusal.CONFLICT_ASSIGNMENTS);
            assertEquals(3, part(fixture, fixture.secondItemId, fixture.ada));
            fixture.setlists.upsertBandAssignment(fixture.secondItemId, fixture.ada, 4);

            fixture.setlists.reorderItems(fixture.setlistId, List.of(fixture.secondItemId, fixture.firstItemId));
            assertRefusal(fixture.journal.undoLatest(fixture.setlistId), OptimizationRefusal.CONFLICT_ORDER);
            fixture.setlists.reorderItems(fixture.setlistId, List.of(fixture.firstItemId, fixture.secondItemId));

            fixture.setlists.updateItem(fixture.firstItemId, null, fixture.otherSongLayoutId);
            assertRefusal(fixture.journal.undoLatest(fixture.setlistId), OptimizationRefusal.CONFLICT_LAYOUT);
            fixture.setlists.updateItem(fixture.firstItemId, null, fixture.songLayoutId);

            setParts(fixture.database, fixture.songId, "[{\"n\":1}]");
            assertRefusal(fixture.journal.undoLatest(fixture.setlistId), OptimizationRefusal.CONFLICT_PARTS);
            setParts(fixture.database, fixture.songId, "[{\"name\":\"Fiddle\"}]");

            fixture.profiles.replacePins(
                    fixture.setlistId, List.of(new AssignmentPin(fixture.firstItemId, 1, fixture.ada)));
            assertRefusal(fixture.journal.undoLatest(fixture.setlistId), OptimizationRefusal.CONFLICT_PINS);
            fixture.profiles.replacePins(fixture.setlistId, List.of());

            List<AssignmentOverride> secondDesired = List.of(
                    new AssignmentOverride(fixture.firstItemId, fixture.ada, 1),
                    new AssignmentOverride(fixture.secondItemId, fixture.ada, 5));
            OptimizationApplyResult second = fixture.journal.apply(
                    fixture.setlistId, fixture.journal.revision(fixture.setlistId), secondDesired, "{}");
            assertTrue(second.applied(), second.message());
            assertEquals(desiredRows(secondDesired), overrides(fixture));
            assertEquals(APPLIED, status(fixture.database, first.operationId()));

            OptimizationUndoResult once = fixture.journal.undoLatest(fixture.setlistId);
            assertTrue(once.undone(), once.message());
            assertEquals(second.operationId(), once.operationId());
            assertEquals(desiredRows(firstDesired), overrides(fixture));
            assertEquals(APPLIED, status(fixture.database, first.operationId()));
            assertEquals(UNDONE, status(fixture.database, second.operationId()));

            OptimizationUndoResult twice = fixture.journal.undoLatest(fixture.setlistId);
            assertTrue(twice.undone(), twice.message());
            assertEquals(first.operationId(), twice.operationId());
            assertEquals(
                    List.of(
                            new AssignmentOverride(fixture.firstItemId, fixture.ada, 1),
                            new AssignmentOverride(fixture.secondItemId, fixture.ada, 2)),
                    overrides(fixture));
        }
    }

    @Test
    void lockedSetlistRefusesApplyAndUndo() throws Exception {
        try (Fixture fixture = Fixture.open(tempDir.resolve("locked.sqlite"))) {
            fixture.lock(true);
            OptimizationApplyResult refused = fixture.journal.apply(
                    fixture.setlistId,
                    fixture.journal.revision(fixture.setlistId),
                    List.of(new AssignmentOverride(fixture.firstItemId, fixture.ada, 2)),
                    "{}");
            assertTrue(refused.refusals().contains(OptimizationRefusal.LOCKED));
            assertEquals(List.of(), overrides(fixture));
            assertEquals(0, countHistory(fixture.database, fixture.setlistId));

            fixture.lock(false);
            OptimizationApplyResult applied = fixture.journal.apply(
                    fixture.setlistId,
                    fixture.journal.revision(fixture.setlistId),
                    List.of(new AssignmentOverride(fixture.firstItemId, fixture.ada, 2)),
                    "{}");
            assertTrue(applied.applied(), applied.message());
            fixture.lock(true);
            OptimizationUndoResult blocked = fixture.journal.undoLatest(fixture.setlistId);
            assertTrue(blocked.refusals().contains(OptimizationRefusal.LOCKED));
            assertEquals(List.of(new AssignmentOverride(fixture.firstItemId, fixture.ada, 2)), overrides(fixture));
            assertEquals(APPLIED, status(fixture.database, applied.operationId()));

            fixture.lock(false);
            assertTrue(fixture.journal.undoLatest(fixture.setlistId).undone());
            assertEquals(List.of(), overrides(fixture));
            fixture.setlists.deleteSetlist(fixture.setlistId);
            assertEquals(0, countHistory(fixture.database, fixture.setlistId));
        }
    }

    @Test
    void readOnlyDatabaseRefusesApplyAndUndo() throws Exception {
        Path db = tempDir.resolve("readonly.sqlite");
        long setlistId;
        List<AssignmentOverride> appliedRows;
        try (Fixture fixture = Fixture.open(db)) {
            setlistId = fixture.setlistId;
            OptimizationApplyResult applied = fixture.journal.apply(
                    setlistId,
                    fixture.journal.revision(setlistId),
                    List.of(new AssignmentOverride(fixture.firstItemId, fixture.ada, 2)),
                    "{}");
            assertTrue(applied.applied(), applied.message());
            appliedRows = overrides(fixture);
        }
        try (SqliteDatabase database = SqliteDatabase.openReadOnly(db)) {
            SqliteOptimizationJournal journal = new SqliteOptimizationJournal(database);
            OptimizationRevision revision = journal.revision(setlistId);
            OptimizationApplyResult apply = journal.apply(setlistId, revision, List.of(), "{}");
            assertTrue(apply.refusals().contains(OptimizationRefusal.READ_ONLY));
            assertTrue(journal.undoLatest(setlistId).refusals().contains(OptimizationRefusal.READ_ONLY));
            OptimizationApplyResult missing = journal.apply(
                    9_999_999L,
                    new OptimizationRevision(null, List.of(), List.of(), List.of()),
                    List.of(),
                    "{}");
            assertTrue(missing.refusals().contains(OptimizationRefusal.READ_ONLY));
        }
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(db)) {
            assertEquals(appliedRows, overrides(new SqliteSetlistRepository(database), setlistId));
            assertTrue(new SqliteOptimizationJournal(database).undoLatest(setlistId).undone());
        }
    }

    @Test
    void retentionKeepsTheCurrentUndoTarget() throws Exception {
        try (Fixture fixture = Fixture.open(tempDir.resolve("retention.sqlite"))) {
            List<AssignmentOverride> desired = List.of(
                    new AssignmentOverride(fixture.firstItemId, fixture.ada, 2));
            long firstId = 0;
            long newestId = 0;
            for (int i = 0; i < 11; i++) {
                OptimizationApplyResult applied = fixture.journal.apply(
                        fixture.setlistId, fixture.journal.revision(fixture.setlistId), desired, "{}");
                assertTrue(applied.applied(), applied.message());
                if (i == 0) {
                    firstId = applied.operationId();
                }
                newestId = applied.operationId();
            }
            assertEquals(OptimizationJournal.MAX_ENTRIES_PER_SETLIST, countHistory(fixture.database, fixture.setlistId));
            assertEquals(0, countHistoryId(fixture.database, firstId));
            assertEquals(APPLIED, status(fixture.database, newestId));

            String old = Instant.now().minus(OptimizationJournal.MAX_AGE_DAYS + 1, ChronoUnit.DAYS).toString();
            try (PreparedStatement statement = fixture.database.connection().prepareStatement(
                    "UPDATE SetlistOptimizationHistory SET created_at = ? WHERE setlist_id = ?")) {
                statement.setString(1, old);
                statement.setLong(2, fixture.setlistId);
                statement.executeUpdate();
            }
            fixture.journal.pruneHistory(fixture.setlistId);
            assertEquals(1, countHistory(fixture.database, fixture.setlistId));
            assertEquals(APPLIED, status(fixture.database, newestId));
            OptimizationUndoResult undone = fixture.journal.undoLatest(fixture.setlistId);
            assertTrue(undone.undone(), undone.message());
            assertEquals(newestId, undone.operationId());
            assertEquals(desiredRows(desired), overrides(fixture));
        }
    }

    private static void assertRefusal(OptimizationUndoResult result, OptimizationRefusal reason) {
        assertFalse(result.undone());
        assertTrue(result.refusals().contains(reason), result.message());
        assertFalse(result.message().isBlank());
    }

    private static List<AssignmentOverride> desiredRows(List<AssignmentOverride> rows) {
        return new OptimizationRevision(null, List.of(), List.of(), rows).overrides();
    }

    private static List<AssignmentOverride> overrides(Fixture fixture) throws Exception {
        return overrides(fixture.setlists, fixture.setlistId);
    }

    private static List<AssignmentOverride> overrides(SqliteSetlistRepository setlists, long setlistId)
            throws Exception {
        List<AssignmentOverride> rows = new ArrayList<>();
        for (SetlistItemInfo item : setlists.listItems(setlistId)) {
            for (SetlistBandAssignmentInfo row : setlists.listBandAssignments(item.id())) {
                rows.add(new AssignmentOverride(row.setlistItemId(), row.playerId(), row.partNumber()));
            }
        }
        return desiredRows(rows);
    }

    private static int part(Fixture fixture, long itemId, long playerId) throws Exception {
        for (AssignmentOverride row : overrides(fixture)) {
            if (row.setlistItemId() == itemId && row.playerId() == playerId) {
                return row.partNumber();
            }
        }
        return -1;
    }

    private static void insertAssignment(SqliteDatabase database, long itemId, long playerId, int part)
            throws Exception {
        String now = SqliteTimestamps.now();
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                INSERT INTO SetlistBandAssignment
                    (setlist_item_id, player_id, part_number, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, itemId);
            statement.setLong(2, playerId);
            statement.setInt(3, part);
            statement.setString(4, now);
            statement.setString(5, now);
            statement.executeUpdate();
        }
    }

    private static void setLastSetlistItem(SqliteDatabase database, long songId, long itemId) throws Exception {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "UPDATE Song SET last_setlist_item_id = ? WHERE id = ?")) {
            statement.setLong(1, itemId);
            statement.setLong(2, songId);
            statement.executeUpdate();
        }
    }

    private static long lastSetlistItem(SqliteDatabase database, long songId) throws Exception {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT last_setlist_item_id FROM Song WHERE id = ?")) {
            statement.setLong(1, songId);
            try (ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next());
                return rs.getLong(1);
            }
        }
    }

    private static void setParts(SqliteDatabase database, long songId, String parts) throws Exception {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "UPDATE Song SET parts = ? WHERE id = ?")) {
            statement.setString(1, parts);
            statement.setLong(2, songId);
            statement.executeUpdate();
        }
    }

    private static String songParts(SqliteDatabase database, long songId) throws Exception {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT parts FROM Song WHERE id = ?")) {
            statement.setLong(1, songId);
            try (ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    private static String layoutRows(SqliteDatabase database, long songLayoutId) throws Exception {
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                SELECT player_id, part_number
                FROM SongLayoutAssignment
                WHERE song_layout_id = ?
                ORDER BY player_id, id
                """)) {
            statement.setLong(1, songLayoutId);
            try (ResultSet rs = statement.executeQuery()) {
                StringBuilder text = new StringBuilder();
                while (rs.next()) {
                    text.append(rs.getLong("player_id")).append(':');
                    text.append(rs.getObject("part_number") == null ? "null" : rs.getInt("part_number"));
                    text.append(';');
                }
                return text.toString();
            }
        }
    }

    private static int countHistory(SqliteDatabase database, long setlistId) throws Exception {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT COUNT(*) FROM SetlistOptimizationHistory WHERE setlist_id = ?")) {
            statement.setLong(1, setlistId);
            try (ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private static int countHistoryId(SqliteDatabase database, long operationId) throws Exception {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT COUNT(*) FROM SetlistOptimizationHistory WHERE id = ?")) {
            statement.setLong(1, operationId);
            try (ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private static String status(SqliteDatabase database, long operationId) throws Exception {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT status FROM SetlistOptimizationHistory WHERE id = ?")) {
            statement.setLong(1, operationId);
            try (ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    private static long fixturePlayer(SqliteDatabase database, String name) throws Exception {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT id FROM Player WHERE name = ?")) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next());
                return rs.getLong(1);
            }
        }
    }

    private static long insertSong(SqliteDatabase database, String parts) throws Exception {
        String now = SqliteTimestamps.now();
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                INSERT INTO Song (title, composers, total_plays, parts, created_at, updated_at)
                VALUES ('Song', 'Composer', 0, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, parts);
            statement.setString(2, now);
            statement.setString(3, now);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    private static final String APPLIED = "APPLIED";
    private static final String UNDONE = "UNDONE";

    private static final class Fixture implements AutoCloseable {
        final SqliteDatabase database;
        final SqliteOptimizationJournal journal;
        final SqliteSetlistRepository setlists;
        final SqliteOptimizationProfileRepository profiles;
        final long setlistId;
        final long songId;
        final long firstItemId;
        final long secondItemId;
        final long ada;
        final long bob;
        final long cara;
        final long bandLayoutId;
        final long songLayoutId;
        final long otherSongLayoutId;

        private Fixture(
                SqliteDatabase database,
                SqliteOptimizationJournal journal,
                SqliteSetlistRepository setlists,
                SqliteOptimizationProfileRepository profiles,
                long setlistId,
                long songId,
                long firstItemId,
                long secondItemId,
                long ada,
                long bob,
                long cara,
                long bandLayoutId,
                long songLayoutId,
                long otherSongLayoutId) {
            this.database = database;
            this.journal = journal;
            this.setlists = setlists;
            this.profiles = profiles;
            this.setlistId = setlistId;
            this.songId = songId;
            this.firstItemId = firstItemId;
            this.secondItemId = secondItemId;
            this.ada = ada;
            this.bob = bob;
            this.cara = cara;
            this.bandLayoutId = bandLayoutId;
            this.songLayoutId = songLayoutId;
            this.otherSongLayoutId = otherSongLayoutId;
        }

        static Fixture open(Path file) throws Exception {
            SqliteDatabase database = SqliteDatabase.openOrCreate(file);
            SqliteBandRepository bands = new SqliteBandRepository(database);
            SqlitePlayerRepository players = new SqlitePlayerRepository(database);
            SqliteSetlistRepository setlists = new SqliteSetlistRepository(database);
            SqliteSongLayoutRepository layouts = new SqliteSongLayoutRepository(database);
            long bandId = bands.addBand("Band", null);
            long bandLayoutId = bands.getOrCreatePrimaryLayout(bandId).id();
            long otherLayoutId = bands.addLayout(bandId, "Other");
            long ada = players.addPlayer("Ada", null, null);
            long bob = players.addPlayer("Bob", null, null);
            long cara = players.addPlayer("Cara", null, null);
            long songId = insertSong(database, "[{\"name\":\"Fiddle\"}]");
            SongLayoutInfo songLayout = layouts.getOrCreateSongLayout(songId, bandLayoutId, "Default");
            layouts.setAssignment(songLayout.id(), ada, 1);
            layouts.setAssignment(songLayout.id(), bob, 2);
            long otherSongLayoutId = layouts.getOrCreateSongLayout(songId, otherLayoutId, "Other").id();
            long setlistId = setlists.addSetlist("Gig", null);
            setlists.updateSetlist(
                    setlistId, "Gig", bandLayoutId, null, 0, false, null, null, null, null, null, null);
            long firstItemId = setlists.addItem(setlistId, songId, 0, null, songLayout.id());
            long secondItemId = setlists.addItem(setlistId, songId, 1, null, songLayout.id());
            return new Fixture(
                    database,
                    new SqliteOptimizationJournal(database),
                    setlists,
                    new SqliteOptimizationProfileRepository(database),
                    setlistId,
                    songId,
                    firstItemId,
                    secondItemId,
                    ada,
                    bob,
                    cara,
                    bandLayoutId,
                    songLayout.id(),
                    otherSongLayoutId);
        }

        void lock(boolean locked) throws Exception {
            setlists.updateSetlist(
                    setlistId, "Gig", bandLayoutId, null, 0, locked, null, null, null, null, null, null);
        }

        @Override
        public void close() {
            database.close();
        }
    }
}

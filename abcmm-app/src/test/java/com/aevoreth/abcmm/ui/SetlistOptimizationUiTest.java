package com.aevoreth.abcmm.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aevoreth.abcmm.domain.band.SongLayoutAssignmentInfo;
import com.aevoreth.abcmm.domain.optimizer.OptimizationJournal;
import com.aevoreth.abcmm.domain.optimizer.OptimizationRefusal;
import com.aevoreth.abcmm.domain.setlist.SetlistBandAssignmentInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistInfo;
import com.aevoreth.abcmm.domain.setlist.SetlistItemInfo;
import com.aevoreth.abcmm.optimizer.AssignedSlot;
import com.aevoreth.abcmm.optimizer.Assignment;
import com.aevoreth.abcmm.optimizer.LoadOutcome;
import com.aevoreth.abcmm.optimizer.SearchOutcome;
import com.aevoreth.abcmm.optimizer.StopReason;
import com.aevoreth.abcmm.storage.SqliteBandRepository;
import com.aevoreth.abcmm.storage.SqliteDatabase;
import com.aevoreth.abcmm.storage.SqliteOptimizationJournal;
import com.aevoreth.abcmm.storage.SqlitePlayerRepository;
import com.aevoreth.abcmm.storage.SqliteSetlistRepository;
import com.aevoreth.abcmm.storage.SqliteSongLayoutRepository;
import com.aevoreth.abcmm.storage.SqliteTimestamps;

class SetlistOptimizationUiTest {

    @TempDir
    Path tempDir;

    @Test
    void previewDoesNotWriteAndLockedSetlistDisablesApply() throws Exception {
        Path path = tempDir.resolve("preview.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(path)) {
            Library library = Library.open(database);
            List<SetlistBandAssignmentInfo> before = library.setlists.listBandAssignments(library.lockedItemId);

            SetlistOptimizationPanel locked = panel(path, library, library.lockedSetlistId);
            locked.loadOnCallerThread();
            SearchOutcome lockedSearch = locked.searchOnCallerThread(Duration.ZERO, () -> false);
            assertInstanceOf(SearchOutcome.Solved.class, lockedSearch);
            assertFalse(locked.applyButton().isEnabled());
            assertEquals(before, library.setlists.listBandAssignments(library.lockedItemId));

            SetlistOptimizationPanel open = panel(path, library, library.openSetlistId);
            open.loadOnCallerThread();
            SearchOutcome openSearch = open.searchOnCallerThread(Duration.ZERO, () -> false);
            assertInstanceOf(SearchOutcome.Solved.class, openSearch);
            assertTrue(open.applyButton().isEnabled());
            assertEquals(List.of(), library.setlists.listBandAssignments(library.openItemId));
        }
    }

    @Test
    void customBudgetUsesTheEnteredSeconds() throws Exception {
        Path path = tempDir.resolve("budget.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(path)) {
            Library library = Library.open(database);
            SetlistOptimizationPanel panel = panel(path, library, library.openSetlistId);
            panel.loadOnCallerThread();
            assertEquals(Duration.ofSeconds(10), panel.selectedDuration());
            panel.selectCustomSeconds(240);
            assertEquals(Duration.ofSeconds(240), panel.selectedDuration());
        }
    }

    @Test
    void cancelSkipsApplyAndUndoRefreshKeepsAbsentOverrides() throws Exception {
        Path path = tempDir.resolve("undo.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(path)) {
            Library library = Library.open(database);
            SetlistOptimizationPanel panel = panel(path, library, library.openSetlistId);
            panel.loadOnCallerThread();
            SearchOutcome cancelled = panel.searchOnCallerThread(Duration.ofSeconds(2), () -> true);

            SearchOutcome.Solved solved = assertInstanceOf(SearchOutcome.Solved.class, cancelled);
            assertEquals(StopReason.CANCELLED, solved.reason());
            assertTrue(panel.applyButton().isEnabled());
            assertTrue(library.journal.undoLatest(library.openSetlistId).refusals()
                    .contains(OptimizationRefusal.NOTHING_TO_UNDO));
            assertEquals(List.of(), library.setlists.listBandAssignments(library.openItemId));

            LoadOutcome outcome = SetlistOptimizationActions.load(path, library.openSetlistId, null);
            long itemId = outcome.problem().occurrences().get(0).setlistItemId();
            assertTrue(SetlistOptimizationActions.apply(
                    library.journal,
                    outcome,
                    new Assignment(List.of(new AssignedSlot(itemId, library.ada, null, null)))).applied());
            List<SetlistBandAssignmentInfo> applied = library.setlists.listBandAssignments(itemId);
            assertEquals(1, applied.size());
            assertNull(applied.get(0).partNumber());

            SetlistsPanel editor = new SetlistsPanel();
            editor.bind(library.players, library.bands, library.setlists, null, library.layouts);
            editor.setOptimizationJournal(path, library.journal);
            editor.reload();
            editor.navigateToSetlist(library.openSetlistId);
            assertEquals(
                    "Restored the assignments from before the last optimization.",
                    editor.undoLastOptimization());

            assertEquals(List.of(), library.setlists.listBandAssignments(itemId));
            List<SongLayoutAssignmentInfo> layout = library.layouts.listAssignments(library.songLayoutId);
            assertEquals(1, layout.size());
            assertEquals(library.ada, layout.get(0).playerId());
            assertEquals(1, layout.get(0).partNumber());
            List<SetlistItemInfo> items = library.setlists.listItems(library.openSetlistId);
            assertEquals(1, items.size());
            assertEquals(0, items.get(0).position());
            assertEquals(itemId, items.get(0).id());
        }
    }

    @Test
    void unifiedSongExceptionsStageChangesAndKeepRepeatedOccurrencesSeparate() throws Exception {
        Path path = tempDir.resolve("exceptions.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(path)) {
            Library library = Library.open(database);
            var profiles = new com.aevoreth.abcmm.storage.SqliteOptimizationProfileRepository(database);
            var item = library.setlists.listItems(library.openSetlistId).getFirst();
            long repeat = library.setlists.addItem(library.openSetlistId, item.songId(), 1, null, library.songLayoutId);
            var old = new com.aevoreth.abcmm.domain.optimizer.OccurrencePreferences(
                    List.of(new com.aevoreth.abcmm.domain.optimizer.PartPreferenceWeight(item.id(), 1, library.ada, 7)),
                    List.of(new com.aevoreth.abcmm.domain.optimizer.ItemKeepWeight(item.id(), 2)),
                    List.of(new com.aevoreth.abcmm.domain.optimizer.PartKeepWeight(item.id(), 1, 3)));
            profiles.replaceOccurrencePreferences(library.openSetlistId, old);
            profiles.replacePins(library.openSetlistId, List.of(new com.aevoreth.abcmm.domain.optimizer.AssignmentPin(item.id(), 1, library.ada)));
            var loaded = SetlistOptimizationActions.load(path, library.openSetlistId, null);
            var form = new SongExceptionsForm(profiles, loaded.problem(), library.setlists.listItems(library.openSetlistId), java.util.Map.of(library.ada, "Ada"));
            if (System.getProperty("optimizer.snapshot") != null) UnifiedOptimizerSettingsTest.snapshot(form, "song-exceptions");
            var table = UnifiedOptimizerSettingsTest.find(form, javax.swing.JTable.class);
            assertEquals("Pin to character", table.getValueAt(0, 1));
            form.validateEdits(); form.save();
            assertEquals(old, profiles.listOccurrencePreferences(library.openSetlistId));
            table.setValueAt("Automatic", 0, 1);
            // A pending edit does not write until the containing settings window saves.
            form.validateEdits();
            assertEquals(1, profiles.listPins(library.openSetlistId).size());
            UnifiedOptimizerSettingsTest.find(form, javax.swing.JComboBox.class).setSelectedIndex(1);
            assertEquals("Automatic", table.getValueAt(0, 1));
            table.setValueAt("Prefer character", 0, 1); table.setValueAt("11", 0, 3);
            form.save();
            var saved = profiles.listOccurrencePreferences(library.openSetlistId);
            assertEquals(List.of(new com.aevoreth.abcmm.domain.optimizer.PartPreferenceWeight(repeat, 1, library.ada, 11)), saved.partPreferences());
            assertEquals(old.itemKeepWeights(), saved.itemKeepWeights());
            assertEquals(old.partKeepWeights(), saved.partKeepWeights());
            assertTrue(profiles.listPins(library.openSetlistId).isEmpty());
            table.setValueAt("NaN", 0, 3);
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, form::validateEdits);
        }
    }

    @Test
    void reloadingRulesKeepsTheChosenSearchDuration() throws Exception {
        Path path = tempDir.resolve("reload.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(path)) {
            Library library = Library.open(database);
            var panel = panel(path, library, library.openSetlistId);
            var preferences = new com.aevoreth.abcmm.domain.prefs.Preferences();
            panel.bindDurationPreferences(preferences, null);
            panel.loadOnCallerThread(); panel.selectCustomSeconds(240); panel.rememberDuration(); panel.loadOnCallerThread();
            assertEquals(Duration.ofSeconds(240), panel.selectedDuration());
            var reopened = panel(path, library, library.openSetlistId);
            reopened.bindDurationPreferences(preferences, null); reopened.loadOnCallerThread();
            assertEquals(Duration.ofSeconds(240), reopened.selectedDuration());
        }
    }

    @Test
    void optimizerAssignsSongsWithoutExistingLayoutAssignments() throws Exception {
        Path path = tempDir.resolve("unassigned.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(path)) {
            Library library = Library.open(database);
            library.layouts.deleteSongLayout(library.songLayoutId);
            var loaded = SetlistOptimizationActions.load(path, library.openSetlistId, null);
            assertTrue(loaded.report().integrityOk());
            assertFalse(loaded.report().baselineFeasible());
            var preview = panel(path, library, library.openSetlistId);
            preview.loadOnCallerThread();
            assertFalse(preview.ruleWarningVisible());
            var solved = assertInstanceOf(SearchOutcome.Solved.class,
                    preview.searchOnCallerThread(Duration.ZERO, () -> false));
            assertEquals(1, solved.assignment().slot(library.openItemId, library.ada).partNumber());
            assertTrue(preview.applyButton().isEnabled());
            assertTrue(SetlistOptimizationActions.apply(library.journal, loaded, solved.assignment()).applied());
            assertEquals(1, library.setlists.listBandAssignments(library.openItemId).getFirst().partNumber());
        }
    }

    @Test
    void explicitCleanupOfOffLayoutRulesUnblocksAutoAssignmentWithoutDroppingValidPins() throws Exception {
        Path path = tempDir.resolve("off-layout.sqlite");
        try (SqliteDatabase database = SqliteDatabase.openOrCreate(path)) {
            Library library = Library.open(database);
            library.layouts.deleteSongLayout(library.songLayoutId);
            var item = library.setlists.listItems(library.openSetlistId).getFirst();
            long repeat = library.setlists.addItem(library.openSetlistId, item.songId(), 1, null, null);
            long outsider = library.players.addPlayer("Outside band", null, null);
            var profiles = new com.aevoreth.abcmm.storage.SqliteOptimizationProfileRepository(database);
            var preferences = new com.aevoreth.abcmm.domain.optimizer.OccurrencePreferences(
                    List.of(new com.aevoreth.abcmm.domain.optimizer.PartPreferenceWeight(item.id(), 1, outsider, 7)),
                    List.of(new com.aevoreth.abcmm.domain.optimizer.ItemKeepWeight(item.id(), 3)),
                    List.of(new com.aevoreth.abcmm.domain.optimizer.PartKeepWeight(item.id(), 1, 4)));
            var validPin = new com.aevoreth.abcmm.domain.optimizer.AssignmentPin(repeat, 1, library.ada);
            profiles.replaceOccurrencePreferences(library.openSetlistId, preferences);
            profiles.replacePins(library.openSetlistId, List.of(
                    new com.aevoreth.abcmm.domain.optimizer.AssignmentPin(item.id(), 1, outsider), validPin));
            var loaded = SetlistOptimizationActions.load(path, library.openSetlistId, null);
            assertInstanceOf(SearchOutcome.Infeasible.class, SetlistOptimizationActions.search(
                    loaded, Duration.ZERO, 0, () -> false, null));
            var blockedPreview = panel(path, library, library.openSetlistId);
            blockedPreview.loadOnCallerThread();
            assertTrue(blockedPreview.ruleWarningVisible(), "Show the fix warning before Search is run");
            assertEquals("Review and fix…", UnifiedOptimizerSettingsTest.find(blockedPreview, javax.swing.JButton.class).getText());
            var form = new SongExceptionsForm(profiles, loaded.problem(), library.setlists.listItems(library.openSetlistId),
                    java.util.Map.of(library.ada, "Ada", outsider, "Outside band"));
            form.clearOffLayoutRules();
            assertEquals(2, profiles.listPins(library.openSetlistId).size());
            assertEquals(preferences, profiles.listOccurrencePreferences(library.openSetlistId));
            form.save();
            assertEquals(List.of(validPin), profiles.listPins(library.openSetlistId));
            blockedPreview.loadOnCallerThread();
            assertFalse(blockedPreview.ruleWarningVisible(), "Clear the warning after rules are repaired");
            assertTrue(profiles.listOccurrencePreferences(library.openSetlistId).partPreferences().isEmpty());
            assertEquals(preferences.itemKeepWeights(), profiles.listOccurrencePreferences(library.openSetlistId).itemKeepWeights());
            assertEquals(preferences.partKeepWeights(), profiles.listOccurrencePreferences(library.openSetlistId).partKeepWeights());
            var preview = panel(path, library, library.openSetlistId);
            preview.loadOnCallerThread();
            var solved = assertInstanceOf(SearchOutcome.Solved.class, preview.searchOnCallerThread(Duration.ZERO, () -> false));
            assertEquals(2, solved.assignment().slots().stream().filter(slot -> slot.partNumber() != null).count());
            assertTrue(preview.applyButton().isEnabled());
        }
    }

    private static SetlistOptimizationPanel panel(Path path, Library library, long setlistId) throws Exception {
        return new SetlistOptimizationPanel(
                path,
                library.setlist(setlistId),
                "Band",
                library.journal,
                library.players,
                library.setlists,
                () -> {
                });
    }

    private static final class Library {
        final SqliteBandRepository bands;
        final SqlitePlayerRepository players;
        final SqliteSetlistRepository setlists;
        final SqliteSongLayoutRepository layouts;
        final OptimizationJournal journal;
        final long ada;
        final long songLayoutId;
        final long openSetlistId;
        final long openItemId;
        final long lockedSetlistId;
        final long lockedItemId;

        private Library(
                SqliteBandRepository bands,
                SqlitePlayerRepository players,
                SqliteSetlistRepository setlists,
                SqliteSongLayoutRepository layouts,
                OptimizationJournal journal,
                long ada,
                long songLayoutId,
                long openSetlistId,
                long openItemId,
                long lockedSetlistId,
                long lockedItemId) {
            this.bands = bands;
            this.players = players;
            this.setlists = setlists;
            this.layouts = layouts;
            this.journal = journal;
            this.ada = ada;
            this.songLayoutId = songLayoutId;
            this.openSetlistId = openSetlistId;
            this.openItemId = openItemId;
            this.lockedSetlistId = lockedSetlistId;
            this.lockedItemId = lockedItemId;
        }

        static Library open(SqliteDatabase database) throws Exception {
            SqliteBandRepository bands = new SqliteBandRepository(database);
            SqlitePlayerRepository players = new SqlitePlayerRepository(database);
            SqliteSetlistRepository setlists = new SqliteSetlistRepository(database);
            SqliteSongLayoutRepository layouts = new SqliteSongLayoutRepository(database);
            long bandId = bands.addBand("Band", null);
            long layoutId = bands.getOrCreatePrimaryLayout(bandId).id();
            long ada = players.addPlayer("Ada", null, null);
            bands.setSlot(layoutId, ada, 0, 0, 7, 5);
            long fiddle = instrumentId(database, "Basic Fiddle");
            players.setPlayerInstrument(ada, fiddle, true, false, null);
            long songId = insertSong(database, part(fiddle));
            long songLayoutId = layouts.getOrCreateSongLayout(songId, layoutId, "Default").id();
            layouts.setAssignment(songLayoutId, ada, 1);

            long openSetlistId = setlists.addSetlist("Open", null);
            setlists.updateSetlist(
                    openSetlistId, "Open", layoutId, null, 0, false, null, null, null, null, null, null);
            long openItemId = setlists.addItem(openSetlistId, songId, 0, null, songLayoutId);

            long lockedSetlistId = setlists.addSetlist("Locked", null);
            setlists.updateSetlist(
                    lockedSetlistId, "Locked", layoutId, null, 0, true, null, null, null, null, null, null);
            long lockedItemId = setlists.addItem(lockedSetlistId, songId, 0, null, songLayoutId);

            return new Library(
                    bands,
                    players,
                    setlists,
                    layouts,
                    new SqliteOptimizationJournal(database),
                    ada,
                    songLayoutId,
                    openSetlistId,
                    openItemId,
                    lockedSetlistId,
                    lockedItemId);
        }

        SetlistInfo setlist(long id) throws Exception {
            for (SetlistInfo info : setlists.listSetlists()) {
                if (info.id() == id) {
                    return info;
                }
            }
            throw new AssertionError("missing setlist " + id);
        }

        private static long instrumentId(SqliteDatabase database, String name) throws Exception {
            try (PreparedStatement statement = database.connection().prepareStatement(
                    "SELECT id FROM Instrument WHERE name = ?")) {
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
                    VALUES ('Tune', 'Composer', 0, ?, ?, ?)
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

        private static String part(long instrumentId) {
            return "[{\"part_number\":1,\"part_name\":\"Fiddle\",\"instrument_id\":" + instrumentId
                    + ",\"made_for\":null}]";
        }
    }
}

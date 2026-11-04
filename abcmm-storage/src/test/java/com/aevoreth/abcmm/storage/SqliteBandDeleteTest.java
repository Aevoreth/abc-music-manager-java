package com.aevoreth.abcmm.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.aevoreth.abcmm.domain.band.BandUsage;
import com.aevoreth.abcmm.domain.library.LibraryException;

class SqliteBandDeleteTest {
    @TempDir Path tempDir;

    @Test void removesReferencesAndPreservesSongsSetsAndOtherBands() throws Exception {
        try (var db = SqliteDatabase.openOrCreate(tempDir.resolve("delete.sqlite"))) {
            var bands = new SqliteBandRepository(db);
            long band = bands.addBand("Old", null);
            long layout = bands.getOrCreatePrimaryLayout(band).id();
            long other = bands.addBand("Keep", null);
            long otherLayout = bands.getOrCreatePrimaryLayout(other).id();
            try (var sql = db.connection().createStatement()) {
                sql.executeUpdate("INSERT INTO Song(id,title,composers,duration_seconds,parts,created_at,updated_at) VALUES(1,'Song','Composer',60,'[]','now','now')");
                insert(sql, "INSERT INTO Player(id,name) VALUES(1,'Player')");
                insert(sql, "INSERT INTO SongLayout(id,song_id,band_layout_id,name) VALUES(1,1," + layout + ",'One'),(2,1," + layout + ",'Two'),(3,1," + otherLayout + ",'Keep')");
                insert(sql, "INSERT INTO Setlist(id,name,band_layout_id) VALUES(1,'Direct'," + layout + "),(2,'Indirect',NULL),(3,'Keep'," + otherLayout + ")");
                insert(sql, "INSERT INTO SetlistItem(id,setlist_id,song_id,position,song_layout_id) VALUES(1,1,1,0,1),(2,2,1,0,2),(3,3,1,0,3)");
                insert(sql, "INSERT INTO SongLayoutAssignment(song_layout_id,player_id,part_number) VALUES(1,1,1),(3,1,1)");
                insert(sql, "INSERT INTO SetlistBandAssignment(setlist_item_id,player_id,part_number) VALUES(1,1,1),(2,1,1),(3,1,1)");
                sql.executeUpdate("UPDATE Song SET last_band_layout_id=" + layout + ",last_song_layout_id=1 WHERE id=1");
            }
            bands.setSlot(layout, 1, 0, 0, 2, 2);
            try (var sql=db.connection().createStatement()) {
                sql.executeUpdate("INSERT INTO SetlistPartPreference(setlist_item_id,part_number,player_id,penalty) VALUES(1,1,1,1.0),(3,1,1,1.0)");
                sql.executeUpdate("INSERT INTO SetlistAssignmentPin(setlist_item_id,part_number,player_id) VALUES(2,1,1),(3,1,1)");
            }
            assertEquals(new BandUsage(1,2), bands.getBandUsage(band));
            bands.deleteBand(band);
            assertEquals(1, bands.listBands().size());
            assertEquals(1, count(db,"Song"));
            assertEquals(3, count(db,"Setlist"));
            assertEquals(3, count(db,"SetlistItem"));
            assertEquals(1, count(db,"SongLayout"));
            assertEquals(1, count(db,"SongLayoutAssignment"));
            assertEquals(1, count(db,"SetlistBandAssignment"));
            assertEquals(1, count(db,"SetlistPartPreference"));
            assertEquals(1, count(db,"SetlistAssignmentPin"));
            assertEquals(0, count(db,"BandLayoutSlot"));
            try (var sql=db.connection().createStatement(); var rs=sql.executeQuery(
                    "SELECT last_band_layout_id,last_song_layout_id FROM Song WHERE id=1")) {
                assertTrue(rs.next());
                assertNull(rs.getObject(1));
                assertNull(rs.getObject(2));
            }
            try (var sql=db.connection().createStatement(); var rs=sql.executeQuery("PRAGMA foreign_key_check")) {
                assertFalse(rs.next());
            }
        }
    }

    @Test void failureRollsBackCleanup() throws Exception {
        try (var db = SqliteDatabase.openOrCreate(tempDir.resolve("rollback.sqlite"))) {
            var bands = new SqliteBandRepository(db);
            long band = bands.addBand("Old", null);
            long layout = bands.getOrCreatePrimaryLayout(band).id();
            try (var sql=db.connection().createStatement()) {
                insert(sql, "INSERT INTO Setlist(id,name,band_layout_id) VALUES(1,'Set'," + layout + ")");
                sql.executeUpdate("CREATE TRIGGER stop_delete BEFORE DELETE ON Band BEGIN SELECT RAISE(ABORT,'test'); END");
            }
            assertThrows(LibraryException.class, () -> bands.deleteBand(band));
            assertEquals(new BandUsage(0,1), bands.getBandUsage(band));
            assertEquals(1, bands.listLayouts(band).size());
            assertTrue(db.connection().getAutoCommit());
        }
    }

    private static void insert(java.sql.Statement sql, String insert) throws Exception {
        sql.executeUpdate(insert.replace(") VALUES", ",created_at,updated_at) VALUES")
                .replaceAll("\\)(?=,|$)", ",'now','now')"));
    }

    private static int count(SqliteDatabase db,String table) throws Exception {
        try (var sql=db.connection().createStatement();var rs=sql.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next(); return rs.getInt(1);
        }
    }
}

package com.aevoreth.abcmm.domain.prefs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LotroPathsTest {

    @AfterEach
    void clearOverride() {
        LotroPaths.setDocumentsPathOverride(null);
    }

    @Test
    void defaultLotroRootFindsFolderUnderDocuments(@TempDir Path temp) throws Exception {
        Path docs = temp.resolve("Documents");
        Path lotro = docs.resolve(LotroPaths.LOTRO_FOLDER_NAME);
        Files.createDirectories(lotro.resolve(LotroPaths.MUSIC_FOLDER_NAME));
        LotroPaths.setDocumentsPathOverride(() -> Optional.of(docs));

        Optional<Path> found = LotroPaths.defaultLotroRoot();
        assertTrue(found.isPresent());
        assertEquals(lotro.toAbsolutePath().normalize(), found.get());
    }

    @Test
    void ensureDefaultLotroRootWritesWhenEmpty(@TempDir Path temp) throws Exception {
        Path docs = temp.resolve("Documents");
        Path lotro = docs.resolve(LotroPaths.LOTRO_FOLDER_NAME);
        Files.createDirectories(lotro);
        LotroPaths.setDocumentsPathOverride(() -> Optional.of(docs));

        Preferences prefs = new Preferences();
        assertTrue(LotroPaths.ensureDefaultLotroRoot(prefs));
        assertEquals(lotro.toAbsolutePath().normalize().toString(), prefs.lotroRoot());
        assertFalse(LotroPaths.ensureDefaultLotroRoot(prefs));
    }

    @Test
    void effectiveLotroRootFallsBackToDefaultWithoutWriting(@TempDir Path temp) throws Exception {
        Path docs = temp.resolve("Documents");
        Path lotro = docs.resolve(LotroPaths.LOTRO_FOLDER_NAME);
        Files.createDirectories(lotro);
        LotroPaths.setDocumentsPathOverride(() -> Optional.of(docs));

        Preferences prefs = new Preferences();
        assertTrue(prefs.lotroRoot().isBlank());
        assertEquals(
                lotro.toAbsolutePath().normalize(),
                LotroPaths.effectiveLotroRoot(prefs).orElseThrow());
        assertTrue(prefs.lotroRoot().isBlank());
    }

    @Test
    void toMusicRelativeStoresUnderMusic(@TempDir Path temp) throws Exception {
        Path lotro = temp.resolve("LOTRO");
        Path music = lotro.resolve(LotroPaths.MUSIC_FOLDER_NAME);
        Path export = music.resolve("Sets").resolve("Export");
        Files.createDirectories(export);

        String relative = LotroPaths.toMusicRelative(
                export.toString(), lotro.toString());
        assertEquals("Sets/Export", relative);
    }

    @Test
    void toMusicRelativeKeepsAlreadyRelativeWithoutUsingCwd(@TempDir Path temp) throws Exception {
        Path lotro = temp.resolve("LOTRO");
        Files.createDirectories(lotro.resolve(LotroPaths.MUSIC_FOLDER_NAME).resolve("Aev").resolve("Sets"));

        // Regression: Save must not resolve "Aev/Sets" against process CWD.
        assertEquals("Aev/Sets", LotroPaths.toMusicRelative("Aev/Sets", lotro.toString()));
        assertEquals("Aev/Sets", LotroPaths.toMusicRelative("Aev\\Sets", lotro.toString()));
        assertTrue(LotroPaths.isUnderMusic("Aev/Sets", lotro.toString()));
    }

    @Test
    void toMusicRelativeConvertsAbsoluteMusicPath(@TempDir Path temp) throws Exception {
        Path lotro = temp.resolve("LOTRO");
        Path sets = lotro.resolve(LotroPaths.MUSIC_FOLDER_NAME).resolve("Aev").resolve("Sets");
        Files.createDirectories(sets);

        assertEquals("Aev/Sets", LotroPaths.toMusicRelative(sets.toString(), lotro.toString()));
        assertTrue(LotroPaths.isUnderMusic(sets.toString(), lotro.toString()));
        assertFalse(LotroPaths.isUnderMusic(temp.resolve("elsewhere").toString(), lotro.toString()));
    }

    @Test
    void resolveMusicPathJoinsRelative(@TempDir Path temp) throws Exception {
        Path lotro = temp.resolve("LOTRO");
        Path music = lotro.resolve(LotroPaths.MUSIC_FOLDER_NAME);
        Files.createDirectories(music.resolve("Sets"));

        String resolved = LotroPaths.resolveMusicPath("Sets", lotro.toString());
        assertEquals(LotroPaths.canonicalize(music.resolve("Sets")).toString(), resolved);
    }

    @Test
    void discoverAccountTargetsListsPluginDataAccounts(@TempDir Path temp) throws Exception {
        Path lotro = temp.resolve("LOTRO");
        Path account = lotro.resolve(LotroPaths.PLUGIN_DATA_FOLDER_NAME).resolve("MyAccount");
        Files.createDirectories(account.resolve(LotroPaths.ALL_SERVERS_FOLDER_NAME));

        List<LotroPaths.DiscoveredAccount> found = LotroPaths.discoverAccountTargets(lotro.toString());
        assertEquals(1, found.size());
        assertEquals("MyAccount", found.get(0).accountName());
        assertTrue(found.get(0).pluginDataPath().endsWith(
                Path.of("MyAccount", LotroPaths.ALL_SERVERS_FOLDER_NAME).toString())
                || found.get(0).pluginDataPath().replace('\\', '/').endsWith("MyAccount/AllServers"));
    }
}

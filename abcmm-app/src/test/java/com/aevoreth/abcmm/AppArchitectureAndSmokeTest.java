package com.aevoreth.abcmm;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import com.aevoreth.abcmm.domain.library.LibrarySong;
import com.aevoreth.abcmm.domain.library.StatusInfo;
import com.aevoreth.abcmm.domain.playback.PlaybackState;
import com.aevoreth.abcmm.domain.prefs.DefaultFilters;
import com.aevoreth.abcmm.domain.prefs.Preferences;
import com.aevoreth.abcmm.maestro.StubAbcPlaybackEngine;
import com.aevoreth.abcmm.ui.LibraryPanel;
import com.aevoreth.abcmm.ui.SettingsDialog;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.aevoreth.abcmm",
        importOptions = ImportOption.DoNotIncludeTests.class)
class AppArchitectureAndSmokeTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    @ArchTest
    static final ArchRule appSourcesMustNotImportDigeroDirectly = noClasses()
            .that().resideInAPackage("com.aevoreth.abcmm..")
            .and().resideOutsideOfPackage("com.aevoreth.abcmm.maestro..")
            .and().resideOutsideOfPackage("com.digero..")
            .and().resideOutsideOfPackage("com.aifel..")
            .should().dependOnClassesThat().resideInAnyPackage("com.digero..", "com.aifel..");

    @Test
    void mainLookAndFeelInstallIsSafe() {
        assertDoesNotThrow(() -> AbcMusicManagerMain.installLookAndFeel());
    }

    @Test
    @DisabledIfSystemProperty(named = "java.awt.headless", matches = "true")
    void mainFrameConstructsWhenDisplayAvailable() {
        MainFrame frame = new MainFrame(new StubAbcPlaybackEngine());
        try {
            assertEquals(MainFrame.APP_TITLE, frame.getTitle());
            assertNotNull(frame.playbackEngine());
            assertEquals(PlaybackState.IDLE, frame.playbackEngine().getState());
            assertNotNull(frame.libraryPanel());
            assertNotNull(frame.statusBar());
        } finally {
            frame.dispose();
            frame.shutdown();
        }
    }

    @Test
    void playbackEngineFactoryReturnsIdleEngine() {
        try (var engine = new StubAbcPlaybackEngine()) {
            assertEquals(PlaybackState.IDLE, engine.getState());
        }
    }

    @Test
    void libraryPanelAcceptsSongsAndBuildsFilter() {
        LibraryPanel panel = new LibraryPanel();
        panel.setStatuses(List.of(new StatusInfo(1, "New", "#00F", 0)));
        panel.setSongs(List.of(new LibrarySong(
                1, "Song", "Composer", "Ada", 120, 2, "[{\"part_number\":1}]",
                null, 0, 3, 1L, "New", "#00F", null, null, false)));
        panel.setDefaultFilters(DefaultFilters.builtins());
        panel.applyDefaultFilters();
        assertNotNull(panel.currentFilter());
        panel.clearFilters();
        assertEquals("", panel.currentFilter().titleOrComposer());
    }

    @Test
    @DisabledIfSystemProperty(named = "java.awt.headless", matches = "true")
    void settingsDialogConstructsWithAllTabs() {
        SettingsDialog dialog = new SettingsDialog(
                null,
                new Preferences(),
                List.of(new StatusInfo(1, "New", "#00F", 0)),
                List.of(),
                List.of(),
                () -> {
                },
                prefs -> {
                });
        try {
            assertEquals("Settings", dialog.getTitle());
        } finally {
            dialog.dispose();
        }
    }
}

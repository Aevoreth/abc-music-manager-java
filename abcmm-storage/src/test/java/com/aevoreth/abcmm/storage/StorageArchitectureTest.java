package com.aevoreth.abcmm.storage;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.aevoreth.abcmm.storage", importOptions = ImportOption.DoNotIncludeTests.class)
class StorageArchitectureTest {

    @ArchTest
    static final ArchRule storageMustNotDependOnMaestro = noClasses()
            .that().resideInAPackage("com.aevoreth.abcmm.storage..")
            .should().dependOnClassesThat().resideInAnyPackage("com.digero..", "com.aifel..");

    @Test
    void dataPathsUsePythonCompatibleFileNames() {
        assertEquals("abc_music_manager.sqlite", DataPaths.DATABASE_FILE_NAME);
        assertEquals("preferences.json", DataPaths.PREFERENCES_FILE_NAME);
        assertTrue(DataPaths.databasePath().endsWith(DataPaths.DATABASE_FILE_NAME));
    }
}

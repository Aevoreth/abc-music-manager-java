package com.aevoreth.abcmm.optimizer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.aevoreth.abcmm.optimizer", importOptions = ImportOption.DoNotIncludeTests.class)
class OptimizerArchitectureTest {

    @ArchTest
    static final ArchRule coreAndCliMustNotTouchStorageOrUi = noClasses()
            .that().resideInAnyPackage(
                    "com.aevoreth.abcmm.optimizer",
                    "com.aevoreth.abcmm.optimizer.cli")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "java.sql..",
                    "org.sqlite..",
                    "com.aevoreth.abcmm.storage..",
                    "javax.swing..",
                    "java.awt..",
                    "com.digero..",
                    "com.aifel..");

    @ArchTest
    static final ArchRule optimizerMustNotDependOnUiOrMaestro = noClasses()
            .that().resideInAPackage("com.aevoreth.abcmm.optimizer..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "javax.swing..",
                    "java.awt..",
                    "com.digero..",
                    "com.aifel..");
}

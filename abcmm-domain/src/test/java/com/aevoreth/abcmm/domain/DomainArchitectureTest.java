package com.aevoreth.abcmm.domain;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.aevoreth.abcmm.domain", importOptions = ImportOption.DoNotIncludeTests.class)
class DomainArchitectureTest {

    @ArchTest
    static final ArchRule domainMustNotDependOnMaestro = noClasses()
            .that().resideInAPackage("com.aevoreth.abcmm.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("com.digero..", "com.aifel..");

    @ArchTest
    static final ArchRule domainMustNotDependOnSwing = noClasses()
            .that().resideInAPackage("com.aevoreth.abcmm.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("javax.swing..", "java.awt..");

    @ArchTest
    static final ArchRule domainMustNotDependOnSqlite = noClasses()
            .that().resideInAPackage("com.aevoreth.abcmm.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("java.sql..", "org.sqlite..");
}

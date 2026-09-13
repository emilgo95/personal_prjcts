package com.agriaudit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Reglas de arquitectura con ArchUnit (RT-01, RT-A01, RT-A03). Complementan a {@link ModularityTests}:
 * Modulith vigila los límites entre módulos y estas reglas, la forma interna de cada módulo.
 */
class ArchitectureTests {

    /**
     * Módulos con hexagonal estricto: su {@code domain} no conoce ni el framework ni la persistencia
     * ni el SDK de IA. Se añaden al crearlos o al migrarlos ({@code fieldbook}, {@code evidence},
     * {@code ai}, y {@code audit}/{@code findings} cuando WS-2/WS-3 los toquen). {@code catalog} no
     * está: es CRUD plano y sus entidades JPA viven en su {@code domain}.
     */
    static final String[] HEXAGONAL_DOMAINS = {
            "com.agriaudit.fieldbook.domain..",
            "com.agriaudit.evidence.domain..",
            "com.agriaudit.ai.domain..",
    };

    /** Paquetes por capas heredados, que desaparecen al terminar WS-1. */
    static final String[] LEGACY_LAYERS = {
            "com.agriaudit.service..", "com.agriaudit.web..", "com.agriaudit.repo..", "com.agriaudit.domain..",
    };

    static final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.agriaudit");

    @Test
    void hexagonalDomainDoesNotDependOnFrameworkPersistenceOrAiSdk() {
        noClasses().that().resideInAnyPackage(HEXAGONAL_DOMAINS)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.persistence..", "org.hibernate..", "org.springframework..", "com.anthropic..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void domainDoesNotDependOnInfrastructure() {
        noClasses().that().resideInAPackage("com.agriaudit.*.domain..")
                .should().dependOnClassesThat().resideInAPackage("com.agriaudit.*.infrastructure..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void extractedModulesDoNotDependOnLegacyLayers() {
        noClasses().that().resideInAnyPackage("com.agriaudit.catalog..", "com.agriaudit.common..", "com.agriaudit.identity..")
                .should().dependOnClassesThat().resideInAnyPackage(LEGACY_LAYERS)
                .check(classes);
    }

    @Test
    void onlyTheAiModuleTalksToTheAiProviderSdk() {
        noClasses().that().resideOutsideOfPackage("com.agriaudit.ai.infrastructure..")
                .should().dependOnClassesThat().resideInAPackage("com.anthropic..")
                .allowEmptyShould(true)
                .check(classes);
    }
}

package com.agriaudit;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verificación del monolito modular con Spring Modulith (RT-00, RT-02), bloqueante en CI (RT-50):
 * sin ciclos entre módulos y sin acceso a tipos internos de otro módulo (solo a su paquete raíz o a sus
 * interfaces con nombre).
 */
class ModularityTests {

    static final ApplicationModules modules = ApplicationModules.of(AgriAuditApplication.class);

    @Test
    void verifiesModularStructure() {
        modules.verify();
    }

    /** Diagramas de componentes y lienzos por módulo en {@code target/spring-modulith-docs}. */
    @Test
    void writesModuleDocumentation() {
        new Documenter(modules).writeDocumentation();
    }
}

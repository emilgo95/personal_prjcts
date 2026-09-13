package com.agriaudit;

import com.agriaudit.support.TestPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tras las migraciones de Flyway, cada tabla vive en el esquema de su módulo y en {@code public} solo
 * queda el historial de Flyway (RT-02, RT-30). Hibernate valida además que las entidades coinciden.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ModuleSchemasIntegrationTest {

    static final List<String> MODULE_SCHEMAS =
            List.of("identity", "catalog", "fieldbook", "storage", "audit", "findings", "documents", "ai");

    @Container
    static PostgreSQLContainer<?> postgres = TestPostgres.container();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void publicSchemaOnlyKeepsFlywayHistory() {
        List<String> publicTables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' "
                        + "AND table_type = 'BASE TABLE' ORDER BY table_name", String.class);

        assertThat(publicTables).containsExactly("flyway_schema_history");
    }

    @Test
    void everyModuleSchemaHasItsTables() {
        List<String> schemasWithTables = jdbc.queryForList(
                "SELECT DISTINCT table_schema FROM information_schema.tables WHERE table_type = 'BASE TABLE' "
                        + "AND table_schema NOT IN ('public', 'pg_catalog', 'information_schema')", String.class);

        assertThat(schemasWithTables).containsExactlyInAnyOrderElementsOf(MODULE_SCHEMAS);
    }

    @Test
    void referenceSequencesAreOwnedByEachModule() {
        List<String> owners = jdbc.queryForList(
                "SELECT table_schema FROM information_schema.tables WHERE table_name = 'reference_sequences' "
                        + "ORDER BY table_schema", String.class);

        assertThat(owners).containsExactly("audit", "findings");
    }
}

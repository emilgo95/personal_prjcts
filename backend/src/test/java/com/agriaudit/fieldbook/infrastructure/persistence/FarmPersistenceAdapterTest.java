package com.agriaudit.fieldbook.infrastructure.persistence;

import com.agriaudit.fieldbook.port.FarmRepository;
import com.agriaudit.fieldbook.port.FarmRepositoryContract;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.agriaudit.support.TestPostgres;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** El adaptador JPA pasa el contrato del puerto contra PostgreSQL real con el esquema de Flyway. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class FarmPersistenceAdapterTest extends FarmRepositoryContract {

    @Container
    static PostgreSQLContainer<?> postgres = TestPostgres.container();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    FarmJpaRepository jpa;

    @Autowired
    JdbcTemplate jdbc;

    private long orgA;
    private long orgB;

    @BeforeEach
    void organizations() {
        orgA = jdbc.queryForObject("INSERT INTO identity.organizations (name) VALUES ('Contrato A') "
                + "ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name RETURNING id", Long.class);
        orgB = jdbc.queryForObject("INSERT INTO identity.organizations (name) VALUES ('Contrato B') "
                + "ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name RETURNING id", Long.class);
    }

    @Override
    protected FarmRepository repository() {
        return new FarmPersistenceAdapter(jpa);
    }

    @Override
    protected long organizationA() {
        return orgA;
    }

    @Override
    protected long organizationB() {
        return orgB;
    }
}

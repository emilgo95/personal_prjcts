package com.agriaudit.support;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** PostgreSQL de los tests de integración: la misma imagen que {@code docker-compose.yml} (con pgvector). */
public final class TestPostgres {

    public static final DockerImageName IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres");

    private TestPostgres() {
    }

    public static PostgreSQLContainer<?> container() {
        return new PostgreSQLContainer<>(IMAGE);
    }
}

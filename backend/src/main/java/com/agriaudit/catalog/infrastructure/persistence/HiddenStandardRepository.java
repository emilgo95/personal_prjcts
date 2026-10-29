package com.agriaudit.catalog.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.Set;

/** Estándares comunes que cada organización oculta en sus pantallas ({@code catalog.hidden_standard}). */
@Repository
public class HiddenStandardRepository {

    private final JdbcTemplate jdbc;

    public HiddenStandardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Set<Long> hiddenBy(Long organizationId) {
        if (organizationId == null) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList(
                "SELECT standard_id FROM catalog.hidden_standard WHERE organization_id = ?", Long.class,
                organizationId));
    }

    public void hide(Long organizationId, Long standardId) {
        jdbc.update("INSERT INTO catalog.hidden_standard (organization_id, standard_id) VALUES (?, ?) "
                + "ON CONFLICT DO NOTHING", organizationId, standardId);
    }

    public void show(Long organizationId, Long standardId) {
        jdbc.update("DELETE FROM catalog.hidden_standard WHERE organization_id = ? AND standard_id = ?",
                organizationId, standardId);
    }
}

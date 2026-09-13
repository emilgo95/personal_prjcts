package com.agriaudit.findings.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Contador atómico de referencias de no conformidad por auditoría (RT-27), sobre la tabla
 * {@code reference_sequences} (ámbitos {@code FINDING:audit:<id>}). Mismo patrón que
 * {@code AuditReferenceSequence}; la unicidad la garantiza además {@code uq_findings_audit_reference}.
 */
@Repository
public class FindingReferenceSequence {

    private static final String NEXT_FINDING_NUMBER = """
            INSERT INTO reference_sequences (scope, last_value)
            VALUES (?, (SELECT COALESCE(MAX(substring(reference FROM '^NC-(\\d{1,9})$')::BIGINT), 0)
                        FROM findings WHERE audit_id = ?) + 1)
            ON CONFLICT (scope) DO UPDATE
                SET last_value = GREATEST(reference_sequences.last_value, EXCLUDED.last_value - 1) + 1
            RETURNING last_value
            """;

    private final JdbcTemplate jdbc;

    public FindingReferenceSequence(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Siguiente número de no conformidad de la auditoría. Llamar dentro de la transacción del alta. */
    public long nextFindingNumber(Long auditId) {
        return jdbc.queryForObject(NEXT_FINDING_NUMBER, Long.class, "FINDING:audit:" + auditId, auditId);
    }
}

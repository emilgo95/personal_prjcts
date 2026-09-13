package com.agriaudit.audit.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Contador atómico de referencias de auditoría por organización (RT-27), sobre la tabla
 * {@code reference_sequences} (ámbitos {@code AUDIT:org:<id>}).
 *
 * <p>Un único {@code INSERT ... ON CONFLICT DO UPDATE ... RETURNING}: la fila queda bloqueada hasta que
 * termina la transacción del alta. El valor es {@code max(contador, mayor número ya usado) + 1}, así
 * que arranca desde los datos existentes y se corrige solo si una referencia se escribió por otra vía.
 */
@Repository
public class AuditReferenceSequence {

    private static final String NEXT_AUDIT_NUMBER = """
            INSERT INTO reference_sequences (scope, last_value)
            VALUES (?, (SELECT COALESCE(MAX(substring(reference FROM '-(\\d{1,9})$')::BIGINT), 0)
                        FROM audits WHERE organization_id = ?) + 1)
            ON CONFLICT (scope) DO UPDATE
                SET last_value = GREATEST(reference_sequences.last_value, EXCLUDED.last_value - 1) + 1
            RETURNING last_value
            """;

    private final JdbcTemplate jdbc;

    public AuditReferenceSequence(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Siguiente número de auditoría de la organización. Llamar dentro de la transacción del alta. */
    public long nextAuditNumber(Long organizationId) {
        return jdbc.queryForObject(NEXT_AUDIT_NUMBER, Long.class, "AUDIT:org:" + organizationId, organizationId);
    }
}

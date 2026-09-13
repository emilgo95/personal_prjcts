package com.agriaudit.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Contadores atómicos para las referencias de negocio (RT-27).
 *
 * <p>Cada llamada es un único {@code INSERT ... ON CONFLICT DO UPDATE ... RETURNING}: la fila del
 * ámbito queda bloqueada hasta que termina la transacción que la incrementa, así que dos altas
 * simultáneas nunca obtienen el mismo número, y si la transacción se deshace el número se libera.
 *
 * <p>El valor nuevo es {@code max(contador, mayor número ya usado) + 1}: el contador arranca desde
 * los datos existentes (incluidos los que se insertan sin pasar por aquí, como los datos de demo)
 * y se corrige solo si alguna referencia se escribió por otra vía.
 */
@Repository
public class ReferenceSequenceRepository {

    private static final String NEXT_AUDIT_NUMBER = """
            INSERT INTO reference_sequences (scope, last_value)
            VALUES (?, (SELECT COALESCE(MAX(substring(reference FROM '-(\\d{1,9})$')::BIGINT), 0)
                        FROM audits WHERE organization_id = ?) + 1)
            ON CONFLICT (scope) DO UPDATE
                SET last_value = GREATEST(reference_sequences.last_value, EXCLUDED.last_value - 1) + 1
            RETURNING last_value
            """;

    private static final String NEXT_FINDING_NUMBER = """
            INSERT INTO reference_sequences (scope, last_value)
            VALUES (?, (SELECT COALESCE(MAX(substring(reference FROM '^NC-(\\d{1,9})$')::BIGINT), 0)
                        FROM findings WHERE audit_id = ?) + 1)
            ON CONFLICT (scope) DO UPDATE
                SET last_value = GREATEST(reference_sequences.last_value, EXCLUDED.last_value - 1) + 1
            RETURNING last_value
            """;

    private final JdbcTemplate jdbc;

    public ReferenceSequenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Siguiente número de auditoría de la organización. Llamar dentro de la transacción del alta. */
    public long nextAuditNumber(Long organizationId) {
        return jdbc.queryForObject(NEXT_AUDIT_NUMBER, Long.class, "AUDIT:org:" + organizationId, organizationId);
    }

    /** Siguiente número de no conformidad de la auditoría. Llamar dentro de la transacción del alta. */
    public long nextFindingNumber(Long auditId) {
        return jdbc.queryForObject(NEXT_FINDING_NUMBER, Long.class, "FINDING:audit:" + auditId, auditId);
    }
}

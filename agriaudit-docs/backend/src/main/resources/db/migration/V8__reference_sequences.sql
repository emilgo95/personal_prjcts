-- RT-27 · Referencias de negocio sin condiciones de carrera.
--
-- Antes, AuditService y FindingService calculaban la siguiente referencia contando filas
-- («count + 1») sin bloqueo: dos altas simultáneas podían obtener la misma referencia, y en
-- `findings` además se repetía al borrar una NC (el recuento baja). Ahora la referencia sale de
-- un contador atómico por ámbito (`reference_sequences`, ver ReferenceSequenceRepository) y la
-- unicidad de las NC queda garantizada por la base de datos.

CREATE TABLE reference_sequences (
    scope       VARCHAR(80) PRIMARY KEY,   -- p. ej. 'AUDIT:org:1', 'FINDING:audit:42'
    last_value  BIGINT      NOT NULL
);

-- Renumerar las NC con referencia duplicada dentro de la misma auditoría: se conserva la más
-- antigua y las demás reciben números nuevos a continuación del máximo existente.
WITH ranked AS (
    SELECT id, audit_id,
           row_number() OVER (PARTITION BY audit_id, reference ORDER BY id) AS rn
    FROM findings
    WHERE reference IS NOT NULL
),
dups AS (
    SELECT id, audit_id, row_number() OVER (PARTITION BY audit_id ORDER BY id) AS k
    FROM ranked
    WHERE rn > 1
),
maxes AS (
    SELECT audit_id,
           COALESCE(MAX(substring(reference FROM '^NC-(\d{1,9})$')::BIGINT), 0) AS mx
    FROM findings
    GROUP BY audit_id
)
UPDATE findings f
SET reference = 'NC-' || CASE WHEN m.mx + d.k < 1000
                              THEN lpad((m.mx + d.k)::TEXT, 3, '0')
                              ELSE (m.mx + d.k)::TEXT END
FROM dups d
JOIN maxes m ON m.audit_id = d.audit_id
WHERE f.id = d.id;

ALTER TABLE findings
    ADD CONSTRAINT uq_findings_audit_reference UNIQUE (audit_id, reference);

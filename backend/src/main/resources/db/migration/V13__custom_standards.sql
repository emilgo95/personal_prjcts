-- Listados propios por organización (RF-CAT-*): cada cooperativa importa los de su estándar.
-- organization_id NULL = estándar común (catálogo de demostración); con valor = privado de esa organización.
ALTER TABLE catalog.audit_standards ADD COLUMN organization_id BIGINT;
ALTER TABLE catalog.audit_standards ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE catalog.audit_standards ADD COLUMN imported_at TIMESTAMPTZ;
ALTER TABLE catalog.audit_standards ADD COLUMN imported_by VARCHAR(200);

-- El código era único en todo el sistema; ahora lo es dentro de su ámbito (común o de una organización).
ALTER TABLE catalog.audit_standards DROP CONSTRAINT IF EXISTS audit_standards_code_key;
CREATE UNIQUE INDEX uq_standard_code_scope ON catalog.audit_standards (COALESCE(organization_id, 0), code);
CREATE INDEX idx_standards_org ON catalog.audit_standards (organization_id);

-- Estándares comunes (demostración) que una organización decide ocultar en sus pantallas.
CREATE TABLE catalog.hidden_standard (
    organization_id BIGINT NOT NULL,
    standard_id     BIGINT NOT NULL REFERENCES catalog.audit_standards (id),
    PRIMARY KEY (organization_id, standard_id)
);

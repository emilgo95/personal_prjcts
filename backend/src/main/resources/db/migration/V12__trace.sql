-- Módulo trace (RF-TR-*): traza de cambios inalterable y encadenada con SHA-512 (+ HMAC-SHA-512).
CREATE SCHEMA IF NOT EXISTS trace;

-- Cabeza de la cadena de cada organización (se bloquea para serializar las altas de esa organización).
CREATE TABLE trace.chain_head (
    organization_id BIGINT      PRIMARY KEY,
    last_seq        BIGINT      NOT NULL,
    last_hash       CHAR(128)   NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

-- Registro de cambios: solo se añaden filas. `hash` encadena cada entrada con la anterior (prev_hash) y
-- `signature` es el HMAC-SHA-512 del hash con una clave que no está en la base de datos.
CREATE TABLE trace.change_log (
    id              BIGSERIAL    PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    seq             BIGINT       NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    user_id         BIGINT,
    username        VARCHAR(200) NOT NULL,
    user_full_name  VARCHAR(200),
    user_role       VARCHAR(20)  NOT NULL,
    entity_type     VARCHAR(40)  NOT NULL,
    entity_id       BIGINT,
    entity_ref      VARCHAR(120),
    parent_type     VARCHAR(40),
    parent_id       BIGINT,
    audit_id        BIGINT,
    action          VARCHAR(20)  NOT NULL,
    summary         TEXT         NOT NULL,
    changes         TEXT         NOT NULL DEFAULT '[]',
    prev_hash       CHAR(128)    NOT NULL,
    hash            CHAR(128)    NOT NULL,
    signature       CHAR(128)    NOT NULL,
    CONSTRAINT uq_change_log_seq UNIQUE (organization_id, seq)
);
CREATE INDEX idx_change_log_org_time ON trace.change_log (organization_id, occurred_at DESC);
CREATE INDEX idx_change_log_audit ON trace.change_log (organization_id, audit_id);
CREATE INDEX idx_change_log_entity ON trace.change_log (organization_id, entity_type, entity_id);
CREATE INDEX idx_change_log_parent ON trace.change_log (organization_id, parent_type, parent_id);

-- Sellos: foto firmada de la última huella de la cadena (anclaje para detectar reescrituras completas).
CREATE TABLE trace.seal (
    id              BIGSERIAL    PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    sealed_at       TIMESTAMPTZ  NOT NULL,
    last_seq        BIGINT       NOT NULL,
    last_hash       CHAR(128)    NOT NULL,
    signature       CHAR(128)    NOT NULL,
    CONSTRAINT uq_seal_seq UNIQUE (organization_id, last_seq)
);

-- Inalterable: ni modificar ni borrar ni vaciar.
CREATE FUNCTION trace.forbid_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'El registro de trazabilidad es inalterable (% sobre trace.%)', TG_OP, TG_TABLE_NAME
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_change_log_immutable BEFORE UPDATE OR DELETE ON trace.change_log
    FOR EACH ROW EXECUTE FUNCTION trace.forbid_change();
CREATE TRIGGER trg_change_log_no_truncate BEFORE TRUNCATE ON trace.change_log
    FOR EACH STATEMENT EXECUTE FUNCTION trace.forbid_change();
CREATE TRIGGER trg_seal_immutable BEFORE UPDATE OR DELETE ON trace.seal
    FOR EACH ROW EXECUTE FUNCTION trace.forbid_change();
CREATE TRIGGER trg_seal_no_truncate BEFORE TRUNCATE ON trace.seal
    FOR EACH STATEMENT EXECUTE FUNCTION trace.forbid_change();

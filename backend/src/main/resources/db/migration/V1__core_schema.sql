-- ===========================================================================
-- AgriAudit Docs - esquema base
-- ===========================================================================

CREATE TABLE organizations (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         VARCHAR(200) NOT NULL UNIQUE,
    country      VARCHAR(100),
    plan         VARCHAR(50)  NOT NULL DEFAULT 'STANDARD',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE app_users (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id  BIGINT NOT NULL REFERENCES organizations (id),
    email            VARCHAR(200) NOT NULL UNIQUE,
    password_hash    VARCHAR(200) NOT NULL,
    full_name        VARCHAR(200),
    role             VARCHAR(20)  NOT NULL DEFAULT 'AUDITOR',
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_app_users_org ON app_users (organization_id);

CREATE TABLE farms (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id      BIGINT NOT NULL REFERENCES organizations (id),
    legal_name           VARCHAR(250) NOT NULL,
    trade_name           VARCHAR(250),
    registration_number  VARCHAR(100),      -- GGN, GLN, u otro identificador
    address              VARCHAR(300),
    city                 VARCHAR(150),
    region               VARCHAR(150),
    country              VARCHAR(100),
    latitude             NUMERIC(9,6),
    longitude            NUMERIC(9,6),
    total_hectares       NUMERIC(12,2),
    crops                TEXT,              -- lista separada por comas
    production_areas     TEXT,
    contact_name         VARCHAR(200),
    contact_email        VARCHAR(200),
    contact_phone        VARCHAR(60),
    notes                TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_farms_org ON farms (organization_id);

CREATE TABLE audit_standards (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          VARCHAR(60)  NOT NULL UNIQUE,
    version       VARCHAR(30),
    name          VARCHAR(200) NOT NULL,
    owner_body    VARCHAR(200),
    category      VARCHAR(40)  NOT NULL DEFAULT 'GAP',
    description   TEXT,
    scoring_rule  TEXT,              -- JSON con reglas de puntuacion
    official_url  VARCHAR(400),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE standard_chapters (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    standard_id  BIGINT NOT NULL REFERENCES audit_standards (id),
    code         VARCHAR(30)  NOT NULL,
    title        VARCHAR(300) NOT NULL,
    description  TEXT,
    sort_order   INT NOT NULL DEFAULT 0,
    UNIQUE (standard_id, code)
);
CREATE INDEX idx_chapters_standard ON standard_chapters (standard_id);

CREATE TABLE control_points (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chapter_id         BIGINT NOT NULL REFERENCES standard_chapters (id),
    ref                VARCHAR(40)  NOT NULL,
    title              VARCHAR(400) NOT NULL,
    description        TEXT,
    level              VARCHAR(30)  NOT NULL DEFAULT 'MAJOR_MUST',
    guidance           TEXT,
    evidence_examples  TEXT,
    applicable_scopes  VARCHAR(200),
    sort_order         INT NOT NULL DEFAULT 0,
    UNIQUE (chapter_id, ref)
);
CREATE INDEX idx_controls_chapter ON control_points (chapter_id);

CREATE TABLE regulations (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code            VARCHAR(60)  NOT NULL UNIQUE,
    title           VARCHAR(400) NOT NULL,
    jurisdiction    VARCHAR(80),
    scope_area      VARCHAR(120),
    summary         TEXT,
    key_obligations TEXT,
    source_url      VARCHAR(400),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE control_point_regulations (
    control_point_id BIGINT NOT NULL REFERENCES control_points (id) ON DELETE CASCADE,
    regulation_id    BIGINT NOT NULL REFERENCES regulations (id) ON DELETE CASCADE,
    PRIMARY KEY (control_point_id, regulation_id)
);

CREATE TABLE audits (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id     BIGINT NOT NULL REFERENCES organizations (id),
    farm_id             BIGINT NOT NULL REFERENCES farms (id),
    standard_id         BIGINT NOT NULL REFERENCES audit_standards (id),
    reference           VARCHAR(60)  NOT NULL,
    audit_type          VARCHAR(30)  NOT NULL DEFAULT 'SELF_ASSESSMENT',
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    scope_description   TEXT,
    planned_start_date  DATE,
    planned_end_date    DATE,
    actual_date         DATE,
    lead_auditor        VARCHAR(200),
    audit_team          VARCHAR(400),
    site_address        VARCHAR(300),
    products_in_scope   TEXT,
    created_by_user_id  BIGINT REFERENCES app_users (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, reference)
);
CREATE INDEX idx_audits_org ON audits (organization_id);
CREATE INDEX idx_audits_farm ON audits (farm_id);

CREATE TABLE audit_item_results (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    audit_id          BIGINT NOT NULL REFERENCES audits (id) ON DELETE CASCADE,
    control_point_id  BIGINT NOT NULL REFERENCES control_points (id),
    compliance        VARCHAR(20) NOT NULL DEFAULT 'NOT_ASSESSED',
    evidence_notes    TEXT,
    auditor_comment   TEXT,
    assessed_at       TIMESTAMPTZ,
    assessed_by       VARCHAR(200),
    UNIQUE (audit_id, control_point_id)
);
CREATE INDEX idx_results_audit ON audit_item_results (audit_id);

CREATE TABLE findings (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    audit_id          BIGINT NOT NULL REFERENCES audits (id) ON DELETE CASCADE,
    control_point_id  BIGINT REFERENCES control_points (id),
    reference         VARCHAR(60),
    classification    VARCHAR(20) NOT NULL DEFAULT 'MINOR',
    title             VARCHAR(400),
    description       TEXT,
    area              VARCHAR(150),
    due_date          DATE,
    status            VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    raised_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_findings_audit ON findings (audit_id);

CREATE TABLE corrective_actions (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    finding_id          BIGINT NOT NULL REFERENCES findings (id) ON DELETE CASCADE,
    root_cause          TEXT,
    correction          TEXT,
    preventive_action   TEXT,
    responsible_person  VARCHAR(200),
    target_date         DATE,
    completed_date      DATE,
    verification_notes  TEXT,
    verified_by         VARCHAR(200),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_capa_finding ON corrective_actions (finding_id);

CREATE TABLE attachments (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id  BIGINT NOT NULL REFERENCES organizations (id),
    entity_type      VARCHAR(40) NOT NULL,
    entity_id        BIGINT NOT NULL,
    file_name        VARCHAR(300) NOT NULL,
    content_type     VARCHAR(120),
    size_bytes       BIGINT,
    storage_key      VARCHAR(400) NOT NULL,
    uploaded_by      VARCHAR(200),
    uploaded_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_attachments_entity ON attachments (entity_type, entity_id);

CREATE TABLE document_templates (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    standard_code  VARCHAR(60),
    document_type  VARCHAR(60) NOT NULL,
    format         VARCHAR(10) NOT NULL,
    engine         VARCHAR(30) NOT NULL,
    template_ref   VARCHAR(200),
    version        VARCHAR(20) NOT NULL DEFAULT '1',
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (standard_code, document_type, format)
);

CREATE TABLE generated_documents (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    audit_id         BIGINT NOT NULL REFERENCES audits (id) ON DELETE CASCADE,
    organization_id  BIGINT NOT NULL REFERENCES organizations (id),
    document_type    VARCHAR(60) NOT NULL,
    standard_code    VARCHAR(60),
    format           VARCHAR(10) NOT NULL,
    file_name        VARCHAR(300) NOT NULL,
    storage_key      VARCHAR(400) NOT NULL,
    size_bytes       BIGINT,
    sha256           VARCHAR(64),
    generated_by     VARCHAR(200),
    generated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    params           TEXT
);
CREATE INDEX idx_gendocs_audit ON generated_documents (audit_id);

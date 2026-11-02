-- Módulo ai · Patrón «propuesta» (RT-A02, RS-IA-01, RS-IA-07).
-- Toda salida de un asistente de IA se guarda aquí como PENDING y no escribe en tablas de dominio hasta
-- que una persona la acepta (tal cual o editada). La fila conserva la traza completa: modelo, versión de
-- prompt, referencias de entrada, tokens, coste, latencia, región de inferencia y quién la revisó.

CREATE TABLE ai_proposals (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id BIGINT       NOT NULL REFERENCES organizations (id),
    assistant       VARCHAR(40)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'ACCEPTED', 'EDITED', 'REJECTED')),
    input_refs      TEXT,                     -- referencias (p. ej. attachment:42), nunca el contenido
    prompt_ref      VARCHAR(120) NOT NULL,    -- p. ej. finding-draft.v2
    output          TEXT         NOT NULL,
    edited_output   TEXT,
    model_id        VARCHAR(80)  NOT NULL,
    input_tokens    BIGINT       NOT NULL,
    output_tokens   BIGINT       NOT NULL,
    cost_usd        NUMERIC(12, 6),
    latency_ms      BIGINT       NOT NULL,
    inference_geo   VARCHAR(20),
    reviewed_by     VARCHAR(200),
    reviewed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK ((status = 'PENDING') = (reviewed_at IS NULL)),
    CHECK (status <> 'EDITED' OR edited_output IS NOT NULL)
);

CREATE INDEX idx_ai_proposals_org_status ON ai_proposals (organization_id, status, created_at DESC);

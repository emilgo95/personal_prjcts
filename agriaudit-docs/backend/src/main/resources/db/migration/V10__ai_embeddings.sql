-- Módulo ai · RAG sobre PostgreSQL con pgvector (RT-A06, RS-IA-06).
-- Un solo almacén de fragmentos embebidos con dos corpus:
--   REGULATORY_CATALOG     catálogo normativo compartido (puntos de control, reglamentos): organization_id NULL
--   ORGANIZATION_EVIDENCE  evidencia de cada organización: organization_id obligatorio
-- El aislamiento multitenant se aplica en el WHERE de cada búsqueda (PgVectorSearchAdapter), nunca
-- filtrando resultados después.
-- Dimensión 1024: la de voyage-3 y bge-m3, los dos modelos de embeddings candidatos (IA-en-AgriAudit §5.3).

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE ai_embeddings (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    corpus          VARCHAR(40)  NOT NULL CHECK (corpus IN ('REGULATORY_CATALOG', 'ORGANIZATION_EVIDENCE')),
    organization_id BIGINT       REFERENCES organizations (id) ON DELETE CASCADE,
    source_type     VARCHAR(40)  NOT NULL,      -- p. ej. CONTROL_POINT, REGULATION, ATTACHMENT
    source_id       VARCHAR(80)  NOT NULL,
    chunk_index     INT          NOT NULL CHECK (chunk_index >= 0),
    content         TEXT         NOT NULL,
    embedding_model VARCHAR(80)  NOT NULL,
    embedding       vector(1024) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK ((corpus = 'REGULATORY_CATALOG') = (organization_id IS NULL)),
    UNIQUE NULLS NOT DISTINCT (corpus, organization_id, source_type, source_id, chunk_index, embedding_model)
);

CREATE INDEX idx_ai_embeddings_scope ON ai_embeddings (corpus, organization_id, embedding_model);
CREATE INDEX idx_ai_embeddings_hnsw ON ai_embeddings USING hnsw (embedding vector_cosine_ops);

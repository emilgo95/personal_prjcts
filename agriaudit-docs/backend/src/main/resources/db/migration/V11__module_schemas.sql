-- RT-02 / RT-30 · Tablas agrupadas por módulo.
--
-- Cada módulo del monolito tiene su propio esquema de PostgreSQL con el mismo nombre que su paquete
-- (com.agriaudit.<módulo>). La frontera entre módulos se ve así también en la base de datos: una
-- consulta que mezcle tablas de dos esquemas es un acceso a los datos de otro módulo y debe pasar
-- por su API. Las claves foráneas entre esquemas se mantienen: protegen la integridad, no son JOIN.
--
-- ALTER TABLE ... SET SCHEMA mueve también índices, restricciones y secuencias de identidad.
-- La extensión pgvector sigue en public (el tipo vector se resuelve por search_path).

CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS fieldbook;
CREATE SCHEMA IF NOT EXISTS storage;
CREATE SCHEMA IF NOT EXISTS audit;
CREATE SCHEMA IF NOT EXISTS findings;
CREATE SCHEMA IF NOT EXISTS documents;
CREATE SCHEMA IF NOT EXISTS ai;

-- identity
ALTER TABLE public.organizations SET SCHEMA identity;
ALTER TABLE public.app_users SET SCHEMA identity;

-- catalog
ALTER TABLE public.audit_standards SET SCHEMA catalog;
ALTER TABLE public.standard_chapters SET SCHEMA catalog;
ALTER TABLE public.control_points SET SCHEMA catalog;
ALTER TABLE public.regulations SET SCHEMA catalog;
ALTER TABLE public.control_point_regulations SET SCHEMA catalog;

-- fieldbook
ALTER TABLE public.farms SET SCHEMA fieldbook;

-- storage
ALTER TABLE public.attachments SET SCHEMA storage;

-- audit
ALTER TABLE public.audits SET SCHEMA audit;
ALTER TABLE public.audit_item_results SET SCHEMA audit;

-- findings
ALTER TABLE public.findings SET SCHEMA findings;
ALTER TABLE public.corrective_actions SET SCHEMA findings;

-- documents
ALTER TABLE public.document_templates SET SCHEMA documents;
ALTER TABLE public.generated_documents SET SCHEMA documents;

-- ai
ALTER TABLE public.ai_proposals SET SCHEMA ai;
ALTER TABLE public.ai_embeddings SET SCHEMA ai;

-- reference_sequences la compartían audit y findings (ámbitos AUDIT:… y FINDING:…): una por módulo.
CREATE TABLE audit.reference_sequences (
    scope       VARCHAR(80) PRIMARY KEY,   -- 'AUDIT:org:<id>'
    last_value  BIGINT      NOT NULL
);
CREATE TABLE findings.reference_sequences (
    scope       VARCHAR(80) PRIMARY KEY,   -- 'FINDING:audit:<id>'
    last_value  BIGINT      NOT NULL
);
INSERT INTO audit.reference_sequences (scope, last_value)
SELECT scope, last_value FROM public.reference_sequences WHERE scope LIKE 'AUDIT:%';
INSERT INTO findings.reference_sequences (scope, last_value)
SELECT scope, last_value FROM public.reference_sequences WHERE scope LIKE 'FINDING:%';
DROP TABLE public.reference_sequences;

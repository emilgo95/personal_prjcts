-- Módulo evidence (RF-401…RF-406): registros de evidencia reutilizables entre normas.
CREATE SCHEMA IF NOT EXISTS evidence;

-- Tipos de evidencia (catálogo común, de solo lectura para las organizaciones).
CREATE TABLE evidence.evidence_types (
    code                   VARCHAR(40)  PRIMARY KEY,
    name                   VARCHAR(160) NOT NULL,
    description            VARCHAR(400),
    personal_data_notice   VARCHAR(300),
    sort_order             INT          NOT NULL
);

-- Matriz de equivalencias: qué tipo de evidencia sirve para qué punto de qué norma. Las filas con
-- organization_id NULL son el borrador de partida; cada organización recibe su copia editable al usarla.
CREATE TABLE evidence.type_control_points (
    id              BIGSERIAL    PRIMARY KEY,
    organization_id BIGINT,
    type_code       VARCHAR(40)  NOT NULL REFERENCES evidence.evidence_types (code),
    standard_code   VARCHAR(60)  NOT NULL,
    control_ref     VARCHAR(40)  NOT NULL,
    status          VARCHAR(12)  NOT NULL DEFAULT 'DRAFT',
    CONSTRAINT chk_matrix_status CHECK (status IN ('DRAFT', 'VALIDATED'))
);
CREATE UNIQUE INDEX uq_matrix_org ON evidence.type_control_points (organization_id, type_code, standard_code, control_ref)
    WHERE organization_id IS NOT NULL;
CREATE UNIQUE INDEX uq_matrix_base ON evidence.type_control_points (type_code, standard_code, control_ref)
    WHERE organization_id IS NULL;

-- Registro de evidencia de una explotación: se da de alta una vez y sirve para varias auditorías y normas.
CREATE TABLE evidence.evidence_records (
    id                      BIGSERIAL    PRIMARY KEY,
    organization_id         BIGINT       NOT NULL,
    farm_id                 BIGINT       NOT NULL,
    type_code               VARCHAR(40)  NOT NULL REFERENCES evidence.evidence_types (code),
    title                   VARCHAR(200) NOT NULL,
    issued_on               DATE         NOT NULL,
    valid_until             DATE,
    outcome                 VARCHAR(12)  NOT NULL DEFAULT 'UNRATED',
    notes                   TEXT,
    contains_personal_data  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by_user_id      BIGINT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_record_outcome CHECK (outcome IN ('FAVORABLE', 'UNFAVORABLE', 'UNRATED')),
    CONSTRAINT chk_record_dates CHECK (valid_until IS NULL OR valid_until >= issued_on)
);
CREATE INDEX idx_evidence_records_farm ON evidence.evidence_records (organization_id, farm_id, type_code);

-- Vinculación confirmada de un registro con un punto de control de una auditoría.
CREATE TABLE evidence.audit_links (
    id                 BIGSERIAL   PRIMARY KEY,
    organization_id    BIGINT      NOT NULL,
    audit_id           BIGINT      NOT NULL,
    control_point_id   BIGINT      NOT NULL,
    record_id          BIGINT      NOT NULL REFERENCES evidence.evidence_records (id),
    linked_by_user_id  BIGINT,
    linked_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_audit_link UNIQUE (audit_id, control_point_id, record_id)
);
CREATE INDEX idx_audit_links_audit ON evidence.audit_links (audit_id);
CREATE INDEX idx_audit_links_record ON evidence.audit_links (record_id);

-- Tipos de evidencia
INSERT INTO evidence.evidence_types (code, name, description, personal_data_notice, sort_order) VALUES
    ('WATER_ANALYSIS', 'Análisis de agua', 'Resultados analíticos del agua de riego o de lavado (boletín del laboratorio).', NULL, 1),
    ('SOIL_ANALYSIS', 'Análisis de suelo o foliar', 'Análisis de suelo, sustrato u hoja con su boletín.', NULL, 2),
    ('RESIDUE_ANALYSIS', 'Análisis de residuos de plaguicidas', 'Boletines de análisis de residuos del producto.', NULL, 3),
    ('RISK_ASSESSMENT', 'Evaluación de riesgos', 'Evaluación de riesgos laborales, del sitio, de higiene o del agua, con fecha y firma.', NULL, 4),
    ('EQUIPMENT_INSPECTION', 'Calibración, inspección o mantenimiento de equipos', 'Certificados de inspección o calibración y registros de mantenimiento de maquinaria y equipos.', NULL, 5),
    ('TRAINING', 'Formación (plan, temario y certificados)', 'Plan de formación, temario y certificados de formación.', 'Si el documento incluye nombres u otros datos de trabajadores, márcalo como «contiene datos personales» y súbelo solo si es necesario.', 6),
    ('FIRST_AID', 'Primeros auxilios y botiquines', 'Certificados de primeros auxilios y contenido y ubicación de botiquines.', NULL, 7),
    ('CHEMICALS_INVENTORY', 'Inventario y fichas de seguridad de productos químicos', 'Inventario de productos químicos y fichas de datos de seguridad.', NULL, 8),
    ('PPE', 'Equipos de protección individual', 'Inventario, entrega e inspección de EPI.', 'Si el registro de entrega incluye nombres de trabajadores, márcalo como «contiene datos personales».', 9),
    ('WASTE_MANAGEMENT', 'Gestión de residuos', 'Plan de gestión de residuos y contratos o justificantes de gestor autorizado.', NULL, 10),
    ('RESOURCE_CONSUMPTION', 'Consumos de energía y agua', 'Facturas y registros de consumo de energía y agua.', NULL, 11),
    ('FACILITIES_INSPECTION', 'Inspección de instalaciones', 'Inspección y fotos de las instalaciones de bienestar, aseos y comedores.', NULL, 12),
    ('NON_DISCRIMINATION', 'Política de no discriminación e igualdad', 'Política escrita de no discriminación e igualdad.', NULL, 13);

-- Matriz de equivalencias de partida. BORRADOR deducido de las «evidencias de ejemplo» de cada punto de control:
-- debe revisarla el responsable de calidad (cada organización la valida o corrige en su copia).
INSERT INTO evidence.type_control_points (organization_id, type_code, standard_code, control_ref, status) VALUES
    (NULL, 'WATER_ANALYSIS', 'GLOBALGAP_IFA_V6', 'FV 9.3', 'DRAFT'),
    (NULL, 'WATER_ANALYSIS', 'GLOBALGAP_IFA_V6', 'CB 5.1', 'DRAFT'),
    (NULL, 'WATER_ANALYSIS', 'SMETA_6_1', '3.D', 'DRAFT'),
    (NULL, 'SOIL_ANALYSIS', 'GLOBALGAP_IFA_V6', 'CB 3.2', 'DRAFT'),
    (NULL, 'SOIL_ANALYSIS', 'GLOBALGAP_IFA_V6', 'CB 4.1', 'DRAFT'),
    (NULL, 'SOIL_ANALYSIS', 'SMETA_6_1', '3.D', 'DRAFT'),
    (NULL, 'RESIDUE_ANALYSIS', 'GLOBALGAP_IFA_V6', 'FV 9.5', 'DRAFT'),
    (NULL, 'RESIDUE_ANALYSIS', 'GLOBALGAP_IFA_V6', 'CB 7.4', 'DRAFT'),
    (NULL, 'RISK_ASSESSMENT', 'GLOBALGAP_IFA_V6', 'AF 1.2', 'DRAFT'),
    (NULL, 'RISK_ASSESSMENT', 'GLOBALGAP_IFA_V6', 'AF 11.1', 'DRAFT'),
    (NULL, 'RISK_ASSESSMENT', 'GLOBALGAP_IFA_V6', 'FV 8.1', 'DRAFT'),
    (NULL, 'RISK_ASSESSMENT', 'GLOBALGAP_IFA_V6', 'CB 5.1', 'DRAFT'),
    (NULL, 'RISK_ASSESSMENT', 'SMETA_6_1', '2.A', 'DRAFT'),
    (NULL, 'EQUIPMENT_INSPECTION', 'GLOBALGAP_IFA_V6', 'CB 7.5', 'DRAFT'),
    (NULL, 'EQUIPMENT_INSPECTION', 'SMETA_6_1', '2.D', 'DRAFT'),
    (NULL, 'TRAINING', 'GLOBALGAP_IFA_V6', 'AF 11.3', 'DRAFT'),
    (NULL, 'TRAINING', 'GLOBALGAP_IFA_V6', 'FV 8.1', 'DRAFT'),
    (NULL, 'TRAINING', 'GLOBALGAP_IFA_V6', 'FV 9.2', 'DRAFT'),
    (NULL, 'TRAINING', 'SMETA_6_1', '4.A', 'DRAFT'),
    (NULL, 'TRAINING', 'SMETA_6_1', '2.B', 'DRAFT'),
    (NULL, 'FIRST_AID', 'GLOBALGAP_IFA_V6', 'AF 11.6', 'DRAFT'),
    (NULL, 'FIRST_AID', 'SMETA_6_1', '2.F', 'DRAFT'),
    (NULL, 'CHEMICALS_INVENTORY', 'GLOBALGAP_IFA_V6', 'AF 11.7', 'DRAFT'),
    (NULL, 'CHEMICALS_INVENTORY', 'SMETA_6_1', '2.E', 'DRAFT'),
    (NULL, 'PPE', 'GLOBALGAP_IFA_V6', 'AF 11.4', 'DRAFT'),
    (NULL, 'PPE', 'SMETA_6_1', '2.E', 'DRAFT'),
    (NULL, 'WASTE_MANAGEMENT', 'GLOBALGAP_IFA_V6', 'AF 10.1', 'DRAFT'),
    (NULL, 'WASTE_MANAGEMENT', 'SMETA_6_1', '3.B', 'DRAFT'),
    (NULL, 'RESOURCE_CONSUMPTION', 'GLOBALGAP_IFA_V6', 'AF 10.2', 'DRAFT'),
    (NULL, 'RESOURCE_CONSUMPTION', 'GLOBALGAP_IFA_V6', 'CB 5.2', 'DRAFT'),
    (NULL, 'RESOURCE_CONSUMPTION', 'SMETA_6_1', '3.C', 'DRAFT'),
    (NULL, 'FACILITIES_INSPECTION', 'GLOBALGAP_IFA_V6', 'AF 11.5', 'DRAFT'),
    (NULL, 'FACILITIES_INSPECTION', 'SMETA_6_1', '2.G', 'DRAFT'),
    (NULL, 'NON_DISCRIMINATION', 'GRASP_V2', 'G 8.1', 'DRAFT'),
    (NULL, 'NON_DISCRIMINATION', 'SMETA_6_1', '1.F', 'DRAFT');

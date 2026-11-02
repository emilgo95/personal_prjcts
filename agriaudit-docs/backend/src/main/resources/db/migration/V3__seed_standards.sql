-- ===========================================================================
-- Estándares de auditoría, capítulos y plantillas de documento
-- ===========================================================================

INSERT INTO audit_standards (code, version, name, owner_body, category, description, scoring_rule, official_url) VALUES
('GLOBALGAP_IFA_V6', 'v6 Smart', 'GLOBALG.A.P. Integrated Farm Assurance (IFA)', 'GLOBALG.A.P. c/o FoodPLUS GmbH', 'GAP',
 'Estándar de buenas prácticas agrícolas reconocido por GFSI. La versión 6 organiza los requisitos en principios y criterios sobre legalidad, gestión y trazabilidad, proceso de producción, seguridad alimentaria, sostenibilidad ambiental y bienestar de trabajadores y animales.',
 '{"type":"MAJOR_MINOR","majorMustMinPct":100,"minorMustMinPct":95,"levels":["MAJOR_MUST","MINOR_MUST","RECOMMENDATION"]}',
 'https://www.globalgap.org/what-we-offer/solutions/ifa-fruit-and-vegetables/'),
('GRASP_V2', 'v2', 'GLOBALG.A.P. Risk Assessment on Social Practice (GRASP)', 'GLOBALG.A.P. c/o FoodPLUS GmbH', 'SOCIAL',
 'Módulo complementario de GLOBALG.A.P. que evalúa las prácticas sociales en la explotación: representación de los trabajadores, quejas, trabajo infantil, contratos, salarios y jornada. El resultado es un informe de evaluación de riesgo, sin certificado independiente.',
 '{"type":"LEVEL_COMPLETION","reportOnly":true,"levels":["MAJOR_MUST","RECOMMENDATION"]}',
 'https://www.globalgap.org/what-we-offer/solutions/grasp/'),
('SMETA_6_1', '6.1 (4 pilares)', 'Sedex Members Ethical Trade Audit (SMETA)', 'Sedex', 'SOCIAL',
 'Metodología de auditoría ética de Sedex que cubre cuatro pilares: normas laborales, salud y seguridad, medio ambiente y ética empresarial, tomando como referencia el ETI Base Code y la legislación local. Incluye un cuestionario de autoevaluación (SAQ) previo y un informe de plan de acciones correctivas (CAPR).',
 '{"type":"PILLAR","reportOnly":true,"pillars":["LABOUR","HEALTH_SAFETY","ENVIRONMENT","BUSINESS_ETHICS"]}',
 'https://www.sedex.com/our-services/smeta-audit/');

-- ---------------------------------------------------------------------------
-- Capítulos GLOBALG.A.P. IFA v6
-- ---------------------------------------------------------------------------
INSERT INTO standard_chapters (standard_id, code, title, description, sort_order)
SELECT s.id, c.code, c.title, c.description, c.sort_order
FROM audit_standards s
JOIN (VALUES
    ('LEGAL', 'Legalidad y gestión del sitio', 'Titularidad legal del terreno, evaluación de riesgos del sitio y cumplimiento de la normativa aplicable.', 1),
    ('MGMT',  'Sistema de gestión y trazabilidad', 'Procedimientos documentados, registros, trazabilidad, balance de masas y gestión de reclamaciones y retiradas.', 2),
    ('PROD',  'Proceso de producción', 'Material vegetal, gestión del suelo y sustratos, fertilización, riego y protección integrada de cultivos.', 3),
    ('HARV',  'Cosecha y manipulación del producto', 'Higiene en cosecha, transporte y actividades de manipulación posteriores a la cosecha.', 4),
    ('FOOD',  'Seguridad alimentaria e higiene (APPCC)', 'Análisis de peligros, higiene del personal e instalaciones, agua de contacto con el producto y análisis de residuos.', 5),
    ('ENV',   'Sostenibilidad ambiental y biodiversidad', 'Gestión de residuos, eficiencia energética e hídrica, conservación de la biodiversidad y calidad del aire.', 6),
    ('WORK',  'Salud, seguridad y bienestar de los trabajadores', 'Evaluación de riesgos laborales, formación, equipos de protección, instalaciones de bienestar y procedimientos de emergencia.', 7)
) AS c(code, title, description, sort_order) ON TRUE
WHERE s.code = 'GLOBALGAP_IFA_V6';

-- ---------------------------------------------------------------------------
-- Capítulos GRASP v2
-- ---------------------------------------------------------------------------
INSERT INTO standard_chapters (standard_id, code, title, description, sort_order)
SELECT s.id, c.code, c.title, c.description, c.sort_order
FROM audit_standards s
JOIN (VALUES
    ('REP',      'Representación de los trabajadores', 'Existencia y funcionamiento de un representante o comité de los trabajadores elegido.', 1),
    ('COMP',     'Procedimiento de quejas', 'Canal de reclamaciones accesible, confidencial y con seguimiento documentado.', 2),
    ('SELFDECL', 'Autodeclaración de buenas prácticas sociales', 'Declaración firmada por la dirección sobre el compromiso con las buenas prácticas sociales.', 3),
    ('CHILD',    'Trabajo infantil y protección de menores', 'Cumplimiento de la edad mínima y protección de los trabajadores jóvenes y de los hijos de los trabajadores.', 4),
    ('CONTRACT', 'Contratos y documentación laboral', 'Contratos de trabajo, registro de horas y de pagos, y acceso de los trabajadores a su documentación.', 5),
    ('WAGES',    'Salarios y prestaciones', 'Pago al menos del salario mínimo legal o de convenio, en plazo y con nómina comprensible.', 6),
    ('HOURS',    'Jornada laboral', 'Control de las horas de trabajo y de las horas extraordinarias conforme a la legislación.', 7),
    ('NONDISC',  'No discriminación y libertad de asociación', 'Ausencia de discriminación y respeto de la libertad de asociación de los trabajadores.', 8)
) AS c(code, title, description, sort_order) ON TRUE
WHERE s.code = 'GRASP_V2';

-- ---------------------------------------------------------------------------
-- Capítulos SMETA 6.1
-- ---------------------------------------------------------------------------
INSERT INTO standard_chapters (standard_id, code, title, description, sort_order)
SELECT s.id, c.code, c.title, c.description, c.sort_order
FROM audit_standards s
JOIN (VALUES
    ('MGMT',   '0. Gestión del sistema y derecho a trabajar', 'Sistemas de gestión, subcontratación, trabajo domiciliario y verificación del derecho a trabajar.', 1),
    ('LABOUR', '1. Normas laborales', 'Empleo libremente elegido, libertad de asociación, trabajo infantil, salarios, jornada, discriminación, empleo regular y trato humano.', 2),
    ('HS',     '2. Salud y seguridad', 'Evaluación de riesgos, seguridad de máquinas, sustancias químicas, prevención de incendios, primeros auxilios y alojamiento de los trabajadores.', 3),
    ('ENV',    '3. Medio ambiente (4 pilares)', 'Permisos ambientales, gestión de residuos y vertidos, consumo de recursos y cumplimiento legal ambiental.', 4),
    ('ETHICS', '4. Ética empresarial', 'Políticas anticorrupción y antisoborno, conflictos de interés y conducta ética en la cadena de suministro.', 5)
) AS c(code, title, description, sort_order) ON TRUE
WHERE s.code = 'SMETA_6_1';

-- ---------------------------------------------------------------------------
-- Plantillas de documento disponibles
--   engine: PDF_HTML (Thymeleaf + openhtmltopdf) | DOCX_POI | XLSX_POI
-- ---------------------------------------------------------------------------
INSERT INTO document_templates (standard_code, document_type, format, engine, template_ref, version) VALUES
(NULL,               'AUDIT_PLAN',            'PDF',  'PDF_HTML',  'docs/audit-plan',       '1'),
(NULL,               'AUDIT_PLAN',            'DOCX', 'DOCX_POI',  'audit-plan',            '1'),
(NULL,               'CHECKLIST',             'XLSX', 'XLSX_POI',  'checklist',             '1'),
(NULL,               'CHECKLIST',             'PDF',  'PDF_HTML',  'docs/checklist',        '1'),
(NULL,               'NONCONFORMITY_REPORT',  'DOCX', 'DOCX_POI',  'nonconformity-report',  '1'),
(NULL,               'NONCONFORMITY_REPORT',  'PDF',  'PDF_HTML',  'docs/nonconformity',    '1'),
(NULL,               'CAPA_REPORT',           'DOCX', 'DOCX_POI',  'capa-report',           '1'),
(NULL,               'CAPA_REPORT',           'XLSX', 'XLSX_POI',  'capa-report',           '1'),
(NULL,               'AUDIT_REPORT',          'PDF',  'PDF_HTML',  'docs/audit-report',     '1'),
(NULL,               'AUDIT_REPORT',          'DOCX', 'DOCX_POI',  'audit-report',          '1'),
(NULL,               'COMPLIANCE_SUMMARY',    'PDF',  'PDF_HTML',  'docs/compliance',       '1'),
('GRASP_V2',         'GRASP_ASSESSMENT',      'PDF',  'PDF_HTML',  'docs/grasp-assessment', '1'),
('GRASP_V2',         'GRASP_ASSESSMENT',     'XLSX', 'XLSX_POI',  'grasp-assessment',      '1'),
('SMETA_6_1',        'SMETA_REPORT',          'PDF',  'PDF_HTML',  'docs/smeta-report',     '1'),
('SMETA_6_1',        'SMETA_REPORT',          'DOCX', 'DOCX_POI',  'smeta-report',          '1');

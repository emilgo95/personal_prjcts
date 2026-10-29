-- RF-301 / RF-308 · Reloj de cierre de no conformidades.
--
-- Plazo de cierre por estándar y gravedad, en días naturales desde la fecha de la auditoría. Por defecto 28 días
-- (regla de GLOBALG.A.P.) para críticas, mayores y menores; las observaciones no tienen plazo. Cada valor se puede
-- cambiar por estándar; NULL = sin plazo automático para esa gravedad.
ALTER TABLE catalog.audit_standards
    ADD COLUMN closure_days_critical    INTEGER DEFAULT 28,
    ADD COLUMN closure_days_major       INTEGER DEFAULT 28,
    ADD COLUMN closure_days_minor       INTEGER DEFAULT 28,
    ADD COLUMN closure_days_observation INTEGER,
    ADD CONSTRAINT ck_closure_days CHECK (
        (closure_days_critical IS NULL OR closure_days_critical BETWEEN 1 AND 365)
        AND (closure_days_major IS NULL OR closure_days_major BETWEEN 1 AND 365)
        AND (closure_days_minor IS NULL OR closure_days_minor BETWEEN 1 AND 365)
        AND (closure_days_observation IS NULL OR closure_days_observation BETWEEN 1 AND 365));

-- Momento en que la NC quedó cerrada o verificada: permite saber si se cerró en plazo (indicador del piloto:
-- % de NC cerradas dentro de su plazo). Para las ya cerradas, la mejor aproximación es la fecha de su última acción
-- correctiva completada y, si no tiene, su última modificación.
ALTER TABLE findings.findings ADD COLUMN closed_at TIMESTAMPTZ;
UPDATE findings.findings f
SET closed_at = COALESCE(
        (SELECT (max(ca.completed_date) + TIME '12:00') AT TIME ZONE 'Europe/Madrid'
         FROM findings.corrective_actions ca WHERE ca.finding_id = f.id),
        f.updated_at)
WHERE f.status IN ('CLOSED', 'VERIFIED');

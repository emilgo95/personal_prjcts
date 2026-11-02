-- Envío de una auditoría con no conformidades vencidas: cada una necesita una justificación del retraso,
-- que sale en los documentos. Quién la escribió queda en la traza (no se guarda aquí ningún nombre).
ALTER TABLE findings.findings
    ADD COLUMN late_justification    VARCHAR(2000),
    ADD COLUMN late_justified_at     TIMESTAMPTZ;

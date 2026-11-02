-- Marca «contiene datos personales» de un adjunto de evidencia (personas identificables en fotos, nombres en escaneados...).
ALTER TABLE storage.attachments ADD COLUMN contains_personal_data BOOLEAN NOT NULL DEFAULT FALSE;

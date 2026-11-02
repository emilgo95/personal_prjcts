-- Cambio de contraseña (fase 1 del alta de usuarios): contraseña temporal que hay que cambiar al entrar y fecha
-- del último cambio, para dejar sin validez los tokens emitidos antes (cierra las demás sesiones).
ALTER TABLE identity.app_users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN password_changed_at  TIMESTAMPTZ;

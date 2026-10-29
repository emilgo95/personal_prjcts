-- Privacidad (RS-GDPR): bloqueo de datos identificativos, plazo de conservación y traza sin nombres.

-- trace: las entradas nuevas (formato 2) guardan solo el código de usuario, no su nombre ni su correo; el nombre se
-- busca al mostrarlas, de modo que bloquear a una persona oculta su identidad sin tocar el registro inalterable.
-- Las anteriores (formato 1) conservan sus columnas y se siguen verificando con su fórmula original.
ALTER TABLE trace.change_log ADD COLUMN format_version SMALLINT NOT NULL DEFAULT 1;
ALTER TABLE trace.change_log ALTER COLUMN username DROP NOT NULL;

-- identity: usuario bloqueado, plazo de conservación por organización y datos identificativos reservados.
ALTER TABLE identity.app_users ADD COLUMN blocked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE identity.organizations ADD COLUMN blocked_retention_months INT;

-- Datos identificativos reservados de un usuario bloqueado (art. 32 LOPDGDD): no se muestran ni se tratan y se
-- destruyen al vencer destroy_after.
CREATE TABLE identity.blocked_identity (
    user_id         BIGINT       PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    email           VARCHAR(200) NOT NULL,
    full_name       VARCHAR(200),
    blocked_at      TIMESTAMPTZ  NOT NULL,
    blocked_by      BIGINT,
    destroy_after   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_blocked_identity_destroy ON identity.blocked_identity (destroy_after);

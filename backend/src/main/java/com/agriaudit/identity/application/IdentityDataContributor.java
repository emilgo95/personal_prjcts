package com.agriaudit.identity.application;

import com.agriaudit.privacy.ExportSink;
import com.agriaudit.privacy.OrganizationDataContributor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Aporta a la exportación de la organización los datos de organización y usuarios. */
@Component
class IdentityDataContributor implements OrganizationDataContributor {

    private final JdbcTemplate jdbc;

    IdentityDataContributor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String section() {
        return "organización y usuarios";
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(Long organizationId, ExportSink sink) {
        sink.json("datos/organizacion.json", jdbc.queryForMap(
                "SELECT id, name, country, plan, blocked_retention_months, created_at FROM identity.organizations "
                        + "WHERE id = ?", organizationId));
        // Sin contraseñas. Los datos identificativos de usuarios bloqueados no se exportan (están reservados).
        sink.json("datos/usuarios.json", jdbc.queryForList(
                "SELECT id, email, full_name, role, active, blocked, created_at, updated_at FROM identity.app_users "
                        + "WHERE organization_id = ? ORDER BY id", organizationId));
    }
}

package com.agriaudit.identity.application;

import com.agriaudit.privacy.ExportSink;
import com.agriaudit.privacy.PersonDataContributor;
import com.agriaudit.privacy.PersonIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** La cuenta de la persona (sin la contraseña). */
@Component
class IdentityPersonDataContributor implements PersonDataContributor {

    private final JdbcTemplate jdbc;

    IdentityPersonDataContributor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String section() {
        return "cuenta de usuario";
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(Long organizationId, PersonIdentity person, ExportSink sink) {
        sink.json("datos/cuenta-de-usuario.json", jdbc.queryForMap(
                "SELECT id, email, full_name, role, active, created_at, updated_at FROM identity.app_users "
                        + "WHERE id = ? AND organization_id = ?", person.userId(), organizationId));
    }
}

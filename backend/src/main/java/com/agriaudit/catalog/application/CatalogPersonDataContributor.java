package com.agriaudit.catalog.application;

import com.agriaudit.privacy.ExportSink;
import com.agriaudit.privacy.PersonDataContributor;
import com.agriaudit.privacy.PersonIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Estándares propios que importó la persona. */
@Component
class CatalogPersonDataContributor implements PersonDataContributor {

    private final JdbcTemplate jdbc;

    CatalogPersonDataContributor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String section() {
        return "estándares importados";
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(Long organizationId, PersonIdentity person, ExportSink sink) {
        Object[] who = person.textKeys();
        sink.json("datos/estandares-importados.json", jdbc.queryForList(
                "SELECT id, code, version, name, imported_at FROM catalog.audit_standards "
                        + "WHERE organization_id = ? AND imported_by IN (?, ?) ORDER BY id",
                organizationId, who[0], who[1]));
    }
}

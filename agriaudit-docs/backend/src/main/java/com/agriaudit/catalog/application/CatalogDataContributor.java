package com.agriaudit.catalog.application;

import com.agriaudit.privacy.ExportSink;
import com.agriaudit.privacy.OrganizationDataContributor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Aporta a la exportación de la organización los datos de estándares propios. */
@Component
class CatalogDataContributor implements OrganizationDataContributor {

    private final JdbcTemplate jdbc;

    CatalogDataContributor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String section() {
        return "estándares propios";
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(Long organizationId, ExportSink sink) {
        sink.json("datos/estandares-propios.json", jdbc.queryForList(
                "SELECT * FROM catalog.audit_standards WHERE organization_id = ? ORDER BY id", organizationId));
        sink.json("datos/estandares-propios-capitulos.json", jdbc.queryForList(
                "SELECT * FROM catalog.standard_chapters WHERE standard_id IN "
                        + "(SELECT id FROM catalog.audit_standards WHERE organization_id = ?) ORDER BY standard_id, id",
                organizationId));
        sink.json("datos/estandares-propios-puntos.json", jdbc.queryForList(
                "SELECT cp.* FROM catalog.control_points cp WHERE cp.chapter_id IN (SELECT c.id FROM "
                        + "catalog.standard_chapters c WHERE c.standard_id IN (SELECT id FROM catalog.audit_standards "
                        + "WHERE organization_id = ?)) ORDER BY cp.chapter_id, cp.id", organizationId));
        sink.json("datos/estandares-ocultos.json", jdbc.queryForList(
                "SELECT standard_id FROM catalog.hidden_standard WHERE organization_id = ?", organizationId));
    }
}

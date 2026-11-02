package com.agriaudit.fieldbook.application;

import com.agriaudit.privacy.ExportSink;
import com.agriaudit.privacy.OrganizationDataContributor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Aporta a la exportación de la organización los datos de explotaciones. */
@Component
class FieldbookDataContributor implements OrganizationDataContributor {

    private final JdbcTemplate jdbc;

    FieldbookDataContributor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String section() {
        return "explotaciones";
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(Long organizationId, ExportSink sink) {
        sink.json("datos/explotaciones.json", jdbc.queryForList(
                "SELECT * FROM fieldbook.farms WHERE organization_id = ? ORDER BY id", organizationId));
    }
}

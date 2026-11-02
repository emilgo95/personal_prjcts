package com.agriaudit.ai.application;

import com.agriaudit.privacy.ExportSink;
import com.agriaudit.privacy.OrganizationDataContributor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Aporta a la exportación de la organización los datos de propuestas de IA. */
@Component
class AiDataContributor implements OrganizationDataContributor {

    private final JdbcTemplate jdbc;

    AiDataContributor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String section() {
        return "propuestas de IA";
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(Long organizationId, ExportSink sink) {
        sink.json("datos/propuestas-ia.json", jdbc.queryForList(
                "SELECT * FROM ai.ai_proposals WHERE organization_id = ? ORDER BY id", organizationId));
    }
}

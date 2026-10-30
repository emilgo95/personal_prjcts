package com.agriaudit.evidence.application;

import com.agriaudit.privacy.ExportSink;
import com.agriaudit.privacy.OrganizationDataContributor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Aporta a la exportación de la organización los registros de evidencia, sus vínculos y su matriz. */
@Component
class EvidenceDataContributor implements OrganizationDataContributor {

    private final JdbcTemplate jdbc;

    EvidenceDataContributor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String section() {
        return "evidencias reutilizables";
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(Long organizationId, ExportSink sink) {
        sink.json("datos/evidencias-registros.json", jdbc.queryForList(
                "SELECT * FROM evidence.evidence_records WHERE organization_id = ? ORDER BY id", organizationId));
        sink.json("datos/evidencias-vinculos.json", jdbc.queryForList(
                "SELECT * FROM evidence.audit_links WHERE organization_id = ? ORDER BY id", organizationId));
        sink.json("datos/evidencias-matriz.json", jdbc.queryForList(
                "SELECT id, type_code, standard_code, control_ref, status FROM evidence.type_control_points "
                        + "WHERE organization_id = ? ORDER BY id", organizationId));
    }
}

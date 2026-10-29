package com.agriaudit.audit.application;

import com.agriaudit.privacy.ExportSink;
import com.agriaudit.privacy.OrganizationDataContributor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Aporta a la exportación de la organización los datos de auditorías y checklist. */
@Component
class AuditDataContributor implements OrganizationDataContributor {

    private final JdbcTemplate jdbc;

    AuditDataContributor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String section() {
        return "auditorías y checklist";
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(Long organizationId, ExportSink sink) {
        sink.json("datos/auditorias.json", jdbc.queryForList(
                "SELECT * FROM audit.audits WHERE organization_id = ? ORDER BY id", organizationId));
        sink.json("datos/resultados-checklist.json", jdbc.queryForList(
                "SELECT * FROM audit.audit_item_results WHERE audit_id IN "
                        + "(SELECT id FROM audit.audits WHERE organization_id = ?) ORDER BY audit_id, id", organizationId));
    }
}

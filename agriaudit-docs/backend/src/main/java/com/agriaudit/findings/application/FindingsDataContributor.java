package com.agriaudit.findings.application;

import com.agriaudit.privacy.ExportSink;
import com.agriaudit.privacy.OrganizationDataContributor;
import com.agriaudit.audit.AuditLookup;
import com.agriaudit.audit.AuditRef;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Aporta a la exportación de la organización los datos de no conformidades y acciones correctivas. */
@Component
class FindingsDataContributor implements OrganizationDataContributor {

    private final JdbcTemplate jdbc;
    private final AuditLookup audits;

    FindingsDataContributor(JdbcTemplate jdbc, AuditLookup audits) {
        this.jdbc = jdbc;
        this.audits = audits;
    }

    @Override
    public String section() {
        return "no conformidades y acciones correctivas";
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(Long organizationId, ExportSink sink) {
        List<Long> auditIds = audits.findByOrganization(organizationId).stream().map(AuditRef::id).toList();
        if (auditIds.isEmpty()) {
            sink.json("datos/no-conformidades.json", List.of());
            sink.json("datos/acciones-correctivas.json", List.of());
            return;
        }
        String in = String.join(",", java.util.Collections.nCopies(auditIds.size(), "?"));
        Object[] args = auditIds.toArray();
        sink.json("datos/no-conformidades.json", jdbc.queryForList(
                "SELECT * FROM findings.findings WHERE audit_id IN (" + in + ") ORDER BY audit_id, id", args));
        sink.json("datos/acciones-correctivas.json", jdbc.queryForList(
                "SELECT * FROM findings.corrective_actions WHERE finding_id IN "
                        + "(SELECT id FROM findings.findings WHERE audit_id IN (" + in + ")) ORDER BY finding_id, id", args));
    }
}

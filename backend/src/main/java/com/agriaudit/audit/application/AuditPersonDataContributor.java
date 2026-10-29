package com.agriaudit.audit.application;

import com.agriaudit.privacy.ExportSink;
import com.agriaudit.privacy.PersonDataContributor;
import com.agriaudit.privacy.PersonIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Auditorías en las que participa la persona y puntos que evaluó. */
@Component
class AuditPersonDataContributor implements PersonDataContributor {

    private final JdbcTemplate jdbc;

    AuditPersonDataContributor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String section() {
        return "auditorías y evaluaciones";
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(Long organizationId, PersonIdentity person, ExportSink sink) {
        Object[] who = person.textKeys();
        sink.json("datos/auditorias-en-las-que-participa.json", jdbc.queryForList(
                "SELECT id, reference, audit_type, status, actual_date, 'auditor jefe' AS participacion "
                        + "FROM audit.audits WHERE organization_id = ? AND lead_auditor IN (?, ?) "
                        + "UNION ALL "
                        + "SELECT id, reference, audit_type, status, actual_date, 'equipo auditor' AS participacion "
                        + "FROM audit.audits WHERE organization_id = ? AND audit_team ~* ? "
                        + "ORDER BY 1",
                organizationId, who[0], who[1], organizationId, AuditPersonText.wordPattern(who)));
        // Solo qué punto evaluó y con qué resultado; no las notas ni los comentarios.
        sink.json("datos/puntos-evaluados.json", jdbc.queryForList(
                "SELECT r.audit_id, a.reference AS audit_reference, r.control_point_id, r.compliance, r.assessed_at "
                        + "FROM audit.audit_item_results r JOIN audit.audits a ON a.id = r.audit_id "
                        + "WHERE a.organization_id = ? AND r.assessed_by IN (?, ?) ORDER BY r.audit_id, r.id",
                organizationId, who[0], who[1]));
    }
}

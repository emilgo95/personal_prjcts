package com.agriaudit.evidence.application;

import com.agriaudit.privacy.ExportSink;
import com.agriaudit.privacy.PersonDataContributor;
import com.agriaudit.privacy.PersonIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Registros de evidencia y vínculos que dio de alta la persona (se buscan por su código de usuario). */
@Component
class EvidencePersonDataContributor implements PersonDataContributor {

    private final JdbcTemplate jdbc;

    EvidencePersonDataContributor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String section() {
        return "evidencias reutilizables";
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(Long organizationId, PersonIdentity person, ExportSink sink) {
        sink.json("datos/evidencias-registradas.json", jdbc.queryForList(
                "SELECT id, type_code, title, issued_on, valid_until, outcome, created_at FROM evidence.evidence_records "
                        + "WHERE organization_id = ? AND created_by_user_id = ? ORDER BY id", organizationId, person.userId()));
        sink.json("datos/evidencias-vinculadas.json", jdbc.queryForList(
                "SELECT id, audit_id, control_point_id, record_id, linked_at FROM evidence.audit_links "
                        + "WHERE organization_id = ? AND linked_by_user_id = ? ORDER BY id", organizationId, person.userId()));
    }
}

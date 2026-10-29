package com.agriaudit.findings.application;

import com.agriaudit.privacy.ExportSink;
import com.agriaudit.privacy.PersonDataContributor;
import com.agriaudit.privacy.PersonIdentity;
import com.agriaudit.audit.AuditLookup;
import com.agriaudit.audit.AuditRef;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Acciones correctivas en las que figura la persona como responsable o verificadora. */
@Component
class FindingsPersonDataContributor implements PersonDataContributor {

    private final JdbcTemplate jdbc;
    private final AuditLookup audits;

    FindingsPersonDataContributor(JdbcTemplate jdbc, AuditLookup audits) {
        this.jdbc = jdbc;
        this.audits = audits;
    }

    @Override
    public String section() {
        return "acciones correctivas";
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(Long organizationId, PersonIdentity person, ExportSink sink) {
        Object[] who = person.textKeys();
        List<Long> auditIds = audits.findByOrganization(organizationId).stream().map(AuditRef::id).toList();
        if (auditIds.isEmpty()) {
            sink.json("datos/acciones-correctivas.json", List.of());
            return;
        }
        String in = String.join(",", java.util.Collections.nCopies(auditIds.size(), "?"));
        java.util.List<Object> args = new java.util.ArrayList<>(List.of(who[0], who[1], who[0], who[1], who[0], who[1],
                who[0], who[1]));
        args.addAll(auditIds);
        sink.json("datos/acciones-correctivas.json", jdbc.queryForList(
                "SELECT ca.id, f.reference AS finding_reference, f.audit_id, ca.status, ca.target_date, "
                        + "ca.completed_date, (ca.responsible_person IN (?, ?)) AS es_responsable, "
                        + "(ca.verified_by IN (?, ?)) AS es_verificador FROM findings.corrective_actions ca "
                        + "JOIN findings.findings f ON f.id = ca.finding_id "
                        + "WHERE (ca.responsible_person IN (?, ?) OR ca.verified_by IN (?, ?)) "
                        + "AND f.audit_id IN (" + in + ") ORDER BY ca.id", args.toArray()));
    }
}

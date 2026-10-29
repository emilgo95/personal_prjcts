package com.agriaudit.findings.application;

import com.agriaudit.identity.IdentityBlockListener;
import com.agriaudit.audit.AuditLookup;
import com.agriaudit.audit.AuditRef;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/** Sustituye el nombre de quien verificó cada acción correctiva al bloquear a un usuario. */
@Component
class FindingsIdentityBlockListener implements IdentityBlockListener {

    private final JdbcTemplate jdbc;
    private final AuditLookup audits;

    FindingsIdentityBlockListener(JdbcTemplate jdbc, AuditLookup audits) {
        this.jdbc = jdbc;
        this.audits = audits;
    }

    @Override
    @Transactional
    public void identityBlocked(Long organizationId, String email, String fullName, String placeholder) {
        Object[] who = new Object[]{email, fullName == null ? email : fullName};
        List<Long> auditIds = audits.findByOrganization(organizationId).stream().map(AuditRef::id).toList();
        if (auditIds.isEmpty()) {
            return;
        }
        String in = String.join(",", java.util.Collections.nCopies(auditIds.size(), "?"));
        Object[] args = new Object[3 + auditIds.size()];
        args[0] = placeholder;
        args[1] = who[0];
        args[2] = who[1];
        for (int i = 0; i < auditIds.size(); i++) {
            args[3 + i] = auditIds.get(i);
        }
        jdbc.update("UPDATE findings.corrective_actions SET verified_by = ? WHERE verified_by IN (?, ?) "
                + "AND finding_id IN (SELECT id FROM findings.findings WHERE audit_id IN (" + in + "))", args);
    }
}

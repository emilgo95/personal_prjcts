package com.agriaudit.audit.application;

import com.agriaudit.identity.IdentityBlockListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/** Sustituye el nombre o correo de quien evaluó cada punto del checklist al bloquear a un usuario. */
@Component
class AuditIdentityBlockListener implements IdentityBlockListener {

    private final JdbcTemplate jdbc;

    AuditIdentityBlockListener(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void identityBlocked(Long organizationId, String email, String fullName, String placeholder) {
        Object[] who = new Object[]{email, fullName == null ? email : fullName};
        jdbc.update("UPDATE audit.audit_item_results SET assessed_by = ? WHERE assessed_by IN (?, ?) "
                + "AND audit_id IN (SELECT id FROM audit.audits WHERE organization_id = ?)",
                placeholder, who[0], who[1], organizationId);
    }
}

package com.agriaudit.documents.application;

import com.agriaudit.identity.IdentityBlockListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/** Sustituye el nombre o correo de quien generó cada documento al bloquear a un usuario. */
@Component
class DocumentsIdentityBlockListener implements IdentityBlockListener {

    private final JdbcTemplate jdbc;

    DocumentsIdentityBlockListener(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void identityBlocked(Long organizationId, String email, String fullName, String placeholder) {
        Object[] who = new Object[]{email, fullName == null ? email : fullName};
        jdbc.update("UPDATE documents.generated_documents SET generated_by = ? WHERE generated_by IN (?, ?) "
                + "AND organization_id = ?", placeholder, who[0], who[1], organizationId);
    }
}

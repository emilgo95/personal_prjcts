package com.agriaudit.storage.application;

import com.agriaudit.identity.IdentityBlockListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/** Sustituye el nombre o correo de quien subió cada adjunto al bloquear a un usuario. */
@Component
class StorageIdentityBlockListener implements IdentityBlockListener {

    private final JdbcTemplate jdbc;

    StorageIdentityBlockListener(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void identityBlocked(Long organizationId, String email, String fullName, String placeholder) {
        Object[] who = new Object[]{email, fullName == null ? email : fullName};
        jdbc.update("UPDATE storage.attachments SET uploaded_by = ? WHERE uploaded_by IN (?, ?) "
                + "AND organization_id = ?", placeholder, who[0], who[1], organizationId);
    }
}

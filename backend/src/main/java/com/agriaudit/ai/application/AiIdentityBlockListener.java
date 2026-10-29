package com.agriaudit.ai.application;

import com.agriaudit.identity.IdentityBlockListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/** Sustituye el nombre o correo de quien revisó cada propuesta de IA al bloquear a un usuario. */
@Component
class AiIdentityBlockListener implements IdentityBlockListener {

    private final JdbcTemplate jdbc;

    AiIdentityBlockListener(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void identityBlocked(Long organizationId, String email, String fullName, String placeholder) {
        Object[] who = new Object[]{email, fullName == null ? email : fullName};
        jdbc.update("UPDATE ai.ai_proposals SET reviewed_by = ? WHERE reviewed_by IN (?, ?) "
                + "AND organization_id = ?", placeholder, who[0], who[1], organizationId);
    }
}

package com.agriaudit.ai.application;

import com.agriaudit.privacy.ExportSink;
import com.agriaudit.privacy.PersonDataContributor;
import com.agriaudit.privacy.PersonIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Propuestas de IA que revisó la persona. */
@Component
class AiPersonDataContributor implements PersonDataContributor {

    private final JdbcTemplate jdbc;

    AiPersonDataContributor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String section() {
        return "propuestas de IA revisadas";
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(Long organizationId, PersonIdentity person, ExportSink sink) {
        Object[] who = person.textKeys();
        sink.json("datos/propuestas-ia-revisadas.json", jdbc.queryForList(
                "SELECT id, assistant, status, model_id, created_at, reviewed_at FROM ai.ai_proposals "
                        + "WHERE organization_id = ? AND reviewed_by IN (?, ?) ORDER BY id",
                organizationId, who[0], who[1]));
    }
}

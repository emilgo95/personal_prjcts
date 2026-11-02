package com.agriaudit.privacy.application;

import com.agriaudit.privacy.ExportSink;
import com.agriaudit.privacy.PersonDataContributor;
import com.agriaudit.privacy.PersonIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Acciones de la persona anotadas en el historial de cambios (se buscan por su código de usuario). */
@Component
class PersonTraceContributor implements PersonDataContributor {

    private final JdbcTemplate jdbc;

    PersonTraceContributor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String section() {
        return "historial de cambios";
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(Long organizationId, PersonIdentity person, ExportSink sink) {
        // Solo qué hizo y cuándo; no el detalle de los valores cambiados, que puede incluir datos de terceros.
        sink.json("datos/historial-de-cambios.json", jdbc.queryForList(
                "SELECT seq, occurred_at, action, entity_type, entity_id, entity_ref, summary, user_role "
                        + "FROM trace.change_log WHERE organization_id = ? AND user_id = ? ORDER BY seq",
                organizationId, person.userId()));
    }
}

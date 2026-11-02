package com.agriaudit.privacy.application;

import com.agriaudit.privacy.ExportSink;
import com.agriaudit.privacy.OrganizationDataContributor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Aporta a la exportación los datos de traza de cambios y sellos (lectura directa: privacy no depende del módulo trace). */
@Component
class TraceDataContributor implements OrganizationDataContributor {

    private final JdbcTemplate jdbc;

    TraceDataContributor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String section() {
        return "traza de cambios y sellos";
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(Long organizationId, ExportSink sink) {
        // Sin nombre ni correo (la traza solo guarda el código de usuario). Se incluyen huellas y firmas para que
        // el cliente pueda comprobar la integridad por su cuenta.
        sink.json("datos/traza-de-cambios.json", jdbc.queryForList(
                "SELECT id, seq, occurred_at, created_at, user_id, user_role, entity_type, entity_id, entity_ref, "
                        + "parent_type, parent_id, audit_id, action, summary, changes, prev_hash, hash, signature, "
                        + "format_version FROM trace.change_log WHERE organization_id = ? ORDER BY seq",
                organizationId));
        sink.json("datos/traza-sellos.json", jdbc.queryForList(
                "SELECT id, sealed_at, last_seq, last_hash, signature FROM trace.seal WHERE organization_id = ? "
                        + "ORDER BY last_seq", organizationId));
    }
}

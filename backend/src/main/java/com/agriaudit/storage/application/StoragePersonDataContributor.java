package com.agriaudit.storage.application;

import com.agriaudit.privacy.ExportSink;
import com.agriaudit.privacy.PersonDataContributor;
import com.agriaudit.privacy.PersonIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adjuntos que subió la persona (solo los datos del adjunto, no su contenido). */
@Component
class StoragePersonDataContributor implements PersonDataContributor {

    private final JdbcTemplate jdbc;

    StoragePersonDataContributor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String section() {
        return "adjuntos subidos";
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(Long organizationId, PersonIdentity person, ExportSink sink) {
        Object[] who = person.textKeys();
        sink.json("datos/adjuntos-subidos.json", jdbc.queryForList(
                "SELECT id, entity_type, entity_id, file_name, content_type, size_bytes, uploaded_at, "
                        + "contains_personal_data FROM storage.attachments WHERE organization_id = ? "
                        + "AND uploaded_by IN (?, ?) ORDER BY id", organizationId, who[0], who[1]));
    }
}

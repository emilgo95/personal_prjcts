package com.agriaudit.documents.application;

import com.agriaudit.privacy.ExportSink;
import com.agriaudit.privacy.PersonDataContributor;
import com.agriaudit.privacy.PersonIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Documentos que generó la persona (solo los datos del documento, no su contenido). */
@Component
class DocumentsPersonDataContributor implements PersonDataContributor {

    private final JdbcTemplate jdbc;

    DocumentsPersonDataContributor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String section() {
        return "documentos generados";
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(Long organizationId, PersonIdentity person, ExportSink sink) {
        Object[] who = person.textKeys();
        sink.json("datos/documentos-generados.json", jdbc.queryForList(
                "SELECT id, audit_id, document_type, standard_code, format, file_name, generated_at "
                        + "FROM documents.generated_documents WHERE organization_id = ? AND generated_by IN (?, ?) "
                        + "ORDER BY id", organizationId, who[0], who[1]));
    }
}

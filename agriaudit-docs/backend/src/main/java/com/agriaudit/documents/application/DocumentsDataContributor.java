package com.agriaudit.documents.application;

import com.agriaudit.privacy.ExportSink;
import com.agriaudit.privacy.OrganizationDataContributor;
import com.agriaudit.storage.BlobStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Aporta a la exportación de la organización los datos de documentos generados. */
@Component
class DocumentsDataContributor implements OrganizationDataContributor {

    private final JdbcTemplate jdbc;
    private final BlobStore blobs;

    DocumentsDataContributor(JdbcTemplate jdbc, BlobStore blobs) {
        this.jdbc = jdbc;
        this.blobs = blobs;
    }

    @Override
    public String section() {
        return "documentos generados";
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(Long organizationId, ExportSink sink) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, audit_id, document_type, standard_code, format, file_name, size_bytes, sha256, "
                        + "generated_by, generated_at, storage_key FROM documents.generated_documents "
                        + "WHERE organization_id = ? ORDER BY id", organizationId);
        sink.json("datos/documentos-generados.json", rows);
        for (Map<String, Object> r : rows) {
            try {
                sink.file("documentos/" + r.get("id") + "-" + safeName(String.valueOf(r.get("file_name"))),
                        blobs.load(String.valueOf(r.get("storage_key"))));
            } catch (RuntimeException e) {
                sink.json("datos/documentos-no-disponibles-" + r.get("id") + ".json", Map.of("id", r.get("id"),
                        "motivo", "El fichero no está disponible en el almacenamiento"));
            }
        }
    }

    /** Nombre apto para un ZIP: sin rutas ni caracteres de control. */
    private static String safeName(String name) {
        String n = name.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        return n.isEmpty() ? "fichero" : n;
    }
}

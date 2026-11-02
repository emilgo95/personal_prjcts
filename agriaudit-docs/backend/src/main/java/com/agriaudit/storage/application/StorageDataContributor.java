package com.agriaudit.storage.application;

import com.agriaudit.privacy.ExportSink;
import com.agriaudit.privacy.OrganizationDataContributor;
import com.agriaudit.storage.BlobStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Aporta a la exportación de la organización los datos de adjuntos de evidencia. */
@Component
class StorageDataContributor implements OrganizationDataContributor {

    private final JdbcTemplate jdbc;
    private final BlobStore blobs;

    StorageDataContributor(JdbcTemplate jdbc, BlobStore blobs) {
        this.jdbc = jdbc;
        this.blobs = blobs;
    }

    @Override
    public String section() {
        return "adjuntos de evidencia";
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(Long organizationId, ExportSink sink) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, entity_type, entity_id, file_name, content_type, size_bytes, uploaded_by, uploaded_at, "
                        + "contains_personal_data, storage_key FROM storage.attachments WHERE organization_id = ? ORDER BY id", organizationId);
        sink.json("datos/adjuntos.json", rows);
        for (Map<String, Object> r : rows) {
            try {
                sink.file("adjuntos/" + r.get("id") + "-" + safeName(String.valueOf(r.get("file_name"))),
                        blobs.load(String.valueOf(r.get("storage_key"))));
            } catch (RuntimeException e) {
                // Un fichero que ya no está en el almacén no debe impedir devolver el resto de los datos.
                sink.json("datos/adjuntos-no-disponibles-" + r.get("id") + ".json", Map.of("id", r.get("id"),
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

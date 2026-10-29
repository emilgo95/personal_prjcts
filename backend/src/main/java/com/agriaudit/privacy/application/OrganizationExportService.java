package com.agriaudit.privacy.application;

import com.agriaudit.privacy.ExportSink;
import com.agriaudit.privacy.OrganizationDataContributor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Reúne en un ZIP los datos de una organización que aporta cada módulo: documentos JSON legibles por máquina y los
 * ficheros (adjuntos y documentos generados). No incluye contraseñas ni los datos identificativos bloqueados.
 */
@Service
public class OrganizationExportService {

    private final List<OrganizationDataContributor> contributors;
    private final JsonMapper json;

    public OrganizationExportService(List<OrganizationDataContributor> contributors, JsonMapper json) {
        this.contributors = contributors;
        this.json = json;
    }

    public void export(Long organizationId, OutputStream out) throws IOException {
        List<String> index = new ArrayList<>();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            ExportSink sink = new ExportSink() {
                @Override
                public void json(String path, Object value) {
                    byte[] bytes = json.writerWithDefaultPrettyPrinter().writeValueAsBytes(normalize(value));
                    put(path, bytes);
                }

                @Override
                public void file(String path, byte[] content) {
                    put(path, content);
                }

                private void put(String path, byte[] bytes) {
                    try {
                        zip.putNextEntry(new ZipEntry(path));
                        zip.write(bytes);
                        zip.closeEntry();
                        index.add(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            };
            for (OrganizationDataContributor c : contributors) {
                c.contribute(organizationId, sink);
            }
            String readme = "Exportación de los datos de la organización " + organizationId + "\n"
                    + "Generada el " + Instant.now() + "\n\n"
                    + "Contenido: documentos JSON (datos/...) y ficheros (adjuntos/..., documentos/...).\n"
                    + "No incluye contraseñas ni los datos identificativos de usuarios bloqueados.\n\n"
                    + "Ficheros incluidos:\n" + String.join("\n", index) + "\n";
            zip.putNextEntry(new ZipEntry("LEEME.txt"));
            zip.write(readme.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    /** Valores de JDBC a algo que se serialice bien: fechas en ISO y tipos propios de PostgreSQL como texto. */
    @SuppressWarnings("unchecked")
    static Object normalize(Object value) {
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), normalize(v)));
            return out;
        }
        if (value instanceof List<?> l) {
            return l.stream().map(OrganizationExportService::normalize).toList();
        }
        if (value instanceof Timestamp t) {
            return t.toInstant().toString();
        }
        if (value instanceof java.sql.Date d) {
            return d.toString();
        }
        if (value instanceof java.time.OffsetDateTime o) {
            return o.toInstant().toString();
        }
        if (value != null && value.getClass().getName().equals("org.postgresql.util.PGobject")) {
            return String.valueOf(value);
        }
        return value;
    }
}

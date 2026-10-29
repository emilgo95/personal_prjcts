package com.agriaudit.privacy.application;

import com.agriaudit.privacy.OrganizationDataContributor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.List;

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
        try (ZipExport zip = new ZipExport(out, json)) {
            for (OrganizationDataContributor c : contributors) {
                c.contribute(organizationId, zip.sink());
            }
            zip.finish("Exportación de los datos de la organización " + organizationId + "\n"
                    + "Generada el " + Instant.now() + "\n\n"
                    + "Contenido: documentos JSON (datos/...) y ficheros (adjuntos/..., documentos/...).\n"
                    + "No incluye contraseñas ni los datos identificativos de usuarios bloqueados.\n");
        }
    }
}

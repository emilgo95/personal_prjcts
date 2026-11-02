package com.agriaudit.privacy.application;

import com.agriaudit.privacy.PersonDataContributor;
import com.agriaudit.privacy.PersonDataExporter;
import com.agriaudit.privacy.PersonIdentity;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.List;

/** Reúne en un ZIP lo que la aplicación guarda sobre una persona (derecho de acceso). */
@Service
public class PersonExportService implements PersonDataExporter {

    private final List<PersonDataContributor> contributors;
    private final JsonMapper json;

    public PersonExportService(List<PersonDataContributor> contributors, JsonMapper json) {
        this.contributors = contributors;
        this.json = json;
    }

    @Override
    public void export(Long organizationId, PersonIdentity person, OutputStream out) throws IOException {
        try (ZipExport zip = new ZipExport(out, json)) {
            for (PersonDataContributor c : contributors) {
                c.contribute(organizationId, person, zip.sink());
            }
            zip.finish("Datos que AgriAudit Docs guarda sobre el usuario n.º " + person.userId() + "\n"
                    + "Generada el " + Instant.now() + "\n\n"
                    + "Incluye: cuenta de usuario, trabajo registrado a su nombre (auditorías, evaluaciones, adjuntos,\n"
                    + "documentos, acciones correctivas, estándares importados, propuestas de IA revisadas) y las\n"
                    + "acciones suyas anotadas en el historial de cambios.\n"
                    + "No incluye: la contraseña, el contenido de los ficheros subidos ni los comentarios y textos\n"
                    + "escritos en los registros (pueden contener datos de otras personas).\n");
        }
    }
}

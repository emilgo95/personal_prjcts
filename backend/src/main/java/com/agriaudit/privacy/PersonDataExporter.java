package com.agriaudit.privacy;

import java.io.IOException;
import java.io.OutputStream;

/** API pública de {@code privacy} para exportar los datos de una persona en un ZIP (la usa {@code identity}). */
public interface PersonDataExporter {

    void export(Long organizationId, PersonIdentity person, OutputStream out) throws IOException;
}

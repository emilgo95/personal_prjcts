package com.agriaudit.privacy;

/** Puerto: un módulo aporta sus datos de una organización a la exportación. Lo implementan los módulos con datos. */
public interface OrganizationDataContributor {

    /** Nombre corto de la sección, para el índice del fichero de exportación (p. ej. «auditorías»). */
    String section();

    /** Vuelca los datos de la organización en el destino; no debe incluir contraseñas ni claves. */
    void contribute(Long organizationId, ExportSink sink);
}

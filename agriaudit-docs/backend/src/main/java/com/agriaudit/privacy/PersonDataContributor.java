package com.agriaudit.privacy;

/**
 * Puerto: un módulo aporta a la exportación de los datos de UNA persona (derecho de acceso, art. 15 RGPD) lo que tiene
 * guardado sobre ella. Debe incluir solo datos de esa persona: nada de comentarios ni datos de terceros.
 */
public interface PersonDataContributor {

    /** Nombre corto de la sección, para el índice del fichero. */
    String section();

    void contribute(Long organizationId, PersonIdentity person, ExportSink sink);
}

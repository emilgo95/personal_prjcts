package com.agriaudit.ai.port;

import com.agriaudit.ai.domain.ExtractedText;

/**
 * Puerto de extracción de texto de documentos (RT-A01). Hoy lo implementa el OCR autoalojado
 * (RapidOCR, sin enviar datos a terceros); la visión de Claude será otro adaptador elegible por
 * configuración, con RapidOCR como respaldo determinista.
 */
public interface DocExtractionPort {

    /** Si hay un motor configurado; si no, la función se oculta en el frontal (RS-IA-10). */
    boolean isAvailable();

    ExtractedText extract(byte[] content, String fileName, String contentType);
}

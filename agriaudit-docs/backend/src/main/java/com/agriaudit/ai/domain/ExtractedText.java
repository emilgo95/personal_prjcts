package com.agriaudit.ai.domain;

/**
 * Texto extraído de un documento (imagen, escaneado o PDF), independiente del motor que lo produjo.
 *
 * @param engine         motor que lo extrajo (p. ej. RapidOCR, visión de un LLM)
 * @param pages          páginas procesadas
 * @param truncated      si el documento superaba el límite de páginas y se procesó solo en parte
 * @param meanConfidence confianza media 0–1 (0 si el motor no la informa)
 */
public record ExtractedText(String engine, String text, int pages, boolean truncated, double meanConfidence) {

    public ExtractedText {
        text = text == null ? "" : text;
    }
}

package com.agriaudit.documents.infrastructure.render;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RenderLimitsTest {

    @Test
    void elPdfConservaElLatinoYSustituyeLoQueNoPuedeDibujar() {
        assertThat(PdfDocumentRenderer.replaceUnsupportedChars("Explotación «Ñandú» – 5 €"))
                .isEqualTo("Explotación «Ñandú» – 5 €");
        assertThat(PdfDocumentRenderer.replaceUnsupportedChars("Фермер 农场 \uD83C\uDF45")).isEqualTo("?????? ?? ?");
    }

    @Test
    void unaCeldaDeExcelNuncaSuperaElLimite() {
        String largo = "x".repeat(40_000);

        assertThat(XlsxDocumentRenderer.clip(largo)).hasSizeLessThanOrEqualTo(32_767).endsWith("[recortado]");
        assertThat(XlsxDocumentRenderer.clip("corto")).isEqualTo("corto");
        assertThat(XlsxDocumentRenderer.clip(null)).isEmpty();
    }
}

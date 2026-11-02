package com.agriaudit.documents.infrastructure.render;

import com.agriaudit.documents.domain.DocumentTemplate;
import com.agriaudit.documents.domain.DocumentFormat;
import com.agriaudit.documents.domain.DocumentType;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.assertj.core.api.Assertions.assertThat;
import com.agriaudit.documents.application.DocumentModel;

/**
 * Verifica que las plantillas Thymeleaf se procesan y que openhtmltopdf produce un PDF válido,
 * sin necesidad de levantar el contexto de Spring ni la base de datos.
 */
class PdfTemplateRenderTest {

    private PdfDocumentRenderer renderer() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return new PdfDocumentRenderer(engine);
    }

    private DocumentTemplate template(DocumentType type, String ref) {
        DocumentTemplate t = new DocumentTemplate();
        t.setDocumentType(type);
        t.setFormat(DocumentFormat.PDF);
        t.setEngine("PDF_HTML");
        t.setTemplateRef(ref);
        return t;
    }

    @Test
    void rendersEveryPdfTemplateToValidPdf() {
        PdfDocumentRenderer renderer = renderer();
        DocumentModel model = TestModels.sample();

        String[][] templates = {
                {"AUDIT_PLAN", "docs/audit-plan"},
                {"CHECKLIST", "docs/checklist"},
                {"NONCONFORMITY_REPORT", "docs/nonconformity"},
                {"AUDIT_REPORT", "docs/audit-report"},
                {"COMPLIANCE_SUMMARY", "docs/compliance"},
                {"GRASP_ASSESSMENT", "docs/grasp-assessment"},
                {"SMETA_REPORT", "docs/smeta-report"},
        };

        for (String[] t : templates) {
            byte[] pdf = renderer.render(template(DocumentType.valueOf(t[0]), t[1]), model);
            assertThat(pdf).as("PDF para %s", t[0]).isNotEmpty();
            assertThat(new String(pdf, 0, 5)).startsWith("%PDF-");
        }
    }
}

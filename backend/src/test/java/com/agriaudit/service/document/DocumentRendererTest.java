package com.agriaudit.service.document;

import com.agriaudit.domain.DocumentTemplate;
import com.agriaudit.domain.enums.DocumentFormat;
import com.agriaudit.domain.enums.DocumentType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentRendererTest {

    private final DocxDocumentRenderer docx = new DocxDocumentRenderer();
    private final XlsxDocumentRenderer xlsx = new XlsxDocumentRenderer();

    private DocumentTemplate template(DocumentType type, DocumentFormat format, String engine) {
        DocumentTemplate t = new DocumentTemplate();
        t.setDocumentType(type);
        t.setFormat(format);
        t.setEngine(engine);
        t.setTemplateRef("test");
        return t;
    }

    @Test
    void generatesValidDocxForEachSupportedType() throws Exception {
        DocumentModel model = TestModels.sample();
        for (DocumentType type : new DocumentType[]{
                DocumentType.AUDIT_PLAN, DocumentType.NONCONFORMITY_REPORT, DocumentType.CAPA_REPORT,
                DocumentType.AUDIT_REPORT, DocumentType.SMETA_REPORT}) {
            byte[] bytes = docx.render(template(type, DocumentFormat.DOCX, "DOCX_POI"), model);
            assertThat(bytes).isNotEmpty();
            // Es un ZIP OOXML válido (cabecera "PK")
            assertThat(bytes[0]).isEqualTo((byte) 'P');
            assertThat(bytes[1]).isEqualTo((byte) 'K');
            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
                assertThat(doc.getParagraphs()).isNotEmpty();
            }
        }
    }

    @Test
    void generatesValidXlsxForChecklistAndCapa() throws Exception {
        DocumentModel model = TestModels.sample();
        for (DocumentType type : new DocumentType[]{
                DocumentType.CHECKLIST, DocumentType.GRASP_ASSESSMENT, DocumentType.CAPA_REPORT}) {
            byte[] bytes = xlsx.render(template(type, DocumentFormat.XLSX, "XLSX_POI"), model);
            assertThat(bytes).isNotEmpty();
            try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                assertThat(wb.getNumberOfSheets()).isGreaterThanOrEqualTo(1);
            }
        }
    }
}

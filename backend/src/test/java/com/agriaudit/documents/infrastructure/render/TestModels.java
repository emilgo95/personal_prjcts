package com.agriaudit.documents.infrastructure.render;

import com.agriaudit.storage.AttachmentDtos.AttachmentDto;
import com.agriaudit.audit.AuditDtos.AuditDetailDto;
import com.agriaudit.audit.AuditDtos.AuditItemResultDto;
import com.agriaudit.catalog.CatalogDtos.ChapterDto;
import com.agriaudit.catalog.CatalogDtos.ControlPointDto;
import com.agriaudit.catalog.CatalogDtos.StandardDetailDto;
import com.agriaudit.fieldbook.FarmDtos.FarmDto;
import com.agriaudit.findings.FindingDtos.CorrectiveActionDto;
import com.agriaudit.findings.FindingDtos.FindingDto;
import com.agriaudit.audit.ScoreDtos.GroupScore;
import com.agriaudit.audit.ScoreDtos.LevelScore;
import com.agriaudit.audit.ScoreDtos.ScoreResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.agriaudit.documents.application.DocumentModel;

public final class TestModels {

    private TestModels() {
    }

    public static DocumentModel sample() {
        FarmDto farm = new FarmDto(1L, "Finca Demo S.L.", "Finca Demo", "4049928999999",
                "Camino de la Vega 1", "Almería", "Andalucía", "España",
                new BigDecimal("36.8"), new BigDecimal("-2.4"), new BigDecimal("42.5"),
                "Tomate, Pimiento", "Invernadero 1, Invernadero 2",
                "María López", "maria@fincademo.test", "+34 600 000 000", "Explotación de referencia");

        AuditDetailDto audit = new AuditDetailDto(
                7L, "AUD-2026-0001", "GLOBALGAP_IFA_V6", "GLOBALG.A.P. IFA", "v6 Smart",
                "SELF_ASSESSMENT", "IN_PROGRESS", "Frutas y hortalizas, invernadero",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1),
                "Ana Auditora", "Ana Auditora, Luis Técnico", "Camino de la Vega 1, Almería",
                "Tomate", Instant.now(), Instant.now(), farm, 3, 2, 67, 1L);

        ControlPointDto cp1 = new ControlPointDto(101L, "AF 2.1", "Trazabilidad del producto certificado",
                "El sistema permite trazar el producto.", "MAJOR_MUST", "Identificación de lotes.",
                "Registros de lote", "ALL", "MGMT", "Gestión y trazabilidad", List.of());
        ControlPointDto cp2 = new ControlPointDto(102L, "AF 2.5", "Autoevaluación anual",
                "El productor realiza una autoevaluación anual.", "MAJOR_MUST", "Checklist firmado.",
                "Checklist", "ALL", "MGMT", "Gestión y trazabilidad", List.of());
        ControlPointDto cp3 = new ControlPointDto(103L, "CB 7.4", "Respeto de LMR",
                "Se respeta el plazo de seguridad.", "MINOR_MUST", "Registros de aplicación.",
                "Cuaderno de campo", "CROPS", "PROD", "Proceso de producción", List.of());

        ChapterDto ch1 = new ChapterDto(1L, "MGMT", "Gestión y trazabilidad", "Procedimientos y registros", 1,
                List.of(cp1, cp2));
        ChapterDto ch2 = new ChapterDto(2L, "PROD", "Proceso de producción", "Fitosanitarios y riego", 2, List.of(cp3));

        StandardDetailDto standard = new StandardDetailDto(10L, "GLOBALGAP_IFA_V6", "v6 Smart", "GLOBALG.A.P. IFA",
                "GLOBALG.A.P.", "GAP", "Estándar GAP", "{\"type\":\"MAJOR_MINOR\"}",
                "https://www.globalgap.org", List.of(ch1, ch2));

        Instant now = Instant.now();
        AttachmentDto photo = new AttachmentDto(500L, "AUDIT_ITEM_RESULT", 1L, "foto-parcela.png",
                "image/png", 1234L, "ana", now, true);
        AttachmentDto pdf = new AttachmentDto(501L, "FINDING", 1L, "analitica-agua.pdf",
                "application/pdf", 45678L, "ana", now, false);

        AuditItemResultDto r1 = new AuditItemResultDto(1L, 101L, "AF 2.1", "Trazabilidad del producto certificado",
                "desc", "guía", "MAJOR_MUST", "ALL", "MGMT", "Gestión y trazabilidad",
                "COMPLIANT", "Prueba de trazabilidad realizada", "OK", now, "ana", List.of(photo));
        AuditItemResultDto r2 = new AuditItemResultDto(2L, 102L, "AF 2.5", "Autoevaluación anual",
                "desc", "guía", "MAJOR_MUST", "ALL", "MGMT", "Gestión y trazabilidad",
                "NON_COMPLIANT", "No consta autoevaluación de este año", "Falta", now, "ana", List.of());
        AuditItemResultDto r3 = new AuditItemResultDto(null, 103L, "CB 7.4", "Respeto de LMR",
                "desc", "guía", "MINOR_MUST", "CROPS", "PROD", "Proceso de producción",
                "NOT_ASSESSED", null, null, null, null, List.of());
        List<AuditItemResultDto> results = List.of(r1, r2, r3);

        CorrectiveActionDto ca = new CorrectiveActionDto(1L, 1L, "No se planificó la autoevaluación",
                "Realizar la autoevaluación inmediatamente", "Incluir la autoevaluación en el calendario anual",
                "Responsable de calidad", LocalDate.of(2026, 9, 15), null, null, null, "IN_PROGRESS",
                now, now);
        FindingDto finding = new FindingDto(1L, 7L, 102L, "AF 2.5", "Autoevaluación anual", "NC-001",
                "MAJOR", "Falta la autoevaluación anual", "No se ha documentado la autoevaluación del año en curso",
                "Gestión", LocalDate.of(2026, 9, 15), "IN_PROGRESS", now, List.of(ca), List.of(pdf));

        ScoreResult score = new ScoreResult("GLOBALGAP_IFA_V6", "GLOBALG.A.P. IFA", "MAJOR_MINOR", false,
                "FAIL", 100.0, 95.0,
                List.of(new LevelScore("MAJOR_MUST", 2, 1, 1, 0, 0, 50.0),
                        new LevelScore("MINOR_MUST", 1, 0, 0, 0, 1, null)),
                List.of(new GroupScore("MGMT", "Gestión y trazabilidad", 2, 1, 1, 0, 0, 50.0),
                        new GroupScore("PROD", "Proceso de producción", 1, 0, 0, 0, 1, 0.0)),
                List.of(),
                3, 2, 67, List.of("Major Musts cumplidos: 50,0% (mínimo 100%).",
                "Minor Musts cumplidos: 100,0% (mínimo 95%)."));

        Map<Long, AuditItemResultDto> byCp = results.stream()
                .collect(Collectors.toMap(AuditItemResultDto::controlPointId, Function.identity()));

        // PNG 1x1 para ejercitar el embebido de imágenes en las plantillas
        String pngDataUri = "data:image/png;base64,"
                + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
        DocumentModel.EvidenceRef img = new DocumentModel.EvidenceRef(500L, "foto-parcela.png",
                "image/png", 1234L, "ana", true, pngDataUri);
        DocumentModel.EvidenceRef doc = new DocumentModel.EvidenceRef(501L, "analitica-agua.pdf",
                "application/pdf", 45678L, "ana", false, null);

        return new DocumentModel("Organización Demo", "ana@demo.test", "26/08/2026 12:00",
                "Documento de prueba", audit, farm, standard, standard.chapters(), results, byCp,
                List.of(finding), score,
                Map.of(101L, List.of(img)),
                Map.of(1L, List.of(doc)),
                List.of(img),
                DocumentModel.DEFAULT_DISCLAIMER);
    }
}

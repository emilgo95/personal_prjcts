package com.agriaudit.service.ocr;

import com.agriaudit.domain.Attachment;
import com.agriaudit.catalog.domain.ControlPoint;
import com.agriaudit.catalog.domain.ControlLevel;
import com.agriaudit.repo.AttachmentRepository;
import com.agriaudit.catalog.CatalogLookup;
import com.agriaudit.security.AppUserPrincipal;
import com.agriaudit.service.AuditService;
import com.agriaudit.service.document.DocumentStorageService;
import com.agriaudit.service.ocr.OcrClient.OcrResult;
import com.agriaudit.web.dto.EvidenceAnalysisDtos.EvidenceAnalysis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EvidenceAnalysisServiceTest {

    private AuditService auditService;
    private AttachmentRepository attachments;
    private CatalogLookup catalog;
    private DocumentStorageService storage;
    private OcrClient ocrClient;
    private EvidenceAnalysisService service;

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        attachments = mock(AttachmentRepository.class);
        catalog = mock(CatalogLookup.class);
        storage = mock(DocumentStorageService.class);
        ocrClient = mock(OcrClient.class);
        service = new EvidenceAnalysisService(auditService, attachments, catalog, storage, ocrClient, 0.55);

        AppUserPrincipal principal = mock(AppUserPrincipal.class);
        when(principal.getOrganizationId()).thenReturn(1L);
        when(principal.getId()).thenReturn(1L);
        when(principal.getUsername()).thenReturn("ana@demo.test");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        Attachment att = new Attachment();
        att.setId(9L);
        att.setOrganizationId(1L);
        att.setFileName("nota.jpg");
        att.setContentType("image/jpeg");
        att.setStorageKey("attachments/2026/x-nota.jpg");
        when(attachments.findByIdAndOrganizationId(9L, 1L)).thenReturn(Optional.of(att));
        when(storage.load(any())).thenReturn(new byte[]{1, 2, 3});
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void ocrReturns(String text, double confidence) {
        when(ocrClient.recognize(any(), any(), any())).thenReturn(new OcrResult(
                "rapidocr-onnxruntime", "nota.jpg", 1, false, 3, confidence, 120, text, List.of()));
    }

    @Test
    void detectsNonComplianceAndProposesFinding() {
        ocrReturns("Inspección del almacén de fitosanitarios. Se observa que NO se dispone de "
                + "kit de derrames y el inventario está caducado. No conforme.", 0.82);
        when(catalog.findControlPoint(50L)).thenReturn(Optional.of(cp(ControlLevel.MAJOR_MUST)));

        EvidenceAnalysis a = service.analyze(1L, 9L, 50L);

        assertThat(a.suggestedCompliance()).isEqualTo("NON_COMPLIANT");
        assertThat(a.suggestedFindings()).hasSize(1);
        assertThat(a.suggestedFindings().get(0).classification()).isEqualTo("MAJOR");
        assertThat(a.suggestedEvidenceNote()).contains("nota.jpg").contains("caducado");
        assertThat(a.warnings()).isEmpty();
    }

    @Test
    void detectsComplianceWithoutFinding() {
        ocrReturns("Registro de formación de trabajadores actualizado y disponible. "
                + "Todo conforme, sin incidencias.", 0.9);

        EvidenceAnalysis a = service.analyze(1L, 9L, null);

        assertThat(a.suggestedCompliance()).isEqualTo("COMPLIANT");
        assertThat(a.suggestedFindings()).isEmpty();
    }

    @Test
    void doesNotFlagNormalProcessVocabulary() {
        // Vocabulario habitual de un diagrama de flujo APPCC: no debe interpretarse como incumplimiento.
        ocrReturns("Diagrama de flujo del proceso. PCC 2 detección de metales con rechazo automático. "
                + "Límites críticos de cloro libre. Prerrequisitos operativos por etapa.", 0.95);

        EvidenceAnalysis a = service.analyze(1L, 9L, null);

        assertThat(a.suggestedCompliance()).isNotEqualTo("NON_COMPLIANT");
        assertThat(a.suggestedFindings()).isEmpty();
    }

    @Test
    void warnsOnLowConfidenceAndEmptyText() {
        ocrReturns("", 0.0);

        EvidenceAnalysis a = service.analyze(1L, 9L, null);

        assertThat(a.suggestedCompliance()).isNull();
        assertThat(a.warnings()).anyMatch(w -> w.contains("no ha detectado texto"));
    }

    private ControlPoint cp(ControlLevel level) {
        ControlPoint cp = new ControlPoint();
        cp.setId(50L);
        cp.setLevel(level);
        return cp;
    }
}

package com.agriaudit.ai.application;

import com.agriaudit.catalog.ControlPointView;
import com.agriaudit.catalog.ControlLevel;
import com.agriaudit.storage.AttachmentDtos.AttachmentDto;
import com.agriaudit.storage.AttachmentStore;
import com.agriaudit.catalog.CatalogLookup;
import com.agriaudit.identity.AppUserPrincipal;
import com.agriaudit.audit.AuditLookup;
import com.agriaudit.ai.domain.ExtractedText;
import com.agriaudit.ai.port.DocExtractionPort;
import com.agriaudit.ai.application.EvidenceAnalysisDtos.EvidenceAnalysis;
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

    private AuditLookup auditService;
    private AttachmentStore attachments;
    private CatalogLookup catalog;
    private DocExtractionPort ocrClient;
    private EvidenceAnalysisService service;

    @BeforeEach
    void setUp() {
        auditService = mock(AuditLookup.class);
        attachments = mock(AttachmentStore.class);
        catalog = mock(CatalogLookup.class);
        ocrClient = mock(DocExtractionPort.class);
        service = new EvidenceAnalysisService(auditService, attachments, catalog, ocrClient, 0.55);

        AppUserPrincipal principal = mock(AppUserPrincipal.class);
        when(principal.getOrganizationId()).thenReturn(1L);
        when(principal.getId()).thenReturn(1L);
        when(principal.getUsername()).thenReturn("ana@demo.test");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        AttachmentDto att = new AttachmentDto(9L, "AUDIT_ITEM_RESULT", 1L, "nota.jpg", "image/jpeg", 3L,
                "ana@demo.test", null, true, null);
        when(attachments.find(1L, 9L)).thenReturn(Optional.of(att));
        when(attachments.content(1L, 9L)).thenReturn(new byte[]{1, 2, 3});
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void ocrReturns(String text, double confidence) {
        when(ocrClient.extract(any(), any(), any())).thenReturn(
                new ExtractedText("rapidocr-onnxruntime", text, 1, false, confidence));
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

    private ControlPointView cp(ControlLevel level) {
        return new ControlPointView(50L, "AF 1.1", "Punto", null, null, level, null, "AF", "Capítulo", 1L, "STD");
    }
}

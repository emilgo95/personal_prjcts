package com.agriaudit.audit.application;

import com.agriaudit.audit.ScoreDtos.ScoreResult;
import com.agriaudit.audit.domain.Audit;
import com.agriaudit.audit.domain.AuditItemResult;
import com.agriaudit.audit.domain.ComplianceResult;
import com.agriaudit.audit.infrastructure.persistence.AuditItemResultRepository;
import com.agriaudit.catalog.CatalogLookup;
import com.agriaudit.catalog.ControlLevel;
import com.agriaudit.catalog.ControlPointView;
import com.agriaudit.catalog.StandardView;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScoringServiceTest {

    private static final StandardView GLOBALGAP = new StandardView(10L, "GLOBALGAP_IFA_V6", "6.0", "GLOBALG.A.P. IFA",
            "{\"type\":\"MAJOR_MINOR\",\"majorMustMinPct\":100,\"minorMustMinPct\":95}");
    private static final StandardView SMETA = new StandardView(30L, "SMETA_6_1", "6.1", "SMETA",
            "{\"type\":\"PILLAR\",\"reportOnly\":true}");

    private CatalogLookup catalog;
    private AuditItemResultRepository results;
    private ScoringService scoringService;
    private final AtomicLong ids = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        catalog = mock(CatalogLookup.class);
        results = mock(AuditItemResultRepository.class);
        scoringService = new ScoringService(catalog, results, JsonMapper.builder().build());
        when(catalog.findStandard(GLOBALGAP.id())).thenReturn(Optional.of(GLOBALGAP));
        when(catalog.findStandard(SMETA.id())).thenReturn(Optional.of(SMETA));
    }

    private Audit audit(long id, StandardView standard) {
        Audit audit = new Audit();
        audit.setId(id);
        audit.setStandardId(standard.id());
        return audit;
    }

    private ControlPointView cp(StandardView standard, String chapter, ControlLevel level, String scopes) {
        long id = ids.getAndIncrement();
        return new ControlPointView(id, "R" + id, "Punto " + id, null, null, level, scopes,
                chapter, "Capítulo " + chapter, standard.id(), standard.code());
    }

    private ControlPointView cp(StandardView standard, ControlLevel level) {
        return cp(standard, "MGMT", level, null);
    }

    private AuditItemResult result(ControlPointView cp, ComplianceResult compliance) {
        AuditItemResult r = new AuditItemResult();
        r.setControlPointId(cp.id());
        r.setCompliance(compliance);
        return r;
    }

    @Test
    void passWhenAllMajorAndEnoughMinorAreCompliant() {
        Audit audit = audit(1L, GLOBALGAP);
        List<ControlPointView> cps = new ArrayList<>();
        List<AuditItemResult> res = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ControlPointView c = cp(GLOBALGAP, ControlLevel.MAJOR_MUST);
            cps.add(c);
            res.add(result(c, ComplianceResult.COMPLIANT));
        }
        for (int i = 0; i < 20; i++) {
            ControlPointView c = cp(GLOBALGAP, ControlLevel.MINOR_MUST);
            cps.add(c);
            res.add(result(c, i == 0 ? ComplianceResult.NON_COMPLIANT : ComplianceResult.COMPLIANT));
        }
        when(catalog.controlPointsOf("GLOBALGAP_IFA_V6")).thenReturn(cps);
        when(results.findByAuditId(1L)).thenReturn(res);

        ScoreResult score = scoringService.score(audit);

        assertThat(score.overallResult()).isEqualTo("PASS");
        assertThat(score.progressPct()).isEqualTo(100);
    }

    @Test
    void failWhenAMajorMustIsNonCompliant() {
        Audit audit = audit(1L, GLOBALGAP);
        ControlPointView major = cp(GLOBALGAP, ControlLevel.MAJOR_MUST);
        ControlPointView minor = cp(GLOBALGAP, ControlLevel.MINOR_MUST);
        when(catalog.controlPointsOf("GLOBALGAP_IFA_V6")).thenReturn(List.of(major, minor));
        when(results.findByAuditId(1L)).thenReturn(List.of(
                result(major, ComplianceResult.NON_COMPLIANT),
                result(minor, ComplianceResult.COMPLIANT)));

        ScoreResult score = scoringService.score(audit);

        assertThat(score.overallResult()).isEqualTo("FAIL");
    }

    @Test
    void pendingWhenMandatoryPointsRemainUnassessed() {
        Audit audit = audit(1L, GLOBALGAP);
        ControlPointView major = cp(GLOBALGAP, ControlLevel.MAJOR_MUST);
        when(catalog.controlPointsOf("GLOBALGAP_IFA_V6")).thenReturn(List.of(major));
        when(results.findByAuditId(1L)).thenReturn(List.of());

        ScoreResult score = scoringService.score(audit);

        assertThat(score.overallResult()).isEqualTo("PENDING");
        assertThat(score.progressPct()).isZero();
    }

    @Test
    void smetaIsReportOnlyAndGroupsByPillar() {
        Audit audit = audit(2L, SMETA);
        ControlPointView c1 = cp(SMETA, "LABOUR", ControlLevel.MAJOR_MUST, "LABOUR");
        ControlPointView c2 = cp(SMETA, "LABOUR", ControlLevel.MAJOR_MUST, "HEALTH_SAFETY");

        when(catalog.controlPointsOf("SMETA_6_1")).thenReturn(List.of(c1, c2));
        when(results.findByAuditId(2L)).thenReturn(List.of(
                result(c1, ComplianceResult.COMPLIANT),
                result(c2, ComplianceResult.NON_COMPLIANT)));

        ScoreResult score = scoringService.score(audit);

        assertThat(score.overallResult()).isEqualTo("REPORT_ONLY");
        assertThat(score.reportOnly()).isTrue();
        assertThat(score.pillarBreakdown()).extracting("code")
                .containsExactlyInAnyOrder("LABOUR", "HEALTH_SAFETY");
    }
}

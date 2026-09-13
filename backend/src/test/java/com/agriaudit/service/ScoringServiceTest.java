package com.agriaudit.service;

import com.agriaudit.domain.*;
import com.agriaudit.domain.enums.ComplianceResult;
import com.agriaudit.catalog.domain.ControlLevel;
import com.agriaudit.catalog.domain.StandardCategory;
import com.agriaudit.repo.AuditItemResultRepository;
import com.agriaudit.catalog.CatalogLookup;
import com.agriaudit.web.dto.ScoreDtos.ScoreResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.agriaudit.catalog.domain.AuditStandard;
import com.agriaudit.catalog.domain.ControlPoint;
import com.agriaudit.catalog.domain.StandardChapter;

class ScoringServiceTest {

    private CatalogLookup catalog;
    private AuditItemResultRepository results;
    private ScoringService scoringService;
    private final AtomicLong ids = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        catalog = mock(CatalogLookup.class);
        results = mock(AuditItemResultRepository.class);
        scoringService = new ScoringService(catalog, results, new ObjectMapper());
    }

    private Audit globalgapAudit() {
        AuditStandard standard = new AuditStandard();
        standard.setId(10L);
        standard.setCode("GLOBALGAP_IFA_V6");
        standard.setName("GLOBALG.A.P. IFA");
        standard.setCategory(StandardCategory.GAP);
        standard.setScoringRule("{\"type\":\"MAJOR_MINOR\",\"majorMustMinPct\":100,\"minorMustMinPct\":95}");
        Audit audit = new Audit();
        audit.setId(1L);
        audit.setStandard(standard);
        return audit;
    }

    private ControlPoint cp(StandardChapter chapter, ControlLevel level) {
        ControlPoint cp = new ControlPoint();
        cp.setId(ids.getAndIncrement());
        cp.setChapter(chapter);
        cp.setRef("R" + cp.getId());
        cp.setTitle("Punto " + cp.getId());
        cp.setLevel(level);
        return cp;
    }

    private AuditItemResult result(ControlPoint cp, ComplianceResult compliance) {
        AuditItemResult r = new AuditItemResult();
        r.setControlPoint(cp);
        r.setCompliance(compliance);
        return r;
    }

    private StandardChapter chapter(AuditStandard standard, String code) {
        StandardChapter ch = new StandardChapter();
        ch.setId(ids.getAndIncrement());
        ch.setStandard(standard);
        ch.setCode(code);
        ch.setTitle("Capítulo " + code);
        return ch;
    }

    @Test
    void passWhenAllMajorAndEnoughMinorAreCompliant() {
        Audit audit = globalgapAudit();
        StandardChapter ch = chapter(audit.getStandard(), "MGMT");
        List<ControlPoint> cps = new ArrayList<>();
        List<AuditItemResult> res = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ControlPoint c = cp(ch, ControlLevel.MAJOR_MUST);
            cps.add(c);
            res.add(result(c, ComplianceResult.COMPLIANT));
        }
        for (int i = 0; i < 20; i++) {
            ControlPoint c = cp(ch, ControlLevel.MINOR_MUST);
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
        Audit audit = globalgapAudit();
        StandardChapter ch = chapter(audit.getStandard(), "MGMT");
        ControlPoint major = cp(ch, ControlLevel.MAJOR_MUST);
        ControlPoint minor = cp(ch, ControlLevel.MINOR_MUST);
        when(catalog.controlPointsOf("GLOBALGAP_IFA_V6")).thenReturn(List.of(major, minor));
        when(results.findByAuditId(1L)).thenReturn(List.of(
                result(major, ComplianceResult.NON_COMPLIANT),
                result(minor, ComplianceResult.COMPLIANT)));

        ScoreResult score = scoringService.score(audit);

        assertThat(score.overallResult()).isEqualTo("FAIL");
    }

    @Test
    void pendingWhenMandatoryPointsRemainUnassessed() {
        Audit audit = globalgapAudit();
        StandardChapter ch = chapter(audit.getStandard(), "MGMT");
        ControlPoint major = cp(ch, ControlLevel.MAJOR_MUST);
        when(catalog.controlPointsOf("GLOBALGAP_IFA_V6")).thenReturn(List.of(major));
        when(results.findByAuditId(1L)).thenReturn(List.of());

        ScoreResult score = scoringService.score(audit);

        assertThat(score.overallResult()).isEqualTo("PENDING");
        assertThat(score.progressPct()).isZero();
    }

    @Test
    void smetaIsReportOnlyAndGroupsByPillar() {
        AuditStandard standard = new AuditStandard();
        standard.setId(30L);
        standard.setCode("SMETA_6_1");
        standard.setName("SMETA");
        standard.setCategory(StandardCategory.SOCIAL);
        standard.setScoringRule("{\"type\":\"PILLAR\",\"reportOnly\":true}");
        Audit audit = new Audit();
        audit.setId(2L);
        audit.setStandard(standard);

        StandardChapter ch = chapter(standard, "LABOUR");
        ControlPoint c1 = cp(ch, ControlLevel.MAJOR_MUST);
        c1.setApplicableScopes("LABOUR");
        ControlPoint c2 = cp(ch, ControlLevel.MAJOR_MUST);
        c2.setApplicableScopes("HEALTH_SAFETY");

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

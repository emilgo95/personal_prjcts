package com.agriaudit.audit.application;

import com.agriaudit.audit.ScoreDtos.ScoreResult;
import com.agriaudit.audit.domain.Audit;
import com.agriaudit.audit.infrastructure.persistence.AuditItemResultRepository;
import com.agriaudit.catalog.CatalogLookup;
import com.agriaudit.catalog.StandardView;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScoringRuleTest {

    private ScoreResult scoreWith(String rule) {
        CatalogLookup catalog = mock(CatalogLookup.class);
        AuditItemResultRepository results = mock(AuditItemResultRepository.class);
        when(catalog.findStandard(org.mockito.ArgumentMatchers.anyLong())).thenReturn(Optional.of(new StandardView(1L, "X", "v1", "Estándar X", rule)));
        when(catalog.controlPointsOf(anyString())).thenReturn(List.of());
        when(results.findByAuditId(any())).thenReturn(List.of());
        Audit audit = new Audit();
        audit.setStandardId(1L);
        return new ScoringService(catalog, results, JsonMapper.builder().build()).score(audit);
    }

    @Test
    void unaReglaIlegibleNuncaDaPorApto() {
        assertThat(scoreWith("{esto no es json").overallResult()).isEqualTo("PENDING");
        assertThat(scoreWith(null).overallResult()).isEqualTo("PENDING");
        assertThat(scoreWith("[1,2]").overallResult()).isEqualTo("PENDING");
        assertThat(scoreWith("{\"type\":\"MAJOR_MINOR\"}").overallResult()).isEqualTo("PENDING");
    }

    @Test
    void unaReglaValidaSiCalcula() {
        ScoreResult r = scoreWith("{\"type\":\"MAJOR_MINOR\",\"majorMustMinPct\":100,\"minorMustMinPct\":95}");

        assertThat(r.overallResult()).isEqualTo("PASS");
        assertThat(r.notes()).anyMatch(n -> n.contains("catálogo de demostración"));
    }
}

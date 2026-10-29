package com.agriaudit.findings.application;

import com.agriaudit.catalog.ClosureRule;
import com.agriaudit.findings.ClosureClock;
import com.agriaudit.findings.FindingLookup.DeadlineSummary;
import com.agriaudit.findings.domain.Finding;
import com.agriaudit.findings.domain.FindingClassification;
import com.agriaudit.findings.domain.FindingStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Reloj de cierre de NC (RF-301, RF-308): fecha límite, cierre en plazo y resumen del panel. */
class ClosureClockTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 29);
    long ids = 1;

    @Test
    void dueDateCountsDaysFromTheAuditDate() {
        assertThat(ClosureClock.dueDate(LocalDate.of(2026, 10, 1), 28, TODAY)).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(ClosureClock.dueDate(null, 28, TODAY)).isEqualTo(LocalDate.of(2026, 11, 26));
        assertThat(ClosureClock.dueDate(LocalDate.of(2026, 10, 1), null, TODAY)).isNull();
    }

    @Test
    void defaultRuleIs28DaysExceptObservations() {
        assertThat(ClosureRule.DEFAULT.daysFor("CRITICAL")).isEqualTo(28);
        assertThat(ClosureRule.DEFAULT.daysFor("MAJOR")).isEqualTo(28);
        assertThat(ClosureRule.DEFAULT.daysFor("MINOR")).isEqualTo(28);
        assertThat(ClosureRule.DEFAULT.daysFor("OBSERVATION")).isNull();
        assertThat(ClosureRule.DEFAULT.daysFor("OTRA")).isNull();
    }

    @Test
    void closingOnTheDueDateCountsAsOnTime() {
        LocalDate due = LocalDate.of(2026, 10, 20);
        assertThat(ClosureClock.closedOnTime(at(LocalDate.of(2026, 10, 20), 23), due)).isTrue();
        assertThat(ClosureClock.closedOnTime(at(LocalDate.of(2026, 10, 21), 0), due)).isFalse();
    }

    @Test
    void closedAtFollowsTheStatus() {
        Finding f = new Finding();
        f.setStatus(FindingStatus.IN_PROGRESS);
        assertThat(f.getClosedAt()).isNull();
        f.setStatus(FindingStatus.CLOSED);
        Instant closed = f.getClosedAt();
        assertThat(closed).isNotNull();
        f.setStatus(FindingStatus.VERIFIED);
        assertThat(f.getClosedAt()).isEqualTo(closed);
        f.setStatus(FindingStatus.OPEN);
        assertThat(f.getClosedAt()).isNull();
    }

    @Test
    void summaryCountsAlertsCumulativelyAndTheOnTimeIndicator() {
        List<Finding> all = new ArrayList<>();
        all.add(open(TODAY.minusDays(3)));          // vencida
        all.add(open(TODAY));                       // vence hoy: 2, 7 y 14
        all.add(open(TODAY.plusDays(2)));           // 2, 7 y 14
        all.add(open(TODAY.plusDays(6)));           // 7 y 14
        all.add(open(TODAY.plusDays(14)));          // 14
        all.add(open(TODAY.plusDays(30)));          // con margen
        all.add(open(null));                        // abierta sin plazo (observación)
        all.add(closed(TODAY.minusDays(10), TODAY.minusDays(12)));   // a tiempo
        all.add(closed(TODAY.minusDays(10), TODAY.minusDays(10)));   // a tiempo, el mismo día
        all.add(closed(TODAY.minusDays(10), TODAY.minusDays(4)));    // tarde
        all.add(closed(TODAY.minusYears(2), TODAY.minusYears(2)));   // hace más de 12 meses: no cuenta

        DeadlineSummary s = FindingService.summarize(all, TODAY, 3);

        assertThat(s.open()).isEqualTo(7);
        assertThat(s.overdue()).isEqualTo(1);
        assertThat(s.dueIn2()).isEqualTo(2);
        assertThat(s.dueIn7()).isEqualTo(3);
        assertThat(s.dueIn14()).isEqualTo(4);
        assertThat(s.closedWithDeadline()).isEqualTo(3);
        assertThat(s.closedOnTime()).isEqualTo(2);
        assertThat(s.next()).extracting(d -> d.dueDate())
                .containsExactly(TODAY.minusDays(3), TODAY, TODAY.plusDays(2));
    }

    private Finding open(LocalDate due) {
        Finding f = finding(due);
        f.setStatus(FindingStatus.OPEN);
        return f;
    }

    private Finding closed(LocalDate due, LocalDate closedOn) {
        Finding f = finding(due);
        f.setStatus(FindingStatus.CLOSED);
        f.setClosedAt(at(closedOn, 12));
        return f;
    }

    private Finding finding(LocalDate due) {
        Finding f = new Finding();
        f.setId(ids++);
        f.setAuditId(1L);
        f.setClassification(due == null ? FindingClassification.OBSERVATION : FindingClassification.MAJOR);
        f.setDueDate(due);
        return f;
    }

    private static Instant at(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(ClosureClock.ZONE).toInstant();
    }
}

package com.agriaudit.dashboard.application;

import com.agriaudit.audit.AuditDtos.AuditSummaryDto;
import com.agriaudit.audit.AuditLookup;
import com.agriaudit.dashboard.application.DashboardDto.DeadlineDto;
import com.agriaudit.documents.DocumentLookup;
import com.agriaudit.fieldbook.FarmDirectory;
import com.agriaudit.findings.FindingLookup;
import com.agriaudit.findings.ClosureClock;
import com.agriaudit.identity.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    static final int DEADLINES_SHOWN = 6;

    private final AuditLookup auditService;
    private final FarmDirectory farms;
    private final FindingLookup findings;
    private final DocumentLookup documents;

    public DashboardService(AuditLookup auditService, FarmDirectory farms,
                            FindingLookup findings, DocumentLookup documents) {
        this.auditService = auditService;
        this.farms = farms;
        this.findings = findings;
        this.documents = documents;
    }

    public DashboardDto load() {
        Long orgId = SecurityUtils.currentOrganizationId();
        LocalDate today = ClosureClock.today();
        Map<String, Long> byStatus = auditService.countByStatus();
        long totalAudits = byStatus.values().stream().mapToLong(Long::longValue).sum();

        FindingLookup.DeadlineSummary clock = findings.deadlineSummary(orgId, today, DEADLINES_SHOWN);
        Map<Long, AuditSummaryDto> auditsById = auditService.summaries(
                clock.next().stream().map(d -> d.auditId()).distinct().toList());
        List<DeadlineDto> deadlines = clock.next().stream()
                .map(d -> {
                    AuditSummaryDto audit = auditsById.get(d.auditId());
                    return new DeadlineDto(d.findingId(), d.auditId(),
                            audit == null ? null : audit.reference(),
                            audit == null ? null : audit.farmName(),
                            d.reference(), d.title(), d.classification(), d.status(), d.dueDate(),
                            ChronoUnit.DAYS.between(today, d.dueDate()));
                })
                .toList();

        return new DashboardDto(
                farms.countFarms(orgId),
                totalAudits,
                byStatus,
                clock.open(),
                clock.overdue(),
                clock.dueIn7(),
                clock.dueIn2(),
                clock.dueIn14(),
                clock.closedWithDeadline(),
                clock.closedOnTime(),
                documents.countGenerated(orgId),
                deadlines,
                auditService.recent(5));
    }
}

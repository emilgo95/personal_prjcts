package com.agriaudit.dashboard.application;

import com.agriaudit.audit.AuditDtos.AuditSummaryDto;
import com.agriaudit.audit.AuditLookup;
import com.agriaudit.dashboard.application.DashboardDto.DeadlineDto;
import com.agriaudit.documents.DocumentLookup;
import com.agriaudit.fieldbook.FarmDirectory;
import com.agriaudit.findings.FindingLookup;
import com.agriaudit.identity.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    /** Una NC «vence pronto» si su fecha límite cae en los próximos 7 días. */
    static final int DUE_SOON_DAYS = 7;
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
        LocalDate today = LocalDate.now();
        List<AuditSummaryDto> audits = auditService.list();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (AuditSummaryDto a : audits) {
            byStatus.merge(a.status(), 1L, Long::sum);
        }

        Map<Long, AuditSummaryDto> auditsById = audits.stream()
                .collect(Collectors.toMap(AuditSummaryDto::id, Function.identity()));
        List<DeadlineDto> deadlines = findings.nextDeadlines(orgId, DEADLINES_SHOWN).stream()
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
                audits.size(),
                byStatus,
                findings.countOpen(orgId),
                findings.countOverdue(orgId, today),
                findings.countDueBetween(orgId, today, today.plusDays(DUE_SOON_DAYS)),
                documents.countGenerated(orgId),
                deadlines,
                audits.stream().limit(5).toList());
    }
}

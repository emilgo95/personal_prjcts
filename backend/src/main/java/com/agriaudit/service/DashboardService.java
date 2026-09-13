package com.agriaudit.service;

import com.agriaudit.fieldbook.FarmDirectory;
import com.agriaudit.findings.FindingLookup;
import com.agriaudit.documents.DocumentLookup;
import com.agriaudit.identity.SecurityUtils;
import com.agriaudit.audit.AuditDtos.AuditSummaryDto;
import com.agriaudit.web.dto.DashboardDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.agriaudit.audit.AuditLookup;

@Service
@Transactional(readOnly = true)
public class DashboardService {

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
        List<AuditSummaryDto> audits = auditService.list();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (AuditSummaryDto a : audits) {
            byStatus.merge(a.status(), 1L, Long::sum);
        }

        long open = findings.countOpen(orgId);
        long overdue = findings.countOverdue(orgId, LocalDate.now());

        List<AuditSummaryDto> recent = audits.stream().limit(5).toList();

        return new DashboardDto(
                farms.countFarms(orgId),
                audits.size(),
                byStatus,
                open,
                overdue,
                documents.countGenerated(orgId),
                recent);
    }
}

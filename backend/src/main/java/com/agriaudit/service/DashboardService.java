package com.agriaudit.service;

import com.agriaudit.domain.Finding;
import com.agriaudit.domain.enums.FindingStatus;
import com.agriaudit.fieldbook.FarmDirectory;
import com.agriaudit.repo.FindingRepository;
import com.agriaudit.repo.GeneratedDocumentRepository;
import com.agriaudit.identity.SecurityUtils;
import com.agriaudit.web.dto.AuditDtos.AuditSummaryDto;
import com.agriaudit.web.dto.DashboardDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final AuditService auditService;
    private final FarmDirectory farms;
    private final FindingRepository findings;
    private final GeneratedDocumentRepository generatedDocuments;

    public DashboardService(AuditService auditService, FarmDirectory farms,
                            FindingRepository findings, GeneratedDocumentRepository generatedDocuments) {
        this.auditService = auditService;
        this.farms = farms;
        this.findings = findings;
        this.generatedDocuments = generatedDocuments;
    }

    public DashboardDto load() {
        Long orgId = SecurityUtils.currentOrganizationId();
        List<AuditSummaryDto> audits = auditService.list();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (AuditSummaryDto a : audits) {
            byStatus.merge(a.status(), 1L, Long::sum);
        }

        List<Finding> orgFindings = findings.findByOrganizationId(orgId);
        LocalDate today = LocalDate.now();
        long open = orgFindings.stream()
                .filter(f -> f.getStatus() != FindingStatus.CLOSED && f.getStatus() != FindingStatus.VERIFIED)
                .count();
        long overdue = orgFindings.stream()
                .filter(f -> f.getStatus() != FindingStatus.CLOSED && f.getStatus() != FindingStatus.VERIFIED)
                .filter(f -> f.getDueDate() != null && f.getDueDate().isBefore(today))
                .count();

        List<AuditSummaryDto> recent = audits.stream().limit(5).toList();

        return new DashboardDto(
                farms.countFarms(orgId),
                audits.size(),
                byStatus,
                open,
                overdue,
                generatedDocuments.findByOrganizationIdOrderByGeneratedAtDesc(orgId).size(),
                recent);
    }
}

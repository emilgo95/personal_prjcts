package com.agriaudit.dashboard.application;

import com.agriaudit.audit.AuditDtos.AuditSummaryDto;

import java.util.List;
import java.util.Map;

public record DashboardDto(
        long farmsCount,
        long auditsTotal,
        Map<String, Long> auditsByStatus,
        long openFindings,
        long overdueFindings,
        long documentsGenerated,
        List<AuditSummaryDto> recentAudits) {
}

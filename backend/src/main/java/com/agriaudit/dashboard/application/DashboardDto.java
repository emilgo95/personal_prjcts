package com.agriaudit.dashboard.application;

import com.agriaudit.audit.AuditDtos.AuditSummaryDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DashboardDto(
        long farmsCount,
        long auditsTotal,
        Map<String, Long> auditsByStatus,
        long openFindings,
        long overdueFindings,
        long dueSoonFindings,
        long dueIn2Findings,
        long dueIn14Findings,
        long closedWithDeadlineFindings,
        long closedOnTimeFindings,
        long documentsGenerated,
        List<DeadlineDto> upcomingDeadlines,
        List<AuditSummaryDto> recentAudits) {

    /**
     * Vencimiento de una no conformidad abierta. {@code daysLeft} es negativo si ya venció y 0 si vence hoy.
     */
    public record DeadlineDto(
            Long findingId,
            Long auditId,
            String auditReference,
            String farmName,
            String findingReference,
            String title,
            String classification,
            String status,
            LocalDate dueDate,
            long daysLeft) {
    }
}

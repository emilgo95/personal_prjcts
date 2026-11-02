package com.agriaudit.findings.application;

import com.agriaudit.audit.AuditDtos.AuditSummaryDto;
import com.agriaudit.audit.AuditLookup;
import com.agriaudit.audit.AuditRef;
import com.agriaudit.catalog.CatalogLookup;
import com.agriaudit.catalog.ControlPointView;
import com.agriaudit.common.BadRequestException;
import com.agriaudit.findings.ClosureClock;
import com.agriaudit.findings.FindingDtos.FindingListDto;
import com.agriaudit.findings.FindingDtos.FindingRowDto;
import com.agriaudit.findings.domain.Finding;
import com.agriaudit.findings.domain.FindingStatus;
import com.agriaudit.findings.infrastructure.persistence.FindingRepository;
import com.agriaudit.identity.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Todas las no conformidades de la organización, en una sola lista con vistas («abiertas», «vencidas», «vencen en 7
 * días», «cerradas en 12 meses»…). Las vistas siguen exactamente las reglas de las cifras del panel
 * ({@link FindingService#summarize}), para que al pulsar una cifra salgan las mismas NC que cuenta.
 */
@Service
@Transactional(readOnly = true)
public class FindingListService {

    /** Vistas de la lista, en el orden en que se muestran. */
    public static final List<String> VIEWS =
            List.of("OPEN", "OVERDUE", "DUE_2", "DUE_7", "DUE_14", "CLOSED_12M", "CLOSED_LATE", "ALL");

    private final FindingRepository findings;
    private final AuditLookup audits;
    private final CatalogLookup catalog;

    public FindingListService(FindingRepository findings, AuditLookup audits, CatalogLookup catalog) {
        this.findings = findings;
        this.audits = audits;
        this.catalog = catalog;
    }

    public FindingListDto list(String requestedView) {
        String view = requestedView == null || requestedView.isBlank() ? "OPEN"
                : requestedView.trim().toUpperCase(Locale.ROOT);
        if (!VIEWS.contains(view)) {
            throw new BadRequestException("Vista no válida: " + requestedView + ". Vistas: " + String.join(", ", VIEWS));
        }
        Long orgId = SecurityUtils.currentOrganizationId();
        List<Long> auditIds = audits.findByOrganization(orgId).stream().map(AuditRef::id).toList();
        List<Finding> all = auditIds.isEmpty() ? List.of() : findings.findByAuditIdIn(auditIds);
        LocalDate today = ClosureClock.today();
        Instant closedSince = today.minusYears(1).atStartOfDay(ClosureClock.ZONE).toInstant();

        Map<String, Long> counts = new LinkedHashMap<>();
        for (String v : VIEWS) {
            Predicate<Finding> p = predicate(v, today, closedSince);
            counts.put(v, all.stream().filter(p).count());
        }

        List<Finding> selected = all.stream().filter(predicate(view, today, closedSince)).sorted(order()).toList();
        Map<Long, AuditSummaryDto> auditById = audits.summaries(selected.stream().map(Finding::getAuditId).distinct().toList());
        Map<Long, ControlPointView> cps = catalog.findControlPoints(selected.stream()
                .map(Finding::getControlPointId).filter(Objects::nonNull).distinct().toList());
        List<FindingRowDto> rows = selected.stream().map(f -> {
            AuditSummaryDto a = auditById.get(f.getAuditId());
            ControlPointView cp = f.getControlPointId() == null ? null : cps.get(f.getControlPointId());
            boolean open = isOpen(f);
            return new FindingRowDto(f.getId(), f.getAuditId(),
                    a == null ? null : a.reference(), a == null ? null : a.status(),
                    a == null ? null : a.farmId(), a == null ? null : a.farmName(),
                    a == null ? null : a.standardCode(), a == null ? null : a.standardName(),
                    cp == null ? null : cp.ref(), f.getReference(), f.getTitle(), f.getClassification().name(),
                    f.getStatus().name(), f.getDueDate(),
                    open && f.getDueDate() != null ? ChronoUnit.DAYS.between(today, f.getDueDate()) : null,
                    f.getClosedAt(),
                    !open && f.getDueDate() != null && f.getClosedAt() != null
                            ? ClosureClock.closedOnTime(f.getClosedAt(), f.getDueDate()) : null,
                    f.getLateJustification());
        }).toList();
        return new FindingListDto(view, counts, rows);
    }

    /** Las mismas reglas que las cifras del panel: «vencen en N días» incluye las que vencen hoy. */
    static Predicate<Finding> predicate(String view, LocalDate today, Instant closedSince) {
        return switch (view) {
            case "OPEN" -> FindingListService::isOpen;
            case "OVERDUE" -> f -> isOpen(f) && days(f, today) != null && days(f, today) < 0;
            case "DUE_2" -> f -> isOpen(f) && within(days(f, today), ClosureClock.WARN_2);
            case "DUE_7" -> f -> isOpen(f) && within(days(f, today), ClosureClock.WARN_7);
            case "DUE_14" -> f -> isOpen(f) && within(days(f, today), ClosureClock.WARN_14);
            case "CLOSED_12M" -> f -> closedWithDeadline(f, closedSince);
            case "CLOSED_LATE" -> f -> closedWithDeadline(f, closedSince)
                    && !ClosureClock.closedOnTime(f.getClosedAt(), f.getDueDate());
            default -> f -> true;
        };
    }

    /** Abiertas primero, de la más urgente a la menos (sin plazo al final); después las cerradas, la más reciente antes. */
    private static Comparator<Finding> order() {
        return Comparator.comparing((Finding f) -> isOpen(f) ? 0 : 1)
                .thenComparing(f -> isOpen(f) ? f.getDueDate() : null, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(f -> isOpen(f) ? null : f.getClosedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Finding::getId);
    }

    private static boolean isOpen(Finding f) {
        return f.getStatus() != FindingStatus.CLOSED && f.getStatus() != FindingStatus.VERIFIED;
    }

    private static Long days(Finding f, LocalDate today) {
        return f.getDueDate() == null ? null : ChronoUnit.DAYS.between(today, f.getDueDate());
    }

    private static boolean within(Long days, int limit) {
        return days != null && days >= 0 && days <= limit;
    }

    private static boolean closedWithDeadline(Finding f, Instant closedSince) {
        return !isOpen(f) && f.getDueDate() != null && f.getClosedAt() != null && !f.getClosedAt().isBefore(closedSince);
    }
}

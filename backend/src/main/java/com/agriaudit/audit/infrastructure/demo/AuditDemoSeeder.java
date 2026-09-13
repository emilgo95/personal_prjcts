package com.agriaudit.audit.infrastructure.demo;

import com.agriaudit.audit.AuditStatus;
import com.agriaudit.audit.DemoAuditSeeded;
import com.agriaudit.audit.AuditRef;
import com.agriaudit.audit.domain.Audit;
import com.agriaudit.audit.domain.AuditItemResult;
import com.agriaudit.audit.domain.AuditType;
import com.agriaudit.audit.domain.ComplianceResult;
import com.agriaudit.audit.infrastructure.persistence.AuditItemResultRepository;
import com.agriaudit.audit.infrastructure.persistence.AuditRepository;
import com.agriaudit.catalog.CatalogLookup;
import com.agriaudit.catalog.domain.AuditStandard;
import com.agriaudit.catalog.domain.ControlPoint;
import com.agriaudit.fieldbook.DemoFarmsSeeded;
import com.agriaudit.fieldbook.FarmDtos.FarmDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Auditorías de demostración en distintos estados, con resultados de checklist, sobre las explotaciones
 * recién sembradas ({@link DemoFarmsSeeded}). Por cada una publica {@link DemoAuditSeeded} para que
 * {@code findings} siembre sus no conformidades.
 */
@Component
class AuditDemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(AuditDemoSeeder.class);

    private final AuditRepository audits;
    private final AuditItemResultRepository results;
    private final CatalogLookup catalog;
    private final ApplicationEventPublisher events;

    AuditDemoSeeder(AuditRepository audits, AuditItemResultRepository results, CatalogLookup catalog,
                    ApplicationEventPublisher events) {
        this.audits = audits;
        this.results = results;
        this.catalog = catalog;
        this.events = events;
    }

    @EventListener
    @Transactional
    public void on(DemoFarmsSeeded event) {
        Long orgId = event.organizationId();
        List<FarmDto> f = event.farms();
        if (f.size() < 5) {
            log.warn("Se esperaban 5 explotaciones de demostración y hay {}; se omiten las auditorías", f.size());
            return;
        }
        int seq = 1;
        seq = audit(orgId, seq, f.get(0), "GLOBALGAP_IFA_V6", AuditType.CB_INITIAL, AuditStatus.CLOSED,
                LocalDate.of(2026, 3, 10), "Ana Auditora", "Ana Auditora, Jordi Pons",
                "Certificación inicial de fresa y frambuesa para exportación.");
        seq = audit(orgId, seq, f.get(0), "GRASP_V2", AuditType.SURVEILLANCE, AuditStatus.SUBMITTED,
                LocalDate.of(2026, 3, 11), "Ana Auditora", "Ana Auditora",
                "Evaluación social GRASP asociada a la certificación GLOBALG.A.P.");
        seq = audit(orgId, seq, f.get(1), "GLOBALGAP_IFA_V6", AuditType.RECERTIFICATION, AuditStatus.CLOSING,
                LocalDate.of(2026, 6, 15), "Jordi Pons", "Jordi Pons, Ana Auditora",
                "Recertificación anual de cítricos. Pendiente de cierre de no conformidades.");
        seq = audit(orgId, seq, f.get(1), "SMETA_6_1", AuditType.INTERNAL, AuditStatus.IN_PROGRESS,
                LocalDate.of(2026, 7, 20), "Ana Auditora", "Ana Auditora",
                "Auditoría ética interna previa a la auditoría SMETA de cliente.");
        seq = audit(orgId, seq, f.get(2), "GLOBALGAP_IFA_V6", AuditType.SURVEILLANCE, AuditStatus.IN_PROGRESS,
                LocalDate.of(2026, 8, 5), "Marco Díaz", "Marco Díaz, Rosa Quispe",
                "Auditoría de seguimiento de uva de mesa y arándano.");
        seq = audit(orgId, seq, f.get(2), "SMETA_6_1", AuditType.CB_INITIAL, AuditStatus.SUBMITTED,
                LocalDate.of(2026, 7, 1), "Marco Díaz", "Marco Díaz",
                "SMETA 4 pilares para incorporación a la plataforma Sedex.");
        seq = audit(orgId, seq, f.get(3), "GLOBALGAP_IFA_V6", AuditType.SELF_ASSESSMENT, AuditStatus.DRAFT,
                LocalDate.of(2026, 8, 25), "Pieter van Wyk", "Pieter van Wyk",
                "Autoevaluación previa a la auditoría del organismo de certificación.");
        audit(orgId, seq, f.get(4), "GRASP_V2", AuditType.SELF_ASSESSMENT, AuditStatus.IN_PROGRESS,
                LocalDate.of(2026, 8, 18), "Khadija Amrani", "Khadija Amrani",
                "Autoevaluación social de la cooperativa.");
        log.info("Datos de demostración: {} auditorías", audits.countByOrganizationId(orgId));
    }

    private int audit(Long orgId, int seq, FarmDto farm, String standardCode, AuditType type, AuditStatus status,
                      LocalDate date, String leadAuditor, String team, String scope) {
        AuditStandard standard = catalog.findStandard(standardCode).orElse(null);
        List<ControlPoint> cps = catalog.controlPointsOf(standardCode);
        if (standard == null || cps.isEmpty()) {
            log.warn("Estándar {} sin puntos de control; se omite la auditoría de demo", standardCode);
            return seq + 1;
        }
        Audit a = new Audit();
        a.setOrganizationId(orgId);
        a.setFarmId(farm.id());
        a.setStandard(standard);
        a.setReference(String.format("AUD-%d-%04d", date.getYear(), seq));
        a.setAuditType(type);
        a.setStatus(status);
        a.setScopeDescription(scope);
        a.setActualDate(status == AuditStatus.DRAFT ? null : date);
        a.setPlannedStartDate(date);
        a.setPlannedEndDate(date.plusDays(1));
        a.setLeadAuditor(leadAuditor);
        a.setAuditTeam(team);
        a.setSiteAddress(farm.address() + ", " + farm.city());
        a.setProductsInScope(farm.crops());
        a = audits.save(a);

        List<Long> nonCompliant = seedResults(a, cps, status, date, leadAuditor);
        events.publishEvent(new DemoAuditSeeded(new AuditRef(a.getId(), orgId, a.getReference(), standardCode, status,
                date, leadAuditor), nonCompliant));
        return seq + 1;
    }

    /** Siembra el checklist y devuelve los puntos de control no conformes, en orden. */
    private List<Long> seedResults(Audit audit, List<ControlPoint> cps, AuditStatus status, LocalDate date,
                                   String leadAuditor) {
        double coverage = switch (status) {
            case DRAFT -> 0.12;
            case IN_PROGRESS -> 0.65;
            default -> 1.0;
        };
        int toAssess = (int) Math.round(cps.size() * coverage);
        Instant assessedAt = date.atStartOfDay().toInstant(ZoneOffset.UTC);

        List<Long> nonCompliant = new ArrayList<>();
        for (int i = 0; i < cps.size(); i++) {
            ControlPoint cp = cps.get(i);
            ComplianceResult compliance;
            if (i >= toAssess) {
                compliance = ComplianceResult.NOT_ASSESSED;
            } else if (i % 9 == 4) {
                compliance = ComplianceResult.NON_COMPLIANT;
            } else if (i % 13 == 7) {
                compliance = ComplianceResult.NOT_APPLICABLE;
            } else {
                compliance = ComplianceResult.COMPLIANT;
            }

            AuditItemResult r = new AuditItemResult();
            r.setAuditId(audit.getId());
            r.setControlPoint(cp);
            r.setCompliance(compliance);
            if (compliance != ComplianceResult.NOT_ASSESSED) {
                r.setAssessedAt(assessedAt);
                r.setAssessedBy(leadAuditor);
                r.setEvidenceNotes(switch (compliance) {
                    case COMPLIANT -> "Verificado durante la inspección; registros disponibles y actualizados.";
                    case NON_COMPLIANT -> "Se observa desviación respecto al criterio; ver no conformidad asociada.";
                    case NOT_APPLICABLE -> "No aplica al alcance de esta explotación.";
                    default -> null;
                });
            }
            results.save(r);
            if (compliance == ComplianceResult.NON_COMPLIANT) {
                nonCompliant.add(cp.getId());
            }
        }
        return nonCompliant;
    }
}

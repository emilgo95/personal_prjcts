package com.agriaudit.audit.infrastructure.demo;

import com.agriaudit.audit.AuditRef;
import com.agriaudit.audit.AuditStatus;
import com.agriaudit.audit.DemoAuditSeeded;
import com.agriaudit.audit.domain.Audit;
import com.agriaudit.audit.domain.AuditItemResult;
import com.agriaudit.audit.domain.AuditType;
import com.agriaudit.audit.domain.ComplianceResult;
import com.agriaudit.audit.infrastructure.persistence.AuditItemResultRepository;
import com.agriaudit.audit.infrastructure.persistence.AuditRepository;
import com.agriaudit.catalog.CatalogLookup;
import com.agriaudit.catalog.ControlPointView;
import com.agriaudit.catalog.StandardView;
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
 *
 * <p>Las fechas son <strong>relativas a hoy</strong>: así, cada vez que se siembra la demo el panel muestra
 * plazos creíbles (algún vencimiento a punto, alguna no conformidad vencida) sea cual sea el día.
 */
@Component
class AuditDemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(AuditDemoSeeder.class);

    private static final String IFA = "GLOBALGAP_IFA_V6";
    private static final String GRASP = "GRASP_V2";
    private static final String SMETA = "SMETA_6_1";

    private static final String[] COMPLIANT_NOTES = {
            "Verificado durante la visita; registros disponibles y actualizados.",
            "Se revisa la documentación y se comprueba en campo; sin desviaciones.",
            "Evidencia documental completa y coherente con lo observado.",
            "Responsable entrevistado; conoce el procedimiento y lo aplica.",
    };

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

    /** Una auditoría de demo: quién, qué norma, en qué estado y a cuántos días de hoy. */
    private record Plan(int farm, String standard, AuditType type, AuditStatus status, int daysAgo,
                        double coverage, String lead, String team, String scope) {
    }

    @EventListener
    @Transactional
    public void on(DemoFarmsSeeded event) {
        Long orgId = event.organizationId();
        List<FarmDto> f = event.farms();
        if (f.size() < 8) {
            log.warn("Se esperaban 8 explotaciones de demostración y hay {}; se omiten las auditorías", f.size());
            return;
        }
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        // Orden de creación: primero lo cerrado y lo enviado, después lo que está vivo, así el panel
        // muestra arriba (las más recientes) las auditorías en curso.
        List<Plan> plans = List.of(
                new Plan(5, IFA, AuditType.CB_INITIAL, AuditStatus.CLOSED, 170, 1.0, "Ana Auditora",
                        "Ana Auditora, Marcos Soler",
                        "Certificación inicial GLOBALG.A.P. de uva de mesa sin semilla en tres fincas de Cehegín."),
                new Plan(5, GRASP, AuditType.CB_INITIAL, AuditStatus.CLOSED, 169, 1.0, "Ana Auditora",
                        "Ana Auditora",
                        "Evaluación social GRASP de la plantilla de campo y del alojamiento de temporeros."),
                new Plan(7, SMETA, AuditType.CB_INITIAL, AuditStatus.CLOSED, 120, 1.0, "Elena Cárceles",
                        "Elena Cárceles, Ana Auditora",
                        "SMETA de 4 pilares para la incorporación de un nuevo cliente de gran distribución."),
                new Plan(6, IFA, AuditType.RECERTIFICATION, AuditStatus.SUBMITTED, 45, 1.0, "Marcos Soler",
                        "Marcos Soler, Ana Auditora",
                        "Recertificación anual de limón, pomelo y naranja. Expediente enviado al organismo de certificación."),
                new Plan(1, IFA, AuditType.RECERTIFICATION, AuditStatus.CLOSING, 25, 1.0, "Ana Auditora",
                        "Ana Auditora, Marcos Soler",
                        "Recertificación anual de pimiento, tomate y calabacín. Pendiente de cierre de no conformidades."),
                new Plan(7, IFA, AuditType.SURVEILLANCE, AuditStatus.CLOSING, 22, 1.0, "Elena Cárceles",
                        "Elena Cárceles",
                        "Auditoría de seguimiento de brócoli, coliflor y romanesco con acciones correctivas en marcha."),
                new Plan(0, IFA, AuditType.SURVEILLANCE, AuditStatus.IN_PROGRESS, 31, 0.80, "Ana Auditora",
                        "Ana Auditora",
                        "Auditoría de seguimiento de lechuga, brócoli y apio en Molina de Segura, Fortuna y Abanilla."),
                new Plan(3, IFA, AuditType.SURVEILLANCE, AuditStatus.IN_PROGRESS, 14, 0.65, "Marcos Soler",
                        "Marcos Soler, Elena Cárceles",
                        "Auditoría de seguimiento de melón, sandía y lechuga iceberg, con confección propia."),
                new Plan(3, SMETA, AuditType.INTERNAL, AuditStatus.IN_PROGRESS, 9, 0.55, "Ana Auditora",
                        "Ana Auditora",
                        "Auditoría ética interna previa a la auditoría SMETA del cliente."),
                new Plan(4, IFA, AuditType.SELF_ASSESSMENT, AuditStatus.IN_PROGRESS, 3, 0.35, "Ana Auditora",
                        "Ana Auditora",
                        "Autoevaluación de una explotación 100 % ecológica antes de la auditoría del organismo."),
                new Plan(0, GRASP, AuditType.SELF_ASSESSMENT, AuditStatus.DRAFT, -12, 0.0, "Ana Auditora",
                        "Ana Auditora",
                        "Autoevaluación social GRASP prevista para el próximo mes."),
                new Plan(2, IFA, AuditType.CB_INITIAL, AuditStatus.DRAFT, -25, 0.0, "Marcos Soler",
                        "Marcos Soler",
                        "Preparación de la certificación inicial de una central de fruta de hueso."));

        int seq = 1;
        for (Plan p : plans) {
            audit(orgId, year, seq++, f.get(p.farm()), p, today.minusDays(p.daysAgo()));
        }
        log.info("Datos de demostración: {} auditorías", audits.countByOrganizationId(orgId));
    }

    private void audit(Long orgId, int year, int seq, FarmDto farm, Plan p, LocalDate date) {
        StandardView standard = catalog.findStandard(p.standard()).orElse(null);
        List<ControlPointView> cps = catalog.controlPointsOf(p.standard());
        if (standard == null || cps.isEmpty()) {
            log.warn("Estándar {} sin puntos de control; se omite la auditoría de demo", p.standard());
            return;
        }
        Audit a = new Audit();
        a.setOrganizationId(orgId);
        a.setFarmId(farm.id());
        a.setStandardId(standard.id());
        a.setReference(String.format("AUD-%d-%04d", year, seq));
        a.setAuditType(p.type());
        a.setStatus(p.status());
        a.setScopeDescription(p.scope());
        a.setActualDate(p.status() == AuditStatus.DRAFT ? null : date);
        a.setPlannedStartDate(date);
        a.setPlannedEndDate(date.plusDays(1));
        a.setLeadAuditor(p.lead());
        a.setAuditTeam(p.team());
        a.setSiteAddress(farm.address() + ", " + farm.city());
        a.setProductsInScope(farm.crops());
        a = audits.save(a);

        List<Long> nonCompliant = seedResults(a, cps, p, date, seq);
        events.publishEvent(new DemoAuditSeeded(new AuditRef(a.getId(), orgId, a.getReference(), p.standard(),
                p.status(), date, p.lead(), a.getActualDate()), nonCompliant));
    }

    /**
     * Siembra el checklist y devuelve los puntos de control de las no conformidades, en orden. En las
     * auditorías cerradas o enviadas esos puntos ya están corregidos y verificados, por lo que constan
     * como conformes y el resultado es «apto»; en las demás siguen como no conformes.
     */
    private List<Long> seedResults(Audit audit, List<ControlPointView> cps, Plan p, LocalDate date, int seq) {
        int toAssess = (int) Math.round(cps.size() * p.coverage());
        boolean corrected = p.status() == AuditStatus.CLOSED || p.status() == AuditStatus.SUBMITTED;
        Instant assessedAt = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        int ncOffset = 3 + (seq % 4); // desplaza las no conformidades para que no coincidan entre auditorías

        List<Long> nonCompliant = new ArrayList<>();
        for (int i = 0; i < cps.size(); i++) {
            ControlPointView cp = cps.get(i);
            boolean isFinding = i < toAssess && i % 9 == ncOffset;
            ComplianceResult compliance;
            if (i >= toAssess) {
                compliance = ComplianceResult.NOT_ASSESSED;
            } else if (isFinding) {
                compliance = corrected ? ComplianceResult.COMPLIANT : ComplianceResult.NON_COMPLIANT;
            } else if (i % 13 == 7) {
                compliance = ComplianceResult.NOT_APPLICABLE;
            } else {
                compliance = ComplianceResult.COMPLIANT;
            }

            AuditItemResult r = new AuditItemResult();
            r.setAuditId(audit.getId());
            r.setControlPointId(cp.id());
            r.setCompliance(compliance);
            if (compliance != ComplianceResult.NOT_ASSESSED) {
                r.setAssessedAt(assessedAt);
                r.setAssessedBy(p.lead());
                r.setEvidenceNotes(notesFor(compliance, isFinding, corrected, i));
            }
            results.save(r);
            if (isFinding) {
                nonCompliant.add(cp.id());
            }
        }
        return nonCompliant;
    }

    private static String notesFor(ComplianceResult compliance, boolean isFinding, boolean corrected, int index) {
        if (isFinding && corrected) {
            return "Desviación detectada en la auditoría; corregida y verificada por el auditor (ver no conformidad).";
        }
        return switch (compliance) {
            case COMPLIANT -> COMPLIANT_NOTES[index % COMPLIANT_NOTES.length];
            case NON_COMPLIANT -> "No se evidencia el cumplimiento del criterio; ver la no conformidad asociada.";
            case NOT_APPLICABLE -> "No aplica al alcance de esta explotación.";
            default -> null;
        };
    }
}

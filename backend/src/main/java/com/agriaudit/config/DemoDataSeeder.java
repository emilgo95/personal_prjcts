package com.agriaudit.config;

import com.agriaudit.domain.*;
import com.agriaudit.domain.enums.*;
import com.agriaudit.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import com.agriaudit.catalog.domain.AuditStandard;
import com.agriaudit.catalog.domain.ControlPoint;
import com.agriaudit.catalog.CatalogLookup;
import com.agriaudit.identity.Role;
import com.agriaudit.identity.UserProvisioning;
import com.agriaudit.identity.OrganizationDirectory;

/**
 * Carga un juego de datos ficticio pero coherente para demostraciones:
 * explotaciones de varios países, usuarios, auditorías en distintos estados
 * (borrador, en curso, cierre, enviada, cerrada) con resultados de checklist,
 * no conformidades y acciones correctivas.
 *
 * Idempotente: no hace nada si ya existen explotaciones.
 * Se puede desactivar con agriaudit.demo.seed=false (AGRIAUDIT_DEMO_SEED=false).
 */
@Component
@Order(20)
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final boolean enabled;
    private final String orgName;
    private final OrganizationDirectory organizations;
    private final UserProvisioning users;
    private final FarmRepository farms;
    private final AuditRepository audits;
    private final CatalogLookup catalog;
    private final AuditItemResultRepository results;
    private final FindingRepository findings;
    private final CorrectiveActionRepository correctiveActions;

    public DemoDataSeeder(@Value("${agriaudit.demo.seed:true}") boolean enabled,
                          @Value("${agriaudit.admin.org-name}") String orgName,
                          OrganizationDirectory organizations, UserProvisioning users, FarmRepository farms,
                          AuditRepository audits, CatalogLookup catalog,
                          AuditItemResultRepository results, FindingRepository findings,
                          CorrectiveActionRepository correctiveActions) {
        this.enabled = enabled;
        this.orgName = orgName;
        this.organizations = organizations;
        this.users = users;
        this.farms = farms;
        this.audits = audits;
        this.catalog = catalog;
        this.results = results;
        this.findings = findings;
        this.correctiveActions = correctiveActions;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        if (farms.count() > 0) {
            log.info("Datos de demostración ya presentes; se omite el sembrado");
            return;
        }
        Long orgId = organizations.findOrganizationIdByName(orgName)
                .or(organizations::anyOrganizationId).orElse(null);
        if (orgId == null) {
            log.warn("No hay organización disponible; se omite el sembrado de demostración");
            return;
        }
        log.info("Sembrando datos de demostración para la organización '{}'",
                organizations.organizationName(orgId).orElse(orgName));

        seedUsers(orgId);
        List<Farm> demoFarms = seedFarms(orgId);
        seedAudits(orgId, demoFarms);

        log.info("Datos de demostración creados: {} explotaciones, {} auditorías",
                demoFarms.size(), audits.countByOrganizationId(orgId));
    }

    private void seedUsers(Long orgId) {
        users.ensureUser(orgId, "ana.auditora@demo.test", "Ana Auditora", Role.AUDITOR, "Demo1234!");
        users.ensureUser(orgId, "carlos.lector@demo.test", "Carlos Lector", Role.VIEWER, "Demo1234!");
    }

    private List<Farm> seedFarms(Long orgId) {
        List<Farm> list = new ArrayList<>();
        list.add(farm(orgId, "Frutas del Sur S.L.", "Frutas del Sur", "4049928111111",
                "Ctra. Cartaya km 4", "Cartaya", "Huelva", "España", "37.283000", "-7.148000",
                "85.00", "Fresa, Frambuesa, Arándano", "Túnel 1-12, Parcela norte",
                "Lucía Márquez", "lmarquez@frutasdelsur.example", "+34 959 000 111"));
        list.add(farm(orgId, "Cítricos Valencianos S.C.V.", "Citriva", "4049928222222",
                "Camí de l'Horta 22", "Carcaixent", "Valencia", "España", "39.121000", "-0.449000",
                "210.00", "Naranja, Mandarina", "Sectores A-F",
                "Vicent Aparicio", "vaparicio@citriva.example", "+34 962 000 222"));
        list.add(farm(orgId, "AgroExport Perú S.A.C.", "AgroExport Perú", "4049928333333",
                "Fundo San José, Panamericana Sur km 300", "Ica", "Ica", "Perú", "-14.087000", "-75.760000",
                "540.00", "Uva de mesa, Arándano, Palta", "Fundos San José, El Carmen y La Huaca",
                "Rosa Quispe", "rquispe@agroexportperu.example", "+51 56 000 333"));
        list.add(farm(orgId, "Golden Farms Ltd", "Golden Farms", "4049928444444",
                "R45 Farm Road", "Ceres", "Western Cape", "Sudáfrica", "-33.363000", "19.310000",
                "320.00", "Manzana, Pera", "Blocks 1-18",
                "Pieter van Wyk", "pvw@goldenfarms.example", "+27 23 000 444"));
        list.add(farm(orgId, "Coopérative Agricole Souss", "Coop Souss", "4049928555555",
                "Route de Taroudant, Aït Melloul", "Agadir", "Souss-Massa", "Marruecos", "30.334000", "-9.497000",
                "140.00", "Tomate, Pimiento, Calabacín", "Serres A1-A9",
                "Khadija Amrani", "kamrani@coopsouss.example", "+212 528 000 555"));
        return list;
    }

    private Farm farm(Long orgId, String legal, String trade, String reg, String address, String city,
                      String region, String country, String lat, String lon, String hectares,
                      String crops, String areas, String contact, String email, String phone) {
        Farm f = new Farm();
        f.setOrganizationId(orgId);
        f.setLegalName(legal);
        f.setTradeName(trade);
        f.setRegistrationNumber(reg);
        f.setAddress(address);
        f.setCity(city);
        f.setRegion(region);
        f.setCountry(country);
        f.setLatitude(new BigDecimal(lat));
        f.setLongitude(new BigDecimal(lon));
        f.setTotalHectares(new BigDecimal(hectares));
        f.setCrops(crops);
        f.setProductionAreas(areas);
        f.setContactName(contact);
        f.setContactEmail(email);
        f.setContactPhone(phone);
        return farms.save(f);
    }

    private void seedAudits(Long orgId, List<Farm> f) {
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
    }

    private int audit(Long orgId, int seq, Farm farm, String standardCode, AuditType type, AuditStatus status,
                      LocalDate date, String leadAuditor, String team, String scope) {
        AuditStandard standard = catalog.controlPointsOf(standardCode).stream()
                .findFirst().map(cp -> cp.getChapter().getStandard()).orElse(null);
        if (standard == null) {
            log.warn("Estándar {} sin puntos de control; se omite la auditoría de demo", standardCode);
            return seq + 1;
        }
        Audit a = new Audit();
        a.setOrganizationId(orgId);
        a.setFarm(farm);
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
        a.setSiteAddress(farm.getAddress() + ", " + farm.getCity());
        a.setProductsInScope(farm.getCrops());
        a = audits.save(a);

        seedResultsAndFindings(a, standardCode, status, date, leadAuditor);
        return seq + 1;
    }

    private void seedResultsAndFindings(Audit audit, String standardCode, AuditStatus status,
                                       LocalDate date, String leadAuditor) {
        List<ControlPoint> cps = catalog.controlPointsOf(standardCode);
        double coverage = switch (status) {
            case DRAFT -> 0.12;
            case IN_PROGRESS -> 0.65;
            default -> 1.0;
        };
        int toAssess = (int) Math.round(cps.size() * coverage);
        Instant assessedAt = date.atStartOfDay().toInstant(ZoneOffset.UTC);

        int findingSeq = 0;
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

            if (compliance == ComplianceResult.NON_COMPLIANT && status != AuditStatus.DRAFT && findingSeq < 5) {
                findingSeq++;
                createFinding(audit, cp, findingSeq, status, date);
            }
        }
    }

    private void createFinding(Audit audit, ControlPoint cp, int seq, AuditStatus status, LocalDate date) {
        FindingClassification classification = switch (cp.getLevel()) {
            case MAJOR_MUST, CORE -> FindingClassification.MAJOR;
            case MINOR_MUST, IMPROVEMENT_L1 -> FindingClassification.MINOR;
            default -> FindingClassification.OBSERVATION;
        };
        Finding finding = new Finding();
        finding.setAuditId(audit.getId());
        finding.setControlPoint(cp);
        finding.setReference(String.format("NC-%03d", seq));
        finding.setClassification(classification);
        finding.setTitle("Incumplimiento en " + cp.getRef());
        finding.setDescription("Durante la auditoría no se evidenció el cumplimiento del criterio \""
                + cp.getTitle() + "\". Debe subsanarse y aportarse evidencia objetiva.");
        finding.setArea(cp.getChapter().getTitle());
        finding.setDueDate(date.plusDays(28));

        FindingStatus fStatus = switch (status) {
            case CLOSED -> FindingStatus.CLOSED;
            case SUBMITTED -> FindingStatus.RESOLVED;
            case CLOSING -> (seq % 2 == 0) ? FindingStatus.IN_PROGRESS : FindingStatus.OPEN;
            default -> FindingStatus.OPEN;
        };
        finding.setStatus(fStatus);
        finding = findings.save(finding);

        if (status == AuditStatus.CLOSED || status == AuditStatus.SUBMITTED || status == AuditStatus.CLOSING
                || (status == AuditStatus.IN_PROGRESS && seq % 2 == 1)) {
            createCorrectiveAction(finding, cp, status, date);
        }
    }

    private void createCorrectiveAction(Finding finding, ControlPoint cp, AuditStatus status, LocalDate date) {
        CorrectiveAction ca = new CorrectiveAction();
        ca.setFindingId(finding.getId());
        ca.setRootCause("Ausencia de procedimiento documentado y de responsable asignado para " + cp.getChapter().getTitle().toLowerCase() + ".");
        ca.setCorrection("Se implanta el registro correspondiente y se recopila la evidencia del punto " + cp.getRef() + ".");
        ca.setPreventiveAction("Se incorpora la revisión de este punto a la lista de verificación interna trimestral.");
        ca.setResponsiblePerson("Responsable de calidad");
        ca.setTargetDate(date.plusDays(21));

        switch (status) {
            case CLOSED -> {
                ca.setStatus(CorrectiveActionStatus.VERIFIED);
                ca.setCompletedDate(date.plusDays(15));
                ca.setVerificationNotes("Evidencia revisada y aceptada por el auditor en la verificación documental.");
                ca.setVerifiedBy(finding.getControlPoint() != null ? "Ana Auditora" : "Auditor");
            }
            case SUBMITTED -> {
                ca.setStatus(CorrectiveActionStatus.COMPLETED);
                ca.setCompletedDate(date.plusDays(18));
            }
            case CLOSING -> ca.setStatus(CorrectiveActionStatus.IN_PROGRESS);
            default -> ca.setStatus(CorrectiveActionStatus.PENDING);
        }
        correctiveActions.save(ca);
    }
}

package com.agriaudit.findings.infrastructure.demo;

import com.agriaudit.audit.AuditRef;
import com.agriaudit.audit.AuditStatus;
import com.agriaudit.audit.DemoAuditSeeded;
import com.agriaudit.catalog.CatalogLookup;
import com.agriaudit.catalog.ControlPointView;
import com.agriaudit.findings.domain.CorrectiveAction;
import com.agriaudit.findings.domain.CorrectiveActionStatus;
import com.agriaudit.findings.domain.Finding;
import com.agriaudit.findings.domain.FindingClassification;
import com.agriaudit.findings.domain.FindingStatus;
import com.agriaudit.findings.infrastructure.persistence.CorrectiveActionRepository;
import com.agriaudit.findings.infrastructure.persistence.FindingRepository;
import com.agriaudit.storage.AttachmentEntityType;
import com.agriaudit.storage.AttachmentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Map;

/**
 * No conformidades y acciones correctivas de demostración para cada auditoría sembrada
 * ({@link DemoAuditSeeded}), con textos realistas por capítulo y estados coherentes con el de la auditoría.
 *
 * <p>Las fechas límite parten de la fecha de la auditoría más 28 días (el plazo de GLOBALG.A.P.), de modo
 * que, al ser las auditorías relativas a hoy, el panel muestra alguna NC vencida, varias a punto de vencer
 * y el resto con margen. Alguna NC lleva una foto de evidencia para que la ficha no se vea vacía.
 */
@Component
class FindingDemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(FindingDemoSeeder.class);

    private static final int MAX_FINDINGS_PER_AUDIT = 5;
    private static final int MAX_FINDINGS_IN_PROGRESS = 3;
    private static final String EVIDENCE_RESOURCE = "/demo/evidencia-calibracion.png";

    /** Texto de una no conformidad y de su acción correctiva. */
    private record Tpl(String title, String description, String rootCause, String correction,
                       String preventive, String responsible) {
    }

    private static final Map<String, Tpl> TEMPLATES = Map.ofEntries(
            Map.entry("GLOBALGAP_IFA_V6:LEGAL", new Tpl(
                    "Evaluación de riesgos del sitio sin actualizar",
                    "La evaluación de riesgos del sitio corresponde a la campaña anterior y no incluye la parcela incorporada este año.",
                    "El procedimiento no exige revisar la evaluación cuando se incorporan parcelas nuevas.",
                    "Se actualiza la evaluación incluyendo todas las parcelas y la firma la dirección.",
                    "Se añade la revisión anual y por alta de parcela al calendario de calidad.",
                    "Responsable de calidad")),
            Map.entry("GLOBALGAP_IFA_V6:MGMT", new Tpl(
                    "Balance de masas no reconstruible en el simulacro de trazabilidad",
                    "En el simulacro no fue posible reconciliar entradas y salidas de un lote de la campaña actual dentro de las 4 horas exigidas.",
                    "Los albaranes de entrada no recogen el código de parcela de origen.",
                    "Se completan los registros del lote y se repite el simulacro con resultado satisfactorio.",
                    "Se incorpora el código de parcela al albarán y a la hoja de recepción.",
                    "Jefe de recepción")),
            Map.entry("GLOBALGAP_IFA_V6:PROD", new Tpl(
                    "Tratamientos fitosanitarios sin plazo de seguridad registrado",
                    "En el registro revisado, 3 de las 12 aplicaciones no incluyen el plazo de seguridad ni la justificación del tratamiento.",
                    "La plantilla del cuaderno de campo no tiene esos campos como obligatorios.",
                    "Se completan los registros con los datos del asesor y se corrigen las 3 aplicaciones.",
                    "Se actualiza la plantilla del cuaderno y se forma a los aplicadores.",
                    "Técnico de campo")),
            Map.entry("GLOBALGAP_IFA_V6:HARV", new Tpl(
                    "Contenedores de cosecha sin limpieza registrada",
                    "No hay registro de limpieza y desinfección de los contenedores reutilizables durante las dos últimas semanas.",
                    "El procedimiento de limpieza no asigna responsable ni frecuencia.",
                    "Se limpia el lote de contenedores y se inicia el registro diario.",
                    "Se asigna responsable y se añade un control semanal de calidad.",
                    "Encargado de cosecha")),
            Map.entry("GLOBALGAP_IFA_V6:FOOD", new Tpl(
                    "Análisis del agua de riego sin renovar",
                    "El último análisis microbiológico del agua en contacto con el producto tiene más de 12 meses y falta la valoración de riesgo.",
                    "No existe un calendario de muestreo con aviso previo.",
                    "Se toman nuevas muestras en un laboratorio acreditado y se archiva el boletín.",
                    "Se crea un calendario anual de muestreo con aviso 30 días antes.",
                    "Responsable de calidad")),
            Map.entry("GLOBALGAP_IFA_V6:ENV", new Tpl(
                    "Plan de gestión de residuos sin actualizar",
                    "El plan de residuos y envases vacíos no refleja los puntos de recogida actuales ni las entregas al gestor autorizado del último semestre.",
                    "Nadie tiene asignada la actualización periódica del plan.",
                    "Se actualiza el plan y se archivan los justificantes de entrega al gestor.",
                    "Se incluye la revisión semestral en el calendario ambiental.",
                    "Responsable de medio ambiente")),
            Map.entry("GLOBALGAP_IFA_V6:WORK", new Tpl(
                    "Formación en primeros auxilios sin registrar",
                    "Dos trabajadores de campo no tienen registrada la formación en primeros auxilios y no consta quién custodia el botiquín en cada parcela.",
                    "La formación se impartió pero no se archivó el registro de asistencia.",
                    "Se recuperan los registros y se imparte una sesión de refuerzo a los dos trabajadores.",
                    "Se centraliza el archivo de formación y se revisa al cierre de cada campaña.",
                    "Responsable de personal")),
            Map.entry("GRASP_V2:CONTRACT", new Tpl(
                    "Contratos sin copia entregada al trabajador",
                    "En 4 de los 15 expedientes revisados no consta que el trabajador recibiera copia de su contrato.",
                    "El acuse de entrega no formaba parte del procedimiento de alta.",
                    "Se entregan las copias pendientes y se recoge la firma de recepción.",
                    "Se añade el acuse de entrega al procedimiento de alta.",
                    "Responsable de personal")),
            Map.entry("GRASP_V2:HOURS", new Tpl(
                    "Registro de jornada incompleto en campaña alta",
                    "El registro de horas de las semanas de mayor carga no está firmado por los trabajadores.",
                    "El registro se completaba al final de la semana por el encargado.",
                    "Se regulariza el registro y se firma con los trabajadores afectados.",
                    "Se pasa a registro diario con firma semanal.",
                    "Encargado de campo")),
            Map.entry("GRASP_V2:COMP", new Tpl(
                    "Canal de quejas sin difusión entre los trabajadores",
                    "Los trabajadores entrevistados desconocen cómo presentar una queja de forma confidencial.",
                    "El procedimiento existe pero no se ha comunicado en el idioma de la plantilla.",
                    "Se publica el canal en tablones y se da una charla informativa.",
                    "Se incluye el canal de quejas en la formación de acogida.",
                    "Responsable de personal")),
            Map.entry("SMETA_6_1:HS", new Tpl(
                    "Salidas de emergencia del alojamiento sin señalizar",
                    "Dos salidas de emergencia del alojamiento de temporeros carecen de señalización visible.",
                    "No se revisó la señalización tras la última reforma.",
                    "Se instala la señalización y se comprueba el libre acceso a las salidas.",
                    "Se añade la revisión trimestral de emergencias al plan de mantenimiento.",
                    "Responsable de mantenimiento")),
            Map.entry("SMETA_6_1:LABOUR", new Tpl(
                    "Horas extraordinarias sin firma del trabajador",
                    "Las horas extraordinarias de algunas semanas figuran en el registro sin conformidad firmada del trabajador.",
                    "El formato de registro no tiene campo de firma.",
                    "Se recaban las firmas pendientes y se corrige el formato.",
                    "Se controla mensualmente el registro por recursos humanos.",
                    "Responsable de personal")),
            Map.entry("SMETA_6_1:MGMT", new Tpl(
                    "Verificación del derecho a trabajar no documentada",
                    "No consta la verificación del derecho a trabajar de los trabajadores de una empresa subcontratada.",
                    "La subcontrata no aporta esa documentación de forma periódica.",
                    "Se solicita y archiva la documentación pendiente.",
                    "Se incluye la entrega periódica de documentación en el contrato de subcontratación.",
                    "Responsable de compras")));

    private final FindingRepository findings;
    private final CorrectiveActionRepository correctiveActions;
    private final CatalogLookup catalog;
    private final AttachmentStore attachments;
    private byte[] evidenceImage;

    FindingDemoSeeder(FindingRepository findings, CorrectiveActionRepository correctiveActions,
                      CatalogLookup catalog, AttachmentStore attachments) {
        this.findings = findings;
        this.correctiveActions = correctiveActions;
        this.catalog = catalog;
        this.attachments = attachments;
    }

    @EventListener
    @Transactional
    public void on(DemoAuditSeeded event) {
        AuditRef audit = event.audit();
        if (audit.status() == AuditStatus.DRAFT) {
            return;
        }
        int max = audit.status() == AuditStatus.IN_PROGRESS ? MAX_FINDINGS_IN_PROGRESS : MAX_FINDINGS_PER_AUDIT;
        int seq = 0;
        for (Long cpId : event.nonCompliantControlPointIds()) {
            if (seq >= max) {
                break;
            }
            ControlPointView cp = catalog.findControlPoint(cpId).orElse(null);
            if (cp == null) {
                continue;
            }
            seq++;
            createFinding(audit, cp, seq);
        }
    }

    private void createFinding(AuditRef audit, ControlPointView cp, int seq) {
        AuditStatus status = audit.status();
        LocalDate today = LocalDate.now();
        LocalDate date = audit.plannedStartDate();
        Tpl tpl = TEMPLATES.getOrDefault(audit.standardCode() + ":" + cp.chapterCode(), generic(cp));

        // Plazo de 28 días desde la auditoría. En la primera auditoría en cierre que ya está al límite,
        // la primera NC sigue sin resolver y ya ha vencido: es lo que el responsable de calidad ve primero.
        LocalDate due = date.plusDays(28);
        if (status == AuditStatus.CLOSING && seq == 1 && !due.isAfter(today.plusDays(4))) {
            due = today.minusDays(4);
        }

        FindingClassification classification = switch (cp.level()) {
            case MAJOR_MUST, CORE -> FindingClassification.MAJOR;
            case MINOR_MUST, IMPROVEMENT_L1 -> FindingClassification.MINOR;
            default -> FindingClassification.OBSERVATION;
        };

        FindingStatus findingStatus;
        CorrectiveActionStatus actionStatus; // null: todavía sin acción correctiva
        switch (status) {
            case CLOSED -> {
                findingStatus = FindingStatus.CLOSED;
                actionStatus = CorrectiveActionStatus.VERIFIED;
            }
            case SUBMITTED -> {
                findingStatus = FindingStatus.VERIFIED;
                actionStatus = CorrectiveActionStatus.VERIFIED;
            }
            case CLOSING -> {
                switch (seq) {
                    case 1 -> {
                        findingStatus = FindingStatus.OPEN;
                        actionStatus = CorrectiveActionStatus.PENDING;
                    }
                    case 3 -> {
                        findingStatus = FindingStatus.RESOLVED;
                        actionStatus = CorrectiveActionStatus.COMPLETED;
                    }
                    case 5 -> {
                        findingStatus = FindingStatus.VERIFIED;
                        actionStatus = CorrectiveActionStatus.VERIFIED;
                    }
                    default -> {
                        findingStatus = FindingStatus.IN_PROGRESS;
                        actionStatus = CorrectiveActionStatus.IN_PROGRESS;
                    }
                }
            }
            default -> { // IN_PROGRESS
                switch (seq) {
                    case 1 -> {
                        findingStatus = FindingStatus.OPEN;
                        actionStatus = CorrectiveActionStatus.PENDING;
                    }
                    case 2 -> {
                        findingStatus = FindingStatus.OPEN;
                        actionStatus = null;
                    }
                    default -> {
                        findingStatus = FindingStatus.IN_PROGRESS;
                        actionStatus = CorrectiveActionStatus.IN_PROGRESS;
                    }
                }
            }
        }

        Finding finding = new Finding();
        finding.setAuditId(audit.id());
        finding.setControlPointId(cp.id());
        finding.setReference(String.format("NC-%03d", seq));
        finding.setClassification(classification);
        finding.setTitle(tpl.title());
        finding.setDescription(tpl.description());
        finding.setArea(cp.chapterTitle());
        finding.setDueDate(due);
        finding.setStatus(findingStatus);
        finding = findings.save(finding);

        if (actionStatus != null) {
            createCorrectiveAction(finding, tpl, actionStatus, date, due, today);
        }
        if (seq == 2 && status != AuditStatus.CLOSED) {
            attachEvidence(audit, finding);
        }
    }

    private void createCorrectiveAction(Finding finding, Tpl tpl, CorrectiveActionStatus status, LocalDate date,
                                        LocalDate due, LocalDate today) {
        CorrectiveAction ca = new CorrectiveAction();
        ca.setFindingId(finding.getId());
        ca.setRootCause(tpl.rootCause());
        ca.setCorrection(tpl.correction());
        ca.setPreventiveAction(tpl.preventive());
        ca.setResponsiblePerson(tpl.responsible());
        ca.setTargetDate(due.minusDays(5));
        ca.setStatus(status);
        if (status == CorrectiveActionStatus.COMPLETED || status == CorrectiveActionStatus.VERIFIED) {
            LocalDate completed = date.plusDays(14);
            ca.setCompletedDate(completed.isAfter(today) ? today : completed);
        }
        if (status == CorrectiveActionStatus.VERIFIED) {
            ca.setVerificationNotes("Evidencia revisada y aceptada por el auditor en la verificación documental.");
            ca.setVerifiedBy("Ana Auditora");
        }
        correctiveActions.save(ca);
    }

    private void attachEvidence(AuditRef audit, Finding finding) {
        byte[] image = evidenceImage();
        if (image == null) {
            return;
        }
        attachments.store(new AttachmentStore.NewAttachment(audit.organizationId(), audit.leadAuditor(),
                AttachmentEntityType.FINDING, finding.getId(), "registro-calibracion-pulverizador.png",
                "image/png", image));
    }

    private byte[] evidenceImage() {
        if (evidenceImage == null) {
            try (InputStream in = FindingDemoSeeder.class.getResourceAsStream(EVIDENCE_RESOURCE)) {
                if (in == null) {
                    log.warn("No se encuentra la imagen de evidencia de demostración {}", EVIDENCE_RESOURCE);
                    return null;
                }
                evidenceImage = in.readAllBytes();
            } catch (IOException e) {
                log.warn("No se pudo leer la imagen de evidencia de demostración", e);
                return null;
            }
        }
        return evidenceImage;
    }

    /** Texto genérico para un capítulo sin plantilla propia. */
    private static Tpl generic(ControlPointView cp) {
        return new Tpl(
                "Incumplimiento en " + cp.ref(),
                "Durante la auditoría no se evidenció el cumplimiento del criterio «" + cp.title()
                        + "». Debe subsanarse y aportarse evidencia objetiva.",
                "Ausencia de procedimiento documentado y de responsable asignado para "
                        + cp.chapterTitle().toLowerCase() + ".",
                "Se implanta el registro correspondiente y se recopila la evidencia del punto " + cp.ref() + ".",
                "Se incorpora la revisión de este punto a la lista de verificación interna trimestral.",
                "Responsable de calidad");
    }
}

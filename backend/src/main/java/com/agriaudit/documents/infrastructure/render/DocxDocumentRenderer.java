package com.agriaudit.documents.infrastructure.render;

import com.agriaudit.common.BadRequestException;
import com.agriaudit.documents.domain.DocumentTemplate;
import com.agriaudit.audit.AuditDtos.AuditDetailDto;
import com.agriaudit.audit.AuditDtos.AuditItemResultDto;
import com.agriaudit.catalog.CatalogDtos.ChapterDto;
import com.agriaudit.catalog.CatalogDtos.ControlPointDto;
import com.agriaudit.findings.FindingDtos.CorrectiveActionDto;
import com.agriaudit.findings.FindingDtos.FindingDto;
import com.agriaudit.audit.ScoreDtos.GroupScore;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.Objects;

import static com.agriaudit.documents.infrastructure.render.DocxSupport.*;
import com.agriaudit.documents.application.DocumentModel;
import com.agriaudit.documents.application.DocumentRenderer;

@Component
public class DocxDocumentRenderer implements DocumentRenderer {

    @Override
    public boolean supports(String engine) {
        return "DOCX_POI".equals(engine);
    }

    @Override
    public byte[] render(DocumentTemplate template, DocumentModel model) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            switch (template.getDocumentType()) {
                case AUDIT_PLAN -> auditPlan(doc, model);
                case NONCONFORMITY_REPORT -> nonconformity(doc, model);
                case CAPA_REPORT -> capa(doc, model);
                case AUDIT_REPORT -> auditReport(doc, model);
                case SMETA_REPORT -> smetaReport(doc, model);
                default -> throw new BadRequestException(
                        "El tipo de documento " + template.getDocumentType() + " no está disponible en formato DOCX");
            }
            small(doc, model.disclaimer());
            doc.write(out);
            return out.toByteArray();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error generando el documento Word", e);
        }
    }

    private void header(XWPFDocument doc, DocumentModel m, String title) {
        title(doc, title);
        AuditDetailDto a = m.audit();
        subtitle(doc, m.organizationName() + "  ·  " + a.standardName()
                + (a.standardVersion() != null ? " (" + a.standardVersion() + ")" : "")
                + "  ·  Generado el " + m.generatedAt() + " por " + m.generatedBy());
        keyValueTable(doc, new String[][]{
                {"Referencia de auditoría", nz(a.reference())},
                {"Estándar", a.standardName()},
                {"Tipo de auditoría", DocLabels.auditType(a.auditType())},
                {"Estado", DocLabels.auditStatus(a.status())},
                {"Explotación / empresa", nz(m.farm().legalName())},
                {"Nº de registro (GGN / otro)", nz(m.farm().registrationNumber())},
                {"Emplazamiento", nz(a.siteAddress())},
                {"País", nz(m.farm().country())},
                {"Fecha prevista", a.plannedStartDate() == null ? "-" : a.plannedStartDate().toString()},
                {"Fecha de auditoría", a.actualDate() == null ? "-" : a.actualDate().toString()},
                {"Auditor responsable", nz(a.leadAuditor())},
                {"Equipo auditor", nz(a.auditTeam())},
                {"Productos en alcance", nz(a.productsInScope())},
        });
    }

    private void auditPlan(XWPFDocument doc, DocumentModel m) {
        header(doc, m, "Plan de Auditoría");
        AuditDetailDto a = m.audit();

        heading(doc, "1. Objeto y alcance");
        paragraph(doc, nz(a.scopeDescription(), "Verificar el grado de cumplimiento de la explotación con los "
                + "requisitos del estándar " + a.standardName() + " para los productos y emplazamientos indicados."));

        heading(doc, "2. Documentos de referencia");
        paragraph(doc, "· " + a.standardName() + (a.standardVersion() != null ? " " + a.standardVersion() : ""));
        paragraph(doc, "· Reglamento general del estándar y guías de interpretación nacionales aplicables.");
        paragraph(doc, "· Legislación aplicable en el país de producción (seguridad alimentaria, fitosanitarios, "
                + "medio ambiente y legislación laboral).");

        heading(doc, "3. Agenda prevista");
        XWPFTable agenda = table(doc, "Hora", "Actividad", "Participantes");
        addRow(agenda, "09:00", "Reunión de apertura: alcance, plan, confidencialidad", "Dirección y equipo auditor");
        addRow(agenda, "09:30", "Revisión documental (sistema de gestión, trazabilidad, registros)", "Responsable de calidad");
        addRow(agenda, "11:00", "Inspección de campo e instalaciones", "Responsable técnico");
        addRow(agenda, "13:30", "Entrevistas a trabajadores", "Equipo auditor");
        addRow(agenda, "15:30", "Análisis de evidencias y redacción de hallazgos", "Equipo auditor");
        addRow(agenda, "16:30", "Reunión de cierre: presentación de no conformidades y plazos", "Dirección y equipo auditor");

        heading(doc, "4. Áreas / capítulos a auditar");
        XWPFTable chapters = table(doc, "Código", "Capítulo", "Nº de puntos de control");
        for (ChapterDto ch : m.chapters()) {
            addRow(chapters, ch.code(), ch.title(), String.valueOf(ch.controlPoints().size()));
        }

        heading(doc, "5. Criterios de decisión");
        for (String note : m.score().notes()) {
            paragraph(doc, "· " + note);
        }
    }

    private void nonconformity(XWPFDocument doc, DocumentModel m) {
        header(doc, m, "Informe de No Conformidades");
        long open = m.findings().stream().filter(f -> !"CLOSED".equals(f.status())).count();
        paragraph(doc, "Total de no conformidades: " + m.findings().size()
                + "  ·  Abiertas: " + open);

        XWPFTable t = table(doc, "Nº", "Punto de control", "Nivel", "Clasificación",
                "Descripción de la no conformidad", "Fecha límite", "Estado");
        int i = 1;
        for (FindingDto f : m.findings()) {
            addRow(t,
                    nz(f.reference(), String.valueOf(i)),
                    (f.controlPointRef() == null ? "-" : f.controlPointRef())
                            + (f.controlPointTitle() != null ? " · " + f.controlPointTitle() : ""),
                    f.controlPointId() == null ? "-" : levelForControl(m, f.controlPointId()),
                    DocLabels.classification(f.classification()),
                    buildFindingText(f),
                    f.dueDate() == null ? "-" : f.dueDate().toString(),
                    DocLabels.findingStatus(f.status()));
            i++;
        }
        if (m.findings().isEmpty()) {
            paragraph(doc, "No se han registrado no conformidades en esta auditoría.");
        }

        findingEvidence(doc, m);

        heading(doc, "Firmas");
        keyValueTable(doc, new String[][]{
                {"Auditor responsable", nz(m.audit().leadAuditor())},
                {"Fecha", m.audit().actualDate() == null ? m.generatedAt() : m.audit().actualDate().toString()},
                {"Representante de la empresa auditada", nz(m.farm().contactName())},
                {"Fecha", ""},
        });
        paragraph(doc, "Ambas partes confirman haber revisado y entendido las no conformidades detectadas y los "
                + "plazos acordados para su resolución.");
    }

    private void capa(XWPFDocument doc, DocumentModel m) {
        header(doc, m, "Plan de Acciones Correctivas (CAPA / CAPR)");
        if (m.findings().isEmpty()) {
            paragraph(doc, "No hay hallazgos que requieran acciones correctivas.");
            return;
        }
        int i = 1;
        for (FindingDto f : m.findings()) {
            heading(doc, "Hallazgo " + nz(f.reference(), String.valueOf(i)) + " · "
                    + DocLabels.classification(f.classification())
                    + (f.controlPointRef() != null ? " · " + f.controlPointRef() : ""));
            paragraph(doc, buildFindingText(f));
            if (f.correctiveActions().isEmpty()) {
                paragraph(doc, "· Pendiente de definir acción correctiva.");
            }
            for (CorrectiveActionDto ca : f.correctiveActions()) {
                keyValueTable(doc, new String[][]{
                        {"Causa raíz", nz(ca.rootCause())},
                        {"Corrección inmediata", nz(ca.correction())},
                        {"Acción preventiva", nz(ca.preventiveAction())},
                        {"Responsable", nz(ca.responsiblePerson())},
                        {"Fecha objetivo", ca.targetDate() == null ? "-" : ca.targetDate().toString()},
                        {"Fecha de cierre", ca.completedDate() == null ? "-" : ca.completedDate().toString()},
                        {"Verificación", nz(ca.verificationNotes())},
                        {"Verificado por", nz(ca.verifiedBy())},
                        {"Estado", DocLabels.caStatus(ca.status())},
                });
            }
            i++;
        }
    }

    private void auditReport(XWPFDocument doc, DocumentModel m) {
        header(doc, m, "Informe de Auditoría");
        var score = m.score();

        heading(doc, "1. Resumen ejecutivo");
        keyValueTable(doc, new String[][]{
                {"Resultado global", DocLabels.overallResult(score.overallResult())},
                {"Progreso de la evaluación", score.progressPct() + "%"},
                {"Puntos de control evaluados", score.assessedCount() + " / " + score.totalControlPoints()},
                {"No conformidades", String.valueOf(m.findings().size())},
        });
        for (String note : score.notes()) {
            paragraph(doc, "· " + note);
        }

        heading(doc, "2. Resultado por capítulo");
        XWPFTable ch = table(doc, "Capítulo", "Conformes", "No conformes", "No aplica", "Sin evaluar", "% conformidad");
        for (GroupScore g : score.chapterBreakdown()) {
            addRow(ch, g.title(), String.valueOf(g.compliant()), String.valueOf(g.nonCompliant()),
                    String.valueOf(g.notApplicable()), String.valueOf(g.notAssessed()),
                    String.format("%.1f%%", g.compliancePct()));
        }

        heading(doc, "3. Detalle de la evaluación");
        for (ChapterDto chapter : m.chapters()) {
            heading(doc, chapter.code() + " · " + chapter.title());
            XWPFTable t = table(doc, "Ref", "Punto de control", "Nivel", "Resultado", "Evidencias / observaciones");
            for (ControlPointDto cp : chapter.controlPoints()) {
                AuditItemResultDto r = m.resultsByControlPoint().get(cp.id());
                addRow(t, cp.ref(), cp.title(), DocLabels.level(cp.level()),
                        DocLabels.compliance(r == null ? "NOT_ASSESSED" : r.compliance()),
                        r == null ? "" : joinNotes(r));
            }
        }

        heading(doc, "4. No conformidades");
        if (m.findings().isEmpty()) {
            paragraph(doc, "No se han registrado no conformidades.");
        } else {
            XWPFTable t = table(doc, "Nº", "Punto", "Clasificación", "Descripción", "Fecha límite", "Estado");
            int i = 1;
            for (FindingDto f : m.findings()) {
                addRow(t, nz(f.reference(), String.valueOf(i)),
                        f.controlPointRef() == null ? "-" : f.controlPointRef(),
                        DocLabels.classification(f.classification()),
                        buildFindingText(f),
                        f.dueDate() == null ? "-" : f.dueDate().toString(),
                        DocLabels.findingStatus(f.status()));
                i++;
            }
            findingEvidence(doc, m);
        }

        heading(doc, "5. Conclusión");
        paragraph(doc, conclusionText(m));

        heading(doc, "6. Firmas");
        keyValueTable(doc, new String[][]{
                {"Auditor responsable", nz(m.audit().leadAuditor())},
                {"Representante de la empresa", nz(m.farm().contactName())},
                {"Fecha", m.audit().actualDate() == null ? m.generatedAt() : m.audit().actualDate().toString()},
        });
    }

    private void smetaReport(XWPFDocument doc, DocumentModel m) {
        header(doc, m, "Informe de Auditoría Ética SMETA");
        paragraph(doc, "Auditoría realizada tomando como referencia el ETI Base Code y la legislación local aplicable. "
                + "Pilares evaluados: normas laborales, salud y seguridad, medio ambiente y ética empresarial.");

        heading(doc, "1. Resumen por pilar");
        XWPFTable t = table(doc, "Pilar", "Conformes", "No conformes", "No aplica", "Sin evaluar");
        for (GroupScore g : m.score().pillarBreakdown()) {
            addRow(t, g.title(), String.valueOf(g.compliant()), String.valueOf(g.nonCompliant()),
                    String.valueOf(g.notApplicable()), String.valueOf(g.notAssessed()));
        }
        if (m.score().pillarBreakdown().isEmpty()) {
            for (GroupScore g : m.score().chapterBreakdown()) {
                addRow(t, g.title(), String.valueOf(g.compliant()), String.valueOf(g.nonCompliant()),
                        String.valueOf(g.notApplicable()), String.valueOf(g.notAssessed()));
            }
        }

        heading(doc, "2. No conformidades detectadas");
        if (m.findings().isEmpty()) {
            paragraph(doc, "No se han registrado no conformidades.");
        } else {
            XWPFTable nc = table(doc, "Nº", "Requisito", "Clasificación", "Descripción", "Acción correctiva acordada");
            int i = 1;
            for (FindingDto f : m.findings()) {
                String ca = f.correctiveActions().isEmpty() ? "Pendiente"
                        : nz(f.correctiveActions().get(0).correction(), "Ver CAPR");
                addRow(nc, nz(f.reference(), String.valueOf(i)),
                        f.controlPointRef() == null ? nz(f.area()) : f.controlPointRef(),
                        DocLabels.classification(f.classification()), buildFindingText(f), ca);
                i++;
            }
            findingEvidence(doc, m);
        }

        heading(doc, "3. Declaración");
        paragraph(doc, "La auditoría se ha realizado mediante revisión documental, inspección de las instalaciones y "
                + "entrevistas confidenciales a los trabajadores. Los hallazgos y el plan de acciones correctivas "
                + "(CAPR) se han comunicado a la dirección en la reunión de cierre.");
    }

    // ---- helpers ----

    /** Bloque "Evidencias" por cada hallazgo que tenga adjuntos. */
    private void findingEvidence(XWPFDocument doc, DocumentModel m) {
        boolean any = m.findings().stream()
                .anyMatch(f -> m.evidenceByFinding().containsKey(f.id()));
        if (!any) {
            return;
        }
        heading(doc, "Evidencias adjuntas a las no conformidades");
        for (FindingDto f : m.findings()) {
            var refs = m.evidenceByFinding().get(f.id());
            if (refs == null || refs.isEmpty()) {
                continue;
            }
            paragraph(doc, nz(f.reference(), "NC") + " — " + buildFindingText(f));
            DocxSupport.evidence(doc, refs);
        }
    }

    private String levelForControl(DocumentModel m, Long cpId) {
        AuditItemResultDto r = m.resultsByControlPoint().get(cpId);
        return r == null ? "-" : DocLabels.level(r.level());
    }

    private static String buildFindingText(FindingDto f) {
        StringBuilder sb = new StringBuilder();
        if (f.title() != null && !f.title().isBlank()) sb.append(f.title());
        if (f.description() != null && !f.description().isBlank()) {
            if (sb.length() > 0) sb.append(" — ");
            sb.append(f.description());
        }
        if (sb.length() == 0 && f.controlPointTitle() != null) sb.append(f.controlPointTitle());
        return sb.toString();
    }

    private static String joinNotes(AuditItemResultDto r) {
        StringBuilder sb = new StringBuilder();
        if (r.evidenceNotes() != null && !r.evidenceNotes().isBlank()) sb.append(r.evidenceNotes());
        if (r.auditorComment() != null && !r.auditorComment().isBlank()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(r.auditorComment());
        }
        return sb.toString();
    }

    private static String conclusionText(DocumentModel m) {
        String result = m.score().overallResult();
        return switch (result) {
            case "PASS" -> "La explotación cumple los criterios del estándar en el alcance evaluado. Se recomienda "
                    + "elevar el expediente a decisión de certificación una vez cerradas las no conformidades menores.";
            case "FAIL" -> "La explotación no alcanza los umbrales de cumplimiento exigidos por el estándar. Es "
                    + "necesario resolver las no conformidades y realizar una verificación antes de continuar.";
            case "REPORT_ONLY" -> "Este estándar produce un informe de evaluación. Los hallazgos deben tratarse "
                    + "mediante el plan de acciones correctivas acordado con la empresa.";
            default -> "La evaluación no está completa. Deben evaluarse todos los puntos de control obligatorios "
                    + "antes de emitir una conclusión.";
        };
    }

    private static String nz(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String nz(String value, String fallback) {
        return value == null || value.isBlank() ? Objects.toString(fallback, "-") : value;
    }
}

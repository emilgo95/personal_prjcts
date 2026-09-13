package com.agriaudit.service.document;

import com.agriaudit.common.BadRequestException;
import com.agriaudit.domain.DocumentTemplate;
import com.agriaudit.audit.AuditDtos.AuditItemResultDto;
import com.agriaudit.findings.FindingDtos.CorrectiveActionDto;
import com.agriaudit.findings.FindingDtos.FindingDto;
import com.agriaudit.audit.ScoreDtos.GroupScore;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;

@Component
public class XlsxDocumentRenderer implements DocumentRenderer {

    @Override
    public boolean supports(String engine) {
        return "XLSX_POI".equals(engine);
    }

    @Override
    public byte[] render(DocumentTemplate template, DocumentModel model) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            switch (template.getDocumentType()) {
                case CHECKLIST -> checklist(wb, model, false);
                case GRASP_ASSESSMENT -> checklist(wb, model, true);
                case CAPA_REPORT -> capa(wb, model);
                default -> throw new BadRequestException(
                        "El tipo de documento " + template.getDocumentType() + " no está disponible en formato XLSX");
            }
            wb.write(out);
            return out.toByteArray();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error generando la hoja de cálculo", e);
        }
    }

    private void checklist(XSSFWorkbook wb, DocumentModel m, boolean grasp) {
        Sheet sheet = wb.createSheet(grasp ? "Evaluación GRASP" : "Checklist");
        CellStyle header = headerStyle(wb);
        CellStyle wrap = wrapStyle(wb);

        String[] cols = grasp
                ? new String[]{"Capítulo", "Ref", "Punto de control", "Nivel", "Cumple (Sí/No/N.A.)", "Evidencias", "Comentario", "Adjuntos"}
                : new String[]{"Capítulo", "Ref", "Punto de control", "Nivel", "Ámbito", "Resultado", "Evidencias", "Comentario auditor", "Guía", "Adjuntos"};

        int r = 0;
        Row meta = sheet.createRow(r++);
        meta.createCell(0).setCellValue(m.documentTitle() + " — " + m.audit().standardName());
        Row meta2 = sheet.createRow(r++);
        meta2.createCell(0).setCellValue("Explotación: " + safe(m.farm().legalName())
                + "  |  Ref: " + safe(m.audit().reference())
                + "  |  Generado: " + m.generatedAt() + " por " + m.generatedBy());
        r++;

        Row head = sheet.createRow(r++);
        for (int c = 0; c < cols.length; c++) {
            Cell cell = head.createCell(c);
            cell.setCellValue(cols[c]);
            cell.setCellStyle(header);
        }

        for (var chapter : m.chapters()) {
            for (var cp : chapter.controlPoints()) {
                AuditItemResultDto res = m.resultsByControlPoint().get(cp.id());
                Row row = sheet.createRow(r++);
                int c = 0;
                put(row, c++, chapter.title(), wrap);
                put(row, c++, cp.ref(), wrap);
                put(row, c++, cp.title(), wrap);
                put(row, c++, DocLabels.level(cp.level()), wrap);
                if (grasp) {
                    put(row, c++, graspAnswer(res), wrap);
                } else {
                    put(row, c++, safe(cp.applicableScopes()), wrap);
                    put(row, c++, DocLabels.compliance(res == null ? "NOT_ASSESSED" : res.compliance()), wrap);
                }
                put(row, c++, res == null ? "" : safe(res.evidenceNotes()), wrap);
                put(row, c++, res == null ? "" : safe(res.auditorComment()), wrap);
                if (!grasp) {
                    put(row, c++, safe(cp.guidance()), wrap);
                }
                put(row, c, attachmentNames(m.evidenceByControlPoint().get(cp.id())), wrap);
            }
        }

        for (int c = 0; c < cols.length; c++) {
            sheet.setColumnWidth(c, c == 2 ? 16000 : 6000);
        }
        sheet.createFreezePane(0, 4);

        summarySheet(wb, m);
        legendSheet(wb, m);
    }

    private void capa(XSSFWorkbook wb, DocumentModel m) {
        Sheet sheet = wb.createSheet("Acciones correctivas");
        CellStyle header = headerStyle(wb);
        CellStyle wrap = wrapStyle(wb);
        String[] cols = {"Nº hallazgo", "Clasificación", "Punto de control", "Descripción del hallazgo", "Fecha límite",
                "Causa raíz", "Corrección", "Acción preventiva", "Responsable", "Fecha objetivo", "Fecha cierre",
                "Verificación", "Estado"};
        Row head = sheet.createRow(0);
        for (int c = 0; c < cols.length; c++) {
            Cell cell = head.createCell(c);
            cell.setCellValue(cols[c]);
            cell.setCellStyle(header);
        }
        int r = 1;
        for (FindingDto f : m.findings()) {
            if (f.correctiveActions().isEmpty()) {
                r = capaRow(sheet, r, wrap, f, null);
            } else {
                for (CorrectiveActionDto ca : f.correctiveActions()) {
                    r = capaRow(sheet, r, wrap, f, ca);
                }
            }
        }
        for (int c = 0; c < cols.length; c++) {
            sheet.setColumnWidth(c, 7000);
        }
        sheet.createFreezePane(0, 1);
    }

    private int capaRow(Sheet sheet, int r, CellStyle wrap, FindingDto f, CorrectiveActionDto ca) {
        Row row = sheet.createRow(r);
        int c = 0;
        put(row, c++, safe(f.reference()), wrap);
        put(row, c++, DocLabels.classification(f.classification()), wrap);
        put(row, c++, safe(f.controlPointRef()), wrap);
        put(row, c++, findingText(f), wrap);
        put(row, c++, f.dueDate() == null ? "" : f.dueDate().toString(), wrap);
        put(row, c++, ca == null ? "" : safe(ca.rootCause()), wrap);
        put(row, c++, ca == null ? "" : safe(ca.correction()), wrap);
        put(row, c++, ca == null ? "" : safe(ca.preventiveAction()), wrap);
        put(row, c++, ca == null ? "" : safe(ca.responsiblePerson()), wrap);
        put(row, c++, ca == null || ca.targetDate() == null ? "" : ca.targetDate().toString(), wrap);
        put(row, c++, ca == null || ca.completedDate() == null ? "" : ca.completedDate().toString(), wrap);
        put(row, c++, ca == null ? "" : safe(ca.verificationNotes()), wrap);
        put(row, c, ca == null ? "Pendiente" : DocLabels.caStatus(ca.status()), wrap);
        return r + 1;
    }

    private void summarySheet(XSSFWorkbook wb, DocumentModel m) {
        Sheet sheet = wb.createSheet("Resumen");
        CellStyle header = headerStyle(wb);
        Row title = sheet.createRow(0);
        title.createCell(0).setCellValue("Resultado global: " + DocLabels.overallResult(m.score().overallResult())
                + "  ·  Progreso: " + m.score().progressPct() + "%");
        String[] cols = {"Capítulo", "Conformes", "No conformes", "No aplica", "Sin evaluar", "% conformidad"};
        Row head = sheet.createRow(2);
        for (int c = 0; c < cols.length; c++) {
            Cell cell = head.createCell(c);
            cell.setCellValue(cols[c]);
            cell.setCellStyle(header);
        }
        int r = 3;
        for (GroupScore g : m.score().chapterBreakdown()) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(g.title());
            row.createCell(1).setCellValue(g.compliant());
            row.createCell(2).setCellValue(g.nonCompliant());
            row.createCell(3).setCellValue(g.notApplicable());
            row.createCell(4).setCellValue(g.notAssessed());
            row.createCell(5).setCellValue(String.format("%.1f%%", g.compliancePct()));
        }
        r++;
        for (String note : m.score().notes()) {
            sheet.createRow(r++).createCell(0).setCellValue("· " + note);
        }
        for (int c = 0; c < cols.length; c++) {
            sheet.setColumnWidth(c, c == 0 ? 14000 : 4500);
        }
    }

    private void legendSheet(XSSFWorkbook wb, DocumentModel m) {
        Sheet sheet = wb.createSheet("Aviso");
        int r = 0;
        for (String line : m.disclaimer().split("(?<=\\.) ")) {
            sheet.createRow(r++).createCell(0).setCellValue(line);
        }
        sheet.setColumnWidth(0, 20000);
    }

    // ---- estilos / helpers ----

    private CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    private CellStyle wrapStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private void put(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static String graspAnswer(AuditItemResultDto r) {
        if (r == null) return "";
        return switch (r.compliance()) {
            case "COMPLIANT" -> "Sí";
            case "NON_COMPLIANT" -> "No";
            case "NOT_APPLICABLE" -> "N.A.";
            default -> "";
        };
    }

    private static String findingText(FindingDto f) {
        StringBuilder sb = new StringBuilder();
        if (f.title() != null) sb.append(f.title());
        if (f.description() != null) {
            if (sb.length() > 0) sb.append(" — ");
            sb.append(f.description());
        }
        return sb.toString();
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }

    private static String attachmentNames(java.util.List<DocumentModel.EvidenceRef> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return items.stream().map(DocumentModel.EvidenceRef::fileName)
                .collect(java.util.stream.Collectors.joining(", "));
    }
}

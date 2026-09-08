package com.agriaudit.service.document;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.util.Base64;
import java.util.List;

/** Utilidades para construir documentos Word con Apache POI. */
final class DocxSupport {

    private DocxSupport() {
    }

    /** Lista de evidencias adjuntas; las imágenes se embeben. */
    static void evidence(XWPFDocument doc, List<DocumentModel.EvidenceRef> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        XWPFParagraph head = doc.createParagraph();
        head.setSpacingBefore(60);
        XWPFRun hr = head.createRun();
        hr.setFontSize(8);
        hr.setBold(true);
        hr.setColor("0F6B5F");
        hr.setFontFamily("Arial");
        hr.setText("Evidencias adjuntas:");

        for (DocumentModel.EvidenceRef ev : items) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun run = p.createRun();
            if (ev.hasImage()) {
                embedImage(run, ev);
            }
            XWPFRun name = p.createRun();
            name.setFontSize(8);
            name.setColor("555555");
            name.setFontFamily("Arial");
            long kb = ev.sizeBytes() == null ? 0 : ev.sizeBytes() / 1024 + 1;
            name.setText("• " + ev.fileName() + (kb > 0 ? " (" + kb + " KB)" : ""));
        }
    }

    private static void embedImage(XWPFRun run, DocumentModel.EvidenceRef ev) {
        try {
            String uri = ev.dataUri();
            byte[] bytes = Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1));
            int w = 380;
            int h = 285;
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img != null && img.getWidth() > 0) {
                double scale = Math.min(380.0 / img.getWidth(), 1.0);
                w = (int) Math.round(img.getWidth() * scale);
                h = (int) Math.round(img.getHeight() * scale);
            }
            int type = ev.contentType() != null && ev.contentType().toLowerCase().contains("png")
                    ? Document.PICTURE_TYPE_PNG : Document.PICTURE_TYPE_JPEG;
            run.addPicture(new ByteArrayInputStream(bytes), type, ev.fileName(), Units.toEMU(w), Units.toEMU(h));
            run.addBreak();
        } catch (Exception ignored) {
            // si la imagen no se puede procesar, se queda solo el nombre del fichero
        }
    }

    static void title(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        p.setSpacingAfter(120);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontSize(18);
        r.setFontFamily("Arial");
        r.setText(text == null ? "" : text);
    }

    static void subtitle(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(200);
        XWPFRun r = p.createRun();
        r.setFontSize(11);
        r.setColor("555555");
        r.setFontFamily("Arial");
        r.setText(text == null ? "" : text);
    }

    static void heading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(240);
        p.setSpacingAfter(100);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontSize(13);
        r.setFontFamily("Arial");
        r.setText(text == null ? "" : text);
    }

    static void paragraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(80);
        XWPFRun r = p.createRun();
        r.setFontSize(10);
        r.setFontFamily("Arial");
        r.setText(text == null ? "" : text);
    }

    static void small(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(240);
        XWPFRun r = p.createRun();
        r.setFontSize(8);
        r.setColor("777777");
        r.setItalic(true);
        r.setFontFamily("Arial");
        r.setText(text == null ? "" : text);
    }

    static XWPFTable table(XWPFDocument doc, String... headers) {
        XWPFTable table = doc.createTable(1, headers.length);
        table.setWidth("100%");
        setTableWidthPct(table);
        XWPFTableRow head = table.getRow(0);
        for (int i = 0; i < headers.length; i++) {
            cell(head.getCell(i), headers[i], true);
        }
        return table;
    }

    static void addRow(XWPFTable table, String... values) {
        XWPFTableRow row = table.createRow();
        for (int i = 0; i < values.length && i < row.getTableCells().size(); i++) {
            cell(row.getCell(i), values[i], false);
        }
    }

    static void keyValueTable(XWPFDocument doc, String[][] rows) {
        XWPFTable table = doc.createTable(rows.length, 2);
        table.setWidth("100%");
        setTableWidthPct(table);
        for (int i = 0; i < rows.length; i++) {
            cell(table.getRow(i).getCell(0), rows[i][0], true);
            cell(table.getRow(i).getCell(1), rows[i][1], false);
        }
    }

    private static void cell(XWPFTableCell cell, String text, boolean bold) {
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        p.setSpacingAfter(20);
        XWPFRun r = p.createRun();
        r.setFontSize(9);
        r.setFontFamily("Arial");
        r.setBold(bold);
        r.setText(text == null ? "" : text);
    }

    private static void setTableWidthPct(XWPFTable table) {
        CTTblWidth width = table.getCTTbl().getTblPr().addNewTblW();
        width.setType(STTblWidth.PCT);
        width.setW(BigInteger.valueOf(5000));
    }
}

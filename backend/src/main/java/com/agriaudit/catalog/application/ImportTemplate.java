package com.agriaudit.catalog.application;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Plantilla de Excel para el listado de puntos de control. Las filas de ejemplo son inventadas, no de ninguna norma. */
public final class ImportTemplate {

    static final String[] HEADERS = {"Capítulo", "Título del capítulo", "Referencia", "Título", "Descripción", "Nivel",
            "Guía", "Evidencias", "Alcance"};
    static final String[] LEVELS = {"Obligación mayor", "Obligación menor", "Recomendación", "Núcleo",
            "Mejora nivel 1", "Mejora nivel 2"};

    private ImportTemplate() {
    }

    public static byte[] build() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Font bold = wb.createFont();
            bold.setBold(true);
            Font white = wb.createFont();
            white.setBold(true);
            white.setColor(IndexedColors.WHITE.getIndex());
            CellStyle head = wb.createCellStyle();
            head.setFont(white);
            head.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle wrap = wb.createCellStyle();
            wrap.setWrapText(true);

            Sheet help = wb.createSheet("Instrucciones");
            String[] lines = {
                    "Listado de puntos de control de un estándar propio",
                    "",
                    "1. Rellena la hoja «Puntos de control»: una fila por punto, con los textos de TU listado.",
                    "2. Obligatorias: Capítulo, Referencia, Título y Nivel. El resto es opcional.",
                    "3. Nivel: Obligación mayor, Obligación menor, Recomendación, Núcleo, Mejora nivel 1 o Mejora nivel 2.",
                    "4. Capítulo es el código del capítulo (p. ej. «1»). Repite el mismo código y título en cada fila de ese capítulo.",
                    "5. La referencia no puede repetirse dentro de un capítulo. Máximo 3.000 puntos.",
                    "6. Alcance (opcional): para estándares por pilares, el pilar (LABOUR, HEALTH_SAFETY, ENVIRONMENT, "
                            + "BUSINESS_ETHICS o MANAGEMENT).",
                    "7. En la aplicación indicas el código, nombre y versión del estándar y su regla de puntuación; "
                            + "después subes este fichero (también vale un CSV en UTF-8 con las mismas columnas).",
                    "",
                    "Las filas de ejemplo de la otra hoja son inventadas: bórralas antes de importar.",
                    "Importa solo listados de los que tengas derecho de uso; la aplicación no distribuye textos de ninguna norma."
            };
            for (int i = 0; i < lines.length; i++) {
                help.createRow(i).createCell(0).setCellValue(lines[i]);
            }
            help.setColumnWidth(0, 120 * 256);

            Sheet sheet = wb.createSheet("Puntos de control");
            Row header = sheet.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) {
                var cell = header.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(head);
            }
            String[][] examples = {
                    {"1", "Capítulo de ejemplo", "1.1", "Ejemplo de requisito de registro", "Texto de ejemplo.",
                            "Obligación mayor", "", "Registro firmado", ""},
                    {"1", "Capítulo de ejemplo", "1.2", "Ejemplo de requisito de formación", "Texto de ejemplo.",
                            "Obligación menor", "", "Lista de asistentes", ""},
                    {"2", "Segundo capítulo de ejemplo", "2.1", "Ejemplo de recomendación", "Texto de ejemplo.",
                            "Recomendación", "", "", ""},
            };
            for (int r = 0; r < examples.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < examples[r].length; c++) {
                    row.createCell(c).setCellValue(examples[r][c]);
                }
            }
            int[] widths = {12, 30, 14, 50, 50, 20, 30, 30, 18};
            for (int c = 0; c < widths.length; c++) {
                sheet.setColumnWidth(c, widths[c] * 256);
            }
            sheet.createFreezePane(0, 1);
            DataValidationHelper dv = sheet.getDataValidationHelper();
            DataValidationConstraint list = dv.createExplicitListConstraint(LEVELS);
            DataValidation validation = dv.createValidation(list, new CellRangeAddressList(1, MAX_ROWS, 5, 5));
            validation.setShowErrorBox(true);
            sheet.addValidationData(validation);
            wb.setActiveSheet(1);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar la plantilla", e);
        }
    }

    private static final int MAX_ROWS = 3100;
}

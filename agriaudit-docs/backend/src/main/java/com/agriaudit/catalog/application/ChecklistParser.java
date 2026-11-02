package com.agriaudit.catalog.application;

import com.agriaudit.catalog.ControlLevel;
import com.agriaudit.catalog.StandardImportDtos.ImportIssue;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lee el listado de puntos de control de un fichero Excel (.xlsx) o CSV y lo valida fila a fila. No guarda nada:
 * devuelve los puntos válidos y la lista de problemas (con fila y columna) para que la persona los corrija.
 */
public final class ChecklistParser {

    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final int MAX_POINTS = 3000;
    static final int MAX_REPORTED_ISSUES = 200;

    public record ParsedPoint(int row, String chapterCode, String chapterTitle, String ref, String title,
                              String description, ControlLevel level, String guidance, String evidenceExamples,
                              String applicableScopes) {
    }

    public record Parsed(List<ParsedPoint> points, List<ImportIssue> issues, int totalIssues) {
    }

    private enum Col { CHAPTER, CHAPTER_TITLE, REF, TITLE, DESCRIPTION, LEVEL, GUIDANCE, EVIDENCE, SCOPES }

    private static final Map<String, Col> ALIASES = new HashMap<>();

    static {
        for (String a : new String[]{"capitulo", "codigocapitulo", "chapter", "chaptercode"}) ALIASES.put(a, Col.CHAPTER);
        for (String a : new String[]{"titulocapitulo", "titulodelcapitulo", "nombrecapitulo", "nombredelcapitulo", "chaptertitle"}) ALIASES.put(a, Col.CHAPTER_TITLE);
        for (String a : new String[]{"referencia", "ref", "codigo", "numero"}) ALIASES.put(a, Col.REF);
        for (String a : new String[]{"titulo", "title", "requisito", "puntodecontrol"}) ALIASES.put(a, Col.TITLE);
        for (String a : new String[]{"descripcion", "description"}) ALIASES.put(a, Col.DESCRIPTION);
        for (String a : new String[]{"nivel", "level", "obligatoriedad"}) ALIASES.put(a, Col.LEVEL);
        for (String a : new String[]{"guia", "guidance", "orientacion"}) ALIASES.put(a, Col.GUIDANCE);
        for (String a : new String[]{"evidencias", "evidencia", "evidenceexamples", "ejemplosdeevidencia"}) ALIASES.put(a, Col.EVIDENCE);
        for (String a : new String[]{"alcance", "ambito", "scope", "scopes", "pilar"}) ALIASES.put(a, Col.SCOPES);
    }

    private ChecklistParser() {
    }

    public static Parsed parse(String fileName, byte[] content) {
        Issues issues = new Issues();
        if (content == null || content.length == 0) {
            issues.add(0, "Fichero", "El fichero está vacío");
            return new Parsed(List.of(), issues.list, issues.total);
        }
        if (content.length > MAX_BYTES) {
            issues.add(0, "Fichero", "El fichero supera el tamaño máximo de 5 MB");
            return new Parsed(List.of(), issues.list, issues.total);
        }
        List<List<String>> rows;
        try {
            rows = isXlsx(fileName, content) ? readExcel(content) : readCsv(content);
        } catch (IOException | RuntimeException e) {
            issues.add(0, "Fichero", "No se ha podido leer el fichero: " + (isXlsx(fileName, content)
                    ? "no es un Excel (.xlsx) válido" : "no es un CSV válido en UTF-8"));
            return new Parsed(List.of(), issues.list, issues.total);
        }
        return toPoints(rows, issues);
    }

    // ------------------------------------------------------------------ lectura

    private static boolean isXlsx(String fileName, byte[] c) {
        boolean zip = c.length > 3 && c[0] == 'P' && c[1] == 'K';
        return zip || (fileName != null && fileName.toLowerCase().endsWith(".xlsx"));
    }

    private static List<List<String>> readExcel(byte[] content) throws IOException {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet sheet = null;
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                String name = normalize(wb.getSheetName(i));
                if (name.contains("puntos") || name.contains("control")) {
                    sheet = wb.getSheetAt(i);
                    break;
                }
            }
            if (sheet == null) {
                sheet = wb.getSheetAt(wb.getNumberOfSheets() > 1 ? 1 : 0);
            }
            DataFormatter fmt = new DataFormatter();
            List<List<String>> out = new ArrayList<>();
            int last = Math.min(sheet.getLastRowNum(), MAX_POINTS + 50);
            for (int r = sheet.getFirstRowNum(); r <= last; r++) {
                Row row = sheet.getRow(r);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    int n = Math.max(row.getLastCellNum(), 0);
                    for (int c = 0; c < n; c++) {
                        Cell cell = row.getCell(c);
                        cells.add(cell == null ? "" : fmt.formatCellValue(cell).trim());
                    }
                }
                out.add(cells);
            }
            return out;
        }
    }

    static List<List<String>> readCsv(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        int firstLine = text.indexOf('\n') < 0 ? text.length() : text.indexOf('\n');
        String header = text.substring(0, firstLine);
        char delimiter = ';';
        int best = count(header, ';');
        if (count(header, ',') > best) {
            delimiter = ',';
            best = count(header, ',');
        }
        if (count(header, '\t') > best) {
            delimiter = '\t';
        }
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == delimiter) {
                row.add(cell.toString().trim());
                cell.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString().trim());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                if (rows.size() > MAX_POINTS + 50) {
                    return rows;
                }
            } else {
                cell.append(ch);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString().trim());
            rows.add(row);
        }
        return rows;
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ validación

    private static Parsed toPoints(List<List<String>> rows, Issues issues) {
        int headerIndex = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).stream().anyMatch(s -> !s.isBlank())) {
                headerIndex = i;
                break;
            }
        }
        if (headerIndex < 0) {
            issues.add(0, "Fichero", "El fichero no tiene datos");
            return new Parsed(List.of(), issues.list, issues.total);
        }
        Map<Col, Integer> columns = new LinkedHashMap<>();
        List<String> header = rows.get(headerIndex);
        for (int c = 0; c < header.size(); c++) {
            Col col = ALIASES.get(normalize(header.get(c)));
            if (col != null) {
                columns.putIfAbsent(col, c);
            }
        }
        for (Col required : new Col[]{Col.CHAPTER, Col.REF, Col.TITLE, Col.LEVEL}) {
            if (!columns.containsKey(required)) {
                issues.add(headerIndex + 1, label(required), "Falta la columna «" + label(required)
                        + "» en la fila de encabezados");
            }
        }
        if (issues.total > 0) {
            return new Parsed(List.of(), issues.list, issues.total);
        }

        List<ParsedPoint> points = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String, String> chapterTitles = new HashMap<>();
        for (int i = headerIndex + 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            if (r.stream().allMatch(String::isBlank)) {
                continue;
            }
            int line = i + 1;
            if (points.size() >= MAX_POINTS) {
                issues.add(line, "Fichero", "Se superan los " + MAX_POINTS + " puntos de control permitidos");
                break;
            }
            String chapter = cell(r, columns, Col.CHAPTER);
            String chapterTitle = cell(r, columns, Col.CHAPTER_TITLE);
            String ref = cell(r, columns, Col.REF);
            String title = cell(r, columns, Col.TITLE);
            String levelText = cell(r, columns, Col.LEVEL);
            int before = issues.total;
            require(issues, line, "Capítulo", chapter, 30);
            require(issues, line, "Referencia", ref, 40);
            require(issues, line, "Título", title, 400);
            optional(issues, line, "Título del capítulo", chapterTitle, 300);
            optional(issues, line, "Alcance", cell(r, columns, Col.SCOPES), 200);
            ControlLevel level = level(levelText);
            if (levelText.isBlank()) {
                issues.add(line, "Nivel", "Falta el nivel");
            } else if (level == null) {
                issues.add(line, "Nivel", "Nivel no reconocido: «" + levelText + "». Usa: Obligación mayor, "
                        + "Obligación menor, Recomendación, Núcleo, Mejora nivel 1 o Mejora nivel 2");
            }
            if (!chapter.isBlank() && !chapterTitle.isBlank()) {
                String known = chapterTitles.putIfAbsent(chapter, chapterTitle);
                if (known != null && !known.equals(chapterTitle)) {
                    issues.add(line, "Título del capítulo", "El capítulo «" + chapter
                            + "» ya tiene otro título en filas anteriores");
                }
            }
            if (!chapter.isBlank() && !ref.isBlank() && !seen.add(chapter + "\u0000" + ref)) {
                issues.add(line, "Referencia", "La referencia «" + ref + "» está repetida en el capítulo «"
                        + chapter + "»");
            }
            if (issues.total == before) {
                points.add(new ParsedPoint(line, chapter, chapterTitles.getOrDefault(chapter, chapterTitle), ref,
                        title, cell(r, columns, Col.DESCRIPTION), level, cell(r, columns, Col.GUIDANCE),
                        cell(r, columns, Col.EVIDENCE), cell(r, columns, Col.SCOPES)));
            }
        }
        if (points.isEmpty() && issues.total == 0) {
            issues.add(0, "Fichero", "No hay ningún punto de control");
        }
        return new Parsed(points, issues.list, issues.total);
    }

    private static String cell(List<String> row, Map<Col, Integer> columns, Col col) {
        Integer idx = columns.get(col);
        return idx == null || idx >= row.size() ? "" : row.get(idx);
    }

    private static void require(Issues issues, int row, String column, String value, int max) {
        if (value.isBlank()) {
            issues.add(row, column, "Falta el valor de «" + column + "»");
        } else {
            optional(issues, row, column, value, max);
        }
    }

    private static void optional(Issues issues, int row, String column, String value, int max) {
        if (value.length() > max) {
            issues.add(row, column, "«" + column + "» supera los " + max + " caracteres");
        }
    }

    private static String label(Col c) {
        return switch (c) {
            case CHAPTER -> "Capítulo";
            case REF -> "Referencia";
            case TITLE -> "Título";
            case LEVEL -> "Nivel";
            default -> c.name();
        };
    }

    /** Nivel a partir de su nombre en español o inglés (sin distinguir mayúsculas ni acentos). */
    public static ControlLevel level(String text) {
        String n = normalize(text);
        return switch (n) {
            case "majormust", "obligacionmayor", "mayor", "obligatoriamayor", "mustmayor" -> ControlLevel.MAJOR_MUST;
            case "minormust", "obligacionmenor", "menor", "obligatoriamenor", "mustmenor" -> ControlLevel.MINOR_MUST;
            case "recommendation", "recomendacion", "recomendado" -> ControlLevel.RECOMMENDATION;
            case "core", "nucleo", "basico" -> ControlLevel.CORE;
            case "improvementl1", "mejoranivel1", "mejoral1", "l1" -> ControlLevel.IMPROVEMENT_L1;
            case "improvementl2", "mejoranivel2", "mejoral2", "l2" -> ControlLevel.IMPROVEMENT_L2;
            default -> null;
        };
    }

    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase()
                .replaceAll("[^a-z0-9]", "");
    }

    /** Lista acotada de problemas que sigue contando el total. */
    private static final class Issues {
        final List<ImportIssue> list = new ArrayList<>();
        int total;

        void add(int row, String column, String message) {
            total++;
            if (list.size() < MAX_REPORTED_ISSUES) {
                list.add(new ImportIssue(row, column, message));
            }
        }
    }
}

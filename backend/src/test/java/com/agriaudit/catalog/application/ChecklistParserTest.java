package com.agriaudit.catalog.application;

import com.agriaudit.catalog.ControlLevel;
import com.agriaudit.catalog.StandardImportDtos.ImportMetadata;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChecklistParserTest {

    private static byte[] csv(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void laPlantillaSeLeeSinProblemas() {
        var parsed = ChecklistParser.parse("plantilla.xlsx", ImportTemplate.build());

        assertThat(parsed.issues()).isEmpty();
        assertThat(parsed.points()).hasSize(3);
        assertThat(parsed.points().get(0).level()).isEqualTo(ControlLevel.MAJOR_MUST);
        assertThat(parsed.points().get(2).level()).isEqualTo(ControlLevel.RECOMMENDATION);
        assertThat(parsed.points().get(0).chapterTitle()).isEqualTo("Capítulo de ejemplo");
    }

    @Test
    void leeUnCsvConPuntoYComaComillasYBom() {
        String text = "﻿Capítulo;Título del capítulo;Referencia;Título;Descripción;Nivel\n"
                + "1;Gestión;1.1;\"Título con; punto y coma\";\"Línea 1\nLínea 2\";Obligación mayor\n"
                + "1;Gestión;1.2;\"Dice \"\"hola\"\"\";;Recomendación\n";

        var parsed = ChecklistParser.parse("listado.csv", csv(text));

        assertThat(parsed.issues()).isEmpty();
        assertThat(parsed.points()).hasSize(2);
        assertThat(parsed.points().get(0).title()).isEqualTo("Título con; punto y coma");
        assertThat(parsed.points().get(0).description()).isEqualTo("Línea 1\nLínea 2");
        assertThat(parsed.points().get(1).title()).isEqualTo("Dice \"hola\"");
    }

    @Test
    void detectaLaComaComoSeparadorYAceptaEncabezadosEnIngles() {
        var parsed = ChecklistParser.parse("l.csv", csv("chapter,ref,title,level\n1,1.1,Requisito,Major Must\n"));

        assertThat(parsed.issues()).isEmpty();
        assertThat(parsed.points().get(0).level()).isEqualTo(ControlLevel.MAJOR_MUST);
    }

    @Test
    void leeUnExcelPropio() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("Datos");
            String[] head = {"Capítulo", "Título del capítulo", "Referencia", "Título", "Nivel", "Alcance"};
            Row h = s.createRow(0);
            for (int i = 0; i < head.length; i++) {
                h.createCell(i).setCellValue(head[i]);
            }
            Row r = s.createRow(1);
            r.createCell(0).setCellValue(7);       // número, no texto
            r.createCell(1).setCellValue("Siete");
            r.createCell(2).setCellValue("7.1");
            r.createCell(3).setCellValue("Requisito siete");
            r.createCell(4).setCellValue("Obligación menor");
            r.createCell(5).setCellValue("LABOUR");
            wb.write(out);

            var parsed = ChecklistParser.parse("mio.xlsx", out.toByteArray());

            assertThat(parsed.issues()).isEmpty();
            assertThat(parsed.points().get(0).chapterCode()).isEqualTo("7");
            assertThat(parsed.points().get(0).applicableScopes()).isEqualTo("LABOUR");
        }
    }

    @Test
    void senalaFilaYColumnaDeCadaProblema() {
        String text = "Capítulo;Título del capítulo;Referencia;Título;Nivel\n"
                + "1;Uno;1.1;Correcto;Obligación mayor\n"
                + "1;Uno;1.1;Repetida;Obligación mayor\n"      // referencia repetida (fila 3)
                + "1;Otro título;1.2;Título distinto;Menor\n"   // título del capítulo en conflicto (fila 4)
                + ";Uno;1.3;Sin capítulo;Menor\n"              // falta el capítulo (fila 5)
                + "1;Uno;1.4;Nivel raro;Importantísimo\n"      // nivel no reconocido (fila 6)
                + "1;Uno;1.5;" + "x".repeat(401) + ";Menor\n";  // título demasiado largo (fila 7)

        var parsed = ChecklistParser.parse("l.csv", csv(text));

        assertThat(parsed.points()).hasSize(1);
        assertThat(parsed.issues()).extracting(i -> i.row() + ":" + i.column()).containsExactly(
                "3:Referencia", "4:Título del capítulo", "5:Capítulo", "6:Nivel", "7:Título");
        assertThat(parsed.totalIssues()).isEqualTo(5);
    }

    @Test
    void avisaSiFaltanColumnasObligatorias() {
        var parsed = ChecklistParser.parse("l.csv", csv("Capítulo;Título\n1;Algo\n"));

        assertThat(parsed.issues()).extracting(i -> i.column()).contains("Referencia", "Nivel");
        assertThat(parsed.points()).isEmpty();
    }

    @Test
    void rechazaFicherosVaciosGrandesOIlegibles() {
        assertThat(ChecklistParser.parse("l.csv", new byte[0]).issues()).hasSize(1);
        assertThat(ChecklistParser.parse("l.csv", new byte[ChecklistParser.MAX_BYTES + 1]).issues().get(0).message())
                .contains("5 MB");
        assertThat(ChecklistParser.parse("roto.xlsx", csv("esto no es un excel")).issues().get(0).message())
                .contains("no es un Excel");
        assertThat(ChecklistParser.parse("l.csv", csv("Capítulo;Referencia;Título;Nivel\n")).issues().get(0).message())
                .contains("No hay ningún punto");
    }

    @Test
    void acotaElNumeroDeProblemasInformados() {
        StringBuilder text = new StringBuilder("Capítulo;Referencia;Título;Nivel\n");
        for (int i = 0; i < 400; i++) {
            text.append("1;").append(i).append(";Título;malo\n");
        }

        var parsed = ChecklistParser.parse("l.csv", csv(text.toString()));

        assertThat(parsed.issues()).hasSize(200);
        assertThat(parsed.totalIssues()).isEqualTo(400);
    }

    @Test
    void entiendeLosNivelesEnEspanolEIngles() {
        assertThat(ChecklistParser.level("Obligación MAYOR")).isEqualTo(ControlLevel.MAJOR_MUST);
        assertThat(ChecklistParser.level("minor must")).isEqualTo(ControlLevel.MINOR_MUST);
        assertThat(ChecklistParser.level("Recomendación")).isEqualTo(ControlLevel.RECOMMENDATION);
        assertThat(ChecklistParser.level("Mejora nivel 2")).isEqualTo(ControlLevel.IMPROVEMENT_L2);
        assertThat(ChecklistParser.level("otro")).isNull();
    }

    @Test
    void elCodigoGuardadoIncluyeLaVersionYLaReglaSeValida() {
        assertThat(StandardImportService.storedCode("coop-ifa", "6.1")).isEqualTo("COOP-IFA_V6.1");
        assertThat(StandardImportService.scoringRule(new ImportMetadata("a", "n", "1", null, null, null, null,
                "MAJOR_MINOR", 100.0, 95.5))).contains("\"majorMustMinPct\":100").contains("\"minorMustMinPct\":95.5");
        assertThat(StandardImportService.scoringRule(new ImportMetadata("a", "n", "1", null, null, null, null,
                "REPORT_ONLY", null, null))).contains("reportOnly");
        assertThatThrownBy(() -> StandardImportService.scoringRule(new ImportMetadata("a", "n", "1", null, null, null,
                null, "MAJOR_MINOR", 120.0, 95.0))).hasMessageContaining("entre 0 y 100");
        assertThatThrownBy(() -> StandardImportService.scoringRule(new ImportMetadata("a", "n", "1", null, null, null,
                null, "OTRA", null, null))).hasMessageContaining("Elige una regla");
    }
}

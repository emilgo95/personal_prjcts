package com.agriaudit.privacy.application;

import com.agriaudit.privacy.ExportSink;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Escritor de un ZIP de exportación: los contribuyentes vuelcan en su {@link ExportSink} y al final se añade el LEEME. */
final class ZipExport implements AutoCloseable {

    private final ZipOutputStream zip;
    private final JsonMapper json;
    private final List<String> index = new ArrayList<>();

    ZipExport(OutputStream out, JsonMapper json) {
        this.zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
        this.json = json;
    }

    ExportSink sink() {
        return new ExportSink() {
            @Override
            public void json(String path, Object value) {
                put(path, ZipExport.this.json.writerWithDefaultPrettyPrinter().writeValueAsBytes(normalize(value)));
            }

            @Override
            public void file(String path, byte[] content) {
                put(path, content);
            }
        };
    }

    private void put(String path, byte[] bytes) {
        try {
            zip.putNextEntry(new ZipEntry(path));
            zip.write(bytes);
            zip.closeEntry();
            index.add(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Añade el LEEME con el texto indicado y la lista de ficheros incluidos. */
    void finish(String readmeHeader) throws IOException {
        String readme = readmeHeader + "\nFicheros incluidos:\n" + String.join("\n", index) + "\n";
        zip.putNextEntry(new ZipEntry("LEEME.txt"));
        zip.write(readme.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }

    /** Valores de JDBC a algo que se serialice bien: fechas en ISO y tipos propios de PostgreSQL como texto. */
    static Object normalize(Object value) {
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), normalize(v)));
            return out;
        }
        if (value instanceof List<?> l) {
            return l.stream().map(ZipExport::normalize).toList();
        }
        if (value instanceof Timestamp t) {
            return t.toInstant().toString();
        }
        if (value instanceof java.sql.Date d) {
            return d.toString();
        }
        if (value instanceof java.time.OffsetDateTime o) {
            return o.toInstant().toString();
        }
        if (value != null && value.getClass().getName().equals("org.postgresql.util.PGobject")) {
            return String.valueOf(value);
        }
        return value;
    }
}

package com.agriaudit.service.ocr;

import com.agriaudit.common.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * Cliente del servicio de OCR autoalojado (contenedor "ocr", RapidOCR / PP-OCR).
 * Gratuito, sin clave de API y sin envío de datos a terceros.
 */
@Component
public class OcrClient {

    private static final Logger log = LoggerFactory.getLogger(OcrClient.class);

    private final String baseUrl;
    private final RestClient restClient;

    public OcrClient(@Value("${agriaudit.ocr.url:}") String baseUrl,
                     @Value("${agriaudit.ocr.timeout-seconds:120}") long timeoutSeconds) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(10));
        rf.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().requestFactory(rf).build();
    }

    public boolean isEnabled() {
        return !baseUrl.isBlank();
    }

    public OcrResult recognize(byte[] data, String fileName, String contentType) {
        if (!isEnabled()) {
            throw new BadRequestException("El reconocimiento de texto (OCR) no está configurado en este entorno");
        }
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedResource(data, fileName == null ? "evidencia" : fileName, contentType));
        try {
            return restClient.post()
                    .uri(baseUrl + "/ocr")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(OcrResult.class);
        } catch (Exception e) {
            log.error("Error llamando al servicio de OCR en {}", baseUrl, e);
            throw new BadRequestException("El servicio de OCR no está disponible o no pudo procesar el fichero: "
                    + e.getMessage());
        }
    }

    /** ByteArrayResource con nombre y tipo de fichero para el multipart. */
    static final class NamedResource extends ByteArrayResource {
        private final String filename;
        private final String contentType;

        NamedResource(byte[] data, String filename, String contentType) {
            super(data);
            this.filename = filename;
            this.contentType = contentType;
        }

        @Override
        public String getFilename() {
            return filename;
        }

        String contentType() {
            return contentType;
        }
    }

    /** Respuesta del servicio de OCR. */
    public record OcrResult(
            String engine,
            String fileName,
            int pages,
            boolean truncated,
            int lineCount,
            double meanConfidence,
            long elapsedMs,
            String text,
            List<OcrLine> lines) {

        public String text() {
            return text == null ? "" : text;
        }

        public List<OcrLine> lines() {
            return lines == null ? List.of() : lines;
        }
    }

    public record OcrLine(int page, String text, double confidence, List<Double> box) {
    }
}

package com.agriaudit.documents.infrastructure.render;

import com.agriaudit.common.BadRequestException;
import com.agriaudit.documents.domain.DocumentTemplate;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.util.Locale;
import com.agriaudit.documents.application.DocumentModel;
import com.agriaudit.documents.application.DocumentRenderer;

@Component
public class PdfDocumentRenderer implements DocumentRenderer {

    private final SpringTemplateEngine templateEngine;

    public PdfDocumentRenderer(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    @Override
    public boolean supports(String engine) {
        return "PDF_HTML".equals(engine);
    }

    @Override
    public byte[] render(DocumentTemplate template, DocumentModel model) {
        String templateName = template.getTemplateRef();
        if (templateName == null || templateName.isBlank()) {
            throw new BadRequestException("La plantilla PDF no tiene una referencia de fichero configurada");
        }
        Context context = new Context(new Locale("es", "ES"));
        context.setVariable("m", model);
        context.setVariable("model", model);

        String html = templateEngine.process(templateName, context);
        String xhtml = toXhtml(html);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando el PDF a partir de la plantilla " + templateName, e);
        }
    }

    private String toXhtml(String html) {
        Document doc = Jsoup.parse(html);
        doc.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml)
                .prettyPrint(false);
        return doc.html();
    }
}

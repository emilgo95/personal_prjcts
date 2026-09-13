package com.agriaudit.service.ocr;

import com.agriaudit.common.BadRequestException;
import com.agriaudit.common.NotFoundException;
import com.agriaudit.domain.Attachment;
import com.agriaudit.catalog.domain.ControlPoint;
import com.agriaudit.catalog.domain.ControlLevel;
import com.agriaudit.repo.AttachmentRepository;
import com.agriaudit.catalog.CatalogLookup;
import com.agriaudit.security.SecurityUtils;
import com.agriaudit.service.AuditService;
import com.agriaudit.service.document.DocumentStorageService;
import com.agriaudit.service.ocr.OcrClient.OcrResult;
import com.agriaudit.web.dto.EvidenceAnalysisDtos.EvidenceAnalysis;
import com.agriaudit.web.dto.EvidenceAnalysisDtos.SuggestedFinding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Reconoce el texto de una evidencia adjunta (PDF / imagen / escaneado / notas
 * manuscritas) mediante el servicio de OCR autoalojado y propone, a partir de ese
 * texto, la nota de evidencia, el resultado de cumplimiento y posibles no
 * conformidades. Nada se guarda: el usuario revisa y confirma en el frontal.
 */
@Service
public class EvidenceAnalysisService {

    private static final int MAX_NOTE_CHARS = 2000;

    /** Indicios claros de incumplimiento: basta uno para proponer NO CONFORME. */
    private static final String[] STRONG_NON_COMPLIANT = {
            "no conforme", "no conformidad", "no cumple", "no se cumple", "incumpl",
            "no se evidencia", "no se ha evidenciado", "no dispone", "no existe registro",
            "no realizado", "no registrado", "sin registro", "no se dispone",
            "no procede la certificacion", "hallazgo", "accion correctiva pendiente"
    };
    /** Indicios débiles: se necesitan al menos dos, o que superen a los de conformidad. */
    private static final String[] WEAK_NON_COMPLIANT = {
            "ausencia de", "falta ", "faltan ", "pendiente", "caducad", "vencid",
            "no actualizado", "deficien", "desviacion", "fuera de limite", "supera el limite",
            "no apto"
    };
    private static final String[] COMPLIANT = {
            "conforme", "cumple", "correcto", "verificado", "se evidencia", "disponible",
            "vigente", "actualizado", "sin incidencias", "sin observaciones", "sin hallazgos",
            "aceptable", "dentro de los limites", "realizado correctamente", "implantado",
            "satisfactorio", "resultado favorable"
    };
    private static final String[] NOT_APPLICABLE = {
            "no aplica", "no aplicable", "no procede en esta explotacion"
    };

    private final AuditService auditService;
    private final AttachmentRepository attachments;
    private final CatalogLookup catalog;
    private final DocumentStorageService storage;
    private final OcrClient ocrClient;
    private final double minConfidenceWarn;

    public EvidenceAnalysisService(AuditService auditService, AttachmentRepository attachments,
                                   CatalogLookup catalog, DocumentStorageService storage,
                                   OcrClient ocrClient,
                                   @Value("${agriaudit.ocr.min-confidence-warn:0.55}") double minConfidenceWarn) {
        this.auditService = auditService;
        this.attachments = attachments;
        this.catalog = catalog;
        this.storage = storage;
        this.ocrClient = ocrClient;
        this.minConfidenceWarn = minConfidenceWarn;
    }

    public boolean isEnabled() {
        return ocrClient.isEnabled();
    }

    @Transactional(readOnly = true)
    public EvidenceAnalysis analyze(Long auditId, Long attachmentId, Long controlPointId) {
        auditService.requireAudit(auditId);
        Attachment att = attachments.findByIdAndOrganizationId(attachmentId, SecurityUtils.currentOrganizationId())
                .orElseThrow(() -> new NotFoundException("Adjunto no encontrado: " + attachmentId));

        String ct = att.getContentType() == null ? "" : att.getContentType().toLowerCase();
        if (!ct.startsWith("image/") && !ct.equals("application/pdf")) {
            throw new BadRequestException("Solo se puede reconocer texto de imágenes y PDF");
        }

        byte[] data = storage.load(att.getStorageKey());
        OcrResult ocr = ocrClient.recognize(data, att.getFileName(), att.getContentType());

        String text = ocr.text().trim();
        List<String> warnings = new ArrayList<>();
        if (text.isEmpty()) {
            warnings.add("El OCR no ha detectado texto en el fichero. ¿Está la imagen enfocada y bien iluminada?");
        }
        if (ocr.meanConfidence() > 0 && ocr.meanConfidence() < minConfidenceWarn) {
            warnings.add(String.format(
                    "Confianza del OCR baja (%.0f %%). Revisa el texto extraído con atención antes de confirmar.",
                    ocr.meanConfidence() * 100));
        }
        if (ocr.truncated()) {
            warnings.add("El documento tiene más páginas de las procesadas; solo se ha reconocido el principio.");
        }

        String normalized = stripAccents(text.toLowerCase());
        int strong = count(normalized, STRONG_NON_COMPLIANT);
        int weak = count(normalized, WEAK_NON_COMPLIANT);
        int c = count(normalized, COMPLIANT);
        int na = count(normalized, NOT_APPLICABLE);

        String compliance = null;
        String rationale = null;
        if (!text.isEmpty()) {
            if (na > 0 && na >= strong + weak && na >= c) {
                compliance = "NOT_APPLICABLE";
                rationale = "El texto contiene expresiones del tipo «no aplica».";
            } else if (strong >= 1) {
                compliance = "NON_COMPLIANT";
                rationale = "El texto contiene indicios claros de incumplimiento (" + strong + ").";
            } else if (weak >= 2 && weak > c) {
                compliance = "NON_COMPLIANT";
                rationale = "El texto contiene varios indicios de posible incumplimiento (" + weak + ").";
            } else if (c >= 1 && weak == 0) {
                compliance = "COMPLIANT";
                rationale = "El texto contiene indicios de conformidad (" + c + ") y ninguno de incumplimiento.";
            } else {
                rationale = "El texto no permite deducir con seguridad el resultado; decídelo manualmente.";
            }
        }

        List<SuggestedFinding> findings = new ArrayList<>();
        if ("NON_COMPLIANT".equals(compliance)) {
            String classification = majorLevel(controlPointId) ? "MAJOR" : "MINOR";
            String[] triggers = strong >= 1 ? STRONG_NON_COMPLIANT : WEAK_NON_COMPLIANT;
            findings.add(new SuggestedFinding(
                    "Posible incumplimiento detectado en la evidencia adjunta",
                    excerptAround(text, normalized, triggers)
                            + "\n\n(Texto extraído automáticamente por OCR de «" + att.getFileName()
                            + "». Revísese y edítese antes de guardar.)",
                    classification));
        }

        String note = buildNote(att.getFileName(), text);

        return new EvidenceAnalysis(
                att.getId(), att.getFileName(), ocr.engine(), ocr.pages(), ocr.truncated(),
                round(ocr.meanConfidence()), text, note, compliance, rationale, findings, warnings);
    }

    // ---- helpers ----

    private boolean majorLevel(Long controlPointId) {
        if (controlPointId == null) {
            return false;
        }
        return catalog.findControlPoint(controlPointId)
                .map(ControlPoint::getLevel)
                .map(l -> l == ControlLevel.MAJOR_MUST || l == ControlLevel.CORE)
                .orElse(false);
    }

    private String buildNote(String fileName, String text) {
        if (text.isEmpty()) {
            return "";
        }
        String collapsed = text.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
        if (collapsed.length() > MAX_NOTE_CHARS) {
            collapsed = collapsed.substring(0, MAX_NOTE_CHARS).trim() + " […]";
        }
        return "Evidencia «" + fileName + "» (texto reconocido por OCR):\n" + collapsed;
    }

    private static int count(String haystack, String[] needles) {
        int total = 0;
        for (String n : needles) {
            int from = 0;
            int idx;
            while ((idx = haystack.indexOf(n, from)) >= 0) {
                total++;
                from = idx + n.length();
            }
        }
        return total;
    }

    private static String excerptAround(String original, String normalized, String[] needles) {
        int pos = -1;
        for (String n : needles) {
            int i = normalized.indexOf(n);
            if (i >= 0 && (pos < 0 || i < pos)) {
                pos = i;
            }
        }
        if (pos < 0) {
            return original.length() > 400 ? original.substring(0, 400) + " […]" : original;
        }
        int start = Math.max(0, pos - 160);
        int end = Math.min(original.length(), pos + 200);
        String seg = original.substring(start, end).trim();
        return (start > 0 ? "… " : "") + seg + (end < original.length() ? " …" : "");
    }

    private static String stripAccents(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}

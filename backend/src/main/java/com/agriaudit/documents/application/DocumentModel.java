package com.agriaudit.documents.application;

import com.agriaudit.audit.AuditDtos.AuditDetailDto;
import com.agriaudit.audit.AuditDtos.AuditItemResultDto;
import com.agriaudit.catalog.CatalogDtos.ChapterDto;
import com.agriaudit.catalog.CatalogDtos.StandardDetailDto;
import com.agriaudit.fieldbook.FarmDtos.FarmDto;
import com.agriaudit.findings.FindingDtos.FindingDto;
import com.agriaudit.audit.ScoreDtos.ScoreResult;

import java.util.List;
import java.util.Map;

/**
 * Agrega toda la información necesaria para generar cualquier documento de auditoría.
 */
public record DocumentModel(
        String organizationName,
        String generatedBy,
        String generatedAt,
        String documentTitle,
        AuditDetailDto audit,
        FarmDto farm,
        StandardDetailDto standard,
        List<ChapterDto> chapters,
        List<AuditItemResultDto> results,
        Map<Long, AuditItemResultDto> resultsByControlPoint,
        List<FindingDto> findings,
        ScoreResult score,
        Map<Long, List<EvidenceRef>> evidenceByControlPoint,
        Map<Long, List<EvidenceRef>> evidenceByFinding,
        List<EvidenceRef> auditEvidence,
        String disclaimer) {

    /** Referencia a un adjunto de evidencia para los documentos. dataUri solo para imágenes embebibles. */
    public record EvidenceRef(
            Long id,
            String fileName,
            String contentType,
            Long sizeBytes,
            String uploadedBy,
            boolean image,
            String dataUri) {

        public boolean hasImage() {
            return image && dataUri != null;
        }
    }

    public static final String DEFAULT_DISCLAIMER =
            "Documento de trabajo generado por AgriAudit Docs a partir de datos introducidos por el usuario. "
            + "Los textos de los puntos de control son un resumen divulgativo elaborado a partir de fuentes públicas "
            + "y no constituyen una reproducción literal de los documentos normativos ni de los estándares de "
            + "certificación, que están sujetos a derechos de autor de sus propietarios. Verifique siempre los "
            + "requisitos con la versión oficial vigente del estándar aplicable.";
}

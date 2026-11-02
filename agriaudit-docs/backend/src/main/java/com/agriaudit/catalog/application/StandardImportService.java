package com.agriaudit.catalog.application;

import com.agriaudit.catalog.ControlLevel;
import com.agriaudit.catalog.StandardImportDtos.ImportIssue;
import com.agriaudit.catalog.StandardImportDtos.ImportMetadata;
import com.agriaudit.catalog.StandardImportDtos.ImportPreview;
import com.agriaudit.catalog.StandardImportDtos.PreviewPoint;
import com.agriaudit.catalog.CatalogDtos.StandardSummaryDto;
import com.agriaudit.catalog.CatalogService;
import com.agriaudit.catalog.domain.AuditStandard;
import com.agriaudit.catalog.domain.ControlPoint;
import com.agriaudit.catalog.domain.StandardCategory;
import com.agriaudit.catalog.domain.StandardChapter;
import com.agriaudit.catalog.infrastructure.persistence.AuditStandardRepository;
import com.agriaudit.catalog.infrastructure.persistence.ControlPointRepository;
import com.agriaudit.catalog.infrastructure.persistence.HiddenStandardRepository;
import com.agriaudit.catalog.infrastructure.persistence.StandardChapterRepository;
import com.agriaudit.common.BadRequestException;
import com.agriaudit.common.ConflictException;
import com.agriaudit.common.NotFoundException;
import com.agriaudit.identity.SecurityUtils;
import com.agriaudit.trace.ChangeEntry;
import com.agriaudit.trace.ChangeRecorder;
import com.agriaudit.trace.Changes;
import com.agriaudit.trace.TraceAction;
import com.agriaudit.trace.TraceEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Importa el listado de un estándar propio de la organización (RF-CAT-01…05): valida los datos del estándar y el
 * fichero, y lo guarda como un estándar privado de la organización. Una versión nueva es un estándar nuevo: las
 * auditorías ya hechas siguen con la anterior.
 */
@Service
@Transactional
public class StandardImportService {

    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{1,39}");

    private final AuditStandardRepository standards;
    private final ControlPointRepository controlPoints;
    private final HiddenStandardRepository hidden;
    private final StandardChapterRepository chapterRepository;
    private final CatalogService catalog;
    private final ChangeRecorder trace;

    public StandardImportService(AuditStandardRepository standards, ControlPointRepository controlPoints,
                                 HiddenStandardRepository hidden, StandardChapterRepository chapterRepository,
                                 CatalogService catalog, ChangeRecorder trace) {
        this.chapterRepository = chapterRepository;
        this.standards = standards;
        this.controlPoints = controlPoints;
        this.hidden = hidden;
        this.catalog = catalog;
        this.trace = trace;
    }

    @Transactional(readOnly = true)
    public ImportPreview preview(ImportMetadata meta, String fileName, byte[] content) {
        return validate(meta, fileName, content).preview();
    }

    public StandardSummaryDto importStandard(ImportMetadata meta, String fileName, byte[] content) {
        Validation v = validate(meta, fileName, content);
        if (!v.preview().valid()) {
            throw new BadRequestException("El listado tiene " + v.preview().totalIssues()
                    + " problemas; corrígelos y vuelve a importar");
        }
        Long orgId = SecurityUtils.currentOrganizationId();
        AuditStandard s = new AuditStandard();
        s.setCode(v.storedCode());
        s.setVersion(meta.version().trim());
        s.setName(meta.name().trim());
        s.setOwnerBody(blankToNull(meta.ownerBody()));
        s.setCategory(v.category());
        s.setDescription(blankToNull(meta.description()));
        s.setOfficialUrl(blankToNull(meta.officialUrl()));
        s.setScoringRule(v.scoringRule());
        s.setOrganizationId(orgId);
        s.setImportedAt(Instant.now());
        s.setImportedBy(SecurityUtils.currentUsername());
        s = standards.save(s);

        Map<String, StandardChapter> chapters = new LinkedHashMap<>();
        int chapterOrder = 0;
        int pointOrder = 0;
        for (ChecklistParser.ParsedPoint p : v.points()) {
            StandardChapter ch = chapters.get(p.chapterCode());
            if (ch == null) {
                ch = new StandardChapter();
                ch.setStandard(s);
                ch.setCode(p.chapterCode());
                ch.setTitle(p.chapterTitle() == null || p.chapterTitle().isBlank() ? p.chapterCode() : p.chapterTitle());
                ch.setSortOrder(chapterOrder++);
                chapters.put(p.chapterCode(), ch);
                ch = persistChapter(ch);
                chapters.put(p.chapterCode(), ch);
            }
            ControlPoint cp = new ControlPoint();
            cp.setChapter(ch);
            cp.setRef(p.ref());
            cp.setTitle(p.title());
            cp.setDescription(blankToNull(p.description()));
            cp.setLevel(p.level());
            cp.setGuidance(blankToNull(p.guidance()));
            cp.setEvidenceExamples(blankToNull(p.evidenceExamples()));
            cp.setApplicableScopes(blankToNull(p.applicableScopes()));
            cp.setSortOrder(pointOrder++);
            controlPoints.save(cp);
        }
        trace.record(ChangeEntry.of(TraceEntity.STANDARD, s.getId(), s.getName() + " " + s.getVersion(), null,
                TraceAction.CREATE, "Importó el estándar «" + s.getName() + "» (versión " + s.getVersion() + ") con "
                        + v.points().size() + " puntos de control",
                Changes.create().initial("name", "Nombre", s.getName()).initial("version", "Versión", s.getVersion())
                        .initial("code", "Código", s.getCode())
                        .initial("points", "Puntos de control", v.points().size())
                        .initial("file", "Fichero", fileName).list()));
        Long standardId = s.getId();
        return catalog.listStandards(true).stream().filter(x -> x.id().equals(standardId)).findFirst().orElseThrow();
    }

    private StandardChapter persistChapter(StandardChapter ch) {
        return chapterRepository.save(ch);
    }

    /** Archiva o recupera un estándar propio: un archivado no se ofrece al crear auditorías nuevas. */
    public void setArchived(String code, boolean archived) {
        Long orgId = SecurityUtils.currentOrganizationId();
        AuditStandard s = standards.findVisibleByCode(code, orgId)
                .orElseThrow(() -> new NotFoundException("Estándar no encontrado: " + code));
        if (!s.isCustom()) {
            throw new BadRequestException("Solo se pueden archivar los estándares propios de la organización");
        }
        if (s.isArchived() == archived) {
            return;
        }
        s.setArchived(archived);
        standards.save(s);
        trace.record(ChangeEntry.of(TraceEntity.STANDARD, s.getId(), s.getName() + " " + s.getVersion(), null,
                TraceAction.UPDATE, (archived ? "Archivó" : "Recuperó") + " el estándar «" + s.getName()
                        + "» (versión " + s.getVersion() + ")",
                Changes.create().track("archived", "Estado", archived ? "Activo" : "Archivado",
                        archived ? "Archivado" : "Activo").list()));
    }

    /** Oculta o muestra un estándar común (demostración) en las pantallas de esta organización. */
    public void setHidden(String code, boolean hide) {
        Long orgId = SecurityUtils.currentOrganizationId();
        AuditStandard s = standards.findVisibleByCode(code, orgId)
                .orElseThrow(() -> new NotFoundException("Estándar no encontrado: " + code));
        if (s.isCustom()) {
            throw new BadRequestException("Los estándares propios se archivan; solo se ocultan los de demostración");
        }
        boolean wasHidden = hidden.hiddenBy(orgId).contains(s.getId());
        if (wasHidden == hide) {
            return;
        }
        if (hide) {
            hidden.hide(orgId, s.getId());
        } else {
            hidden.show(orgId, s.getId());
        }
        trace.record(ChangeEntry.of(TraceEntity.STANDARD, s.getId(), s.getName() + " " + s.getVersion(), null,
                TraceAction.UPDATE, (hide ? "Ocultó" : "Volvió a mostrar") + " el estándar de demostración «"
                        + s.getName() + "»",
                Changes.create().track("visibility", "Visibilidad", hide ? "Visible" : "Oculto",
                        hide ? "Oculto" : "Visible").list()));
    }

    // ------------------------------------------------------------------ validación

    private record Validation(ImportPreview preview, String storedCode, StandardCategory category, String scoringRule,
                              List<ChecklistParser.ParsedPoint> points) {
    }

    private Validation validate(ImportMetadata meta, String fileName, byte[] content) {
        List<ImportIssue> meta_issues = new ArrayList<>();
        if (meta == null) {
            throw new BadRequestException("Faltan los datos del estándar");
        }
        String code = trim(meta.code());
        String version = trim(meta.version());
        if (!CODE.matcher(code).matches()) {
            meta_issues.add(new ImportIssue(0, "Código", "El código debe tener entre 2 y 40 caracteres: letras, "
                    + "números, punto, guion o guion bajo"));
        }
        if (trim(meta.name()).isEmpty() || trim(meta.name()).length() > 200) {
            meta_issues.add(new ImportIssue(0, "Nombre", "El nombre es obligatorio y no puede superar 200 caracteres"));
        }
        if (version.isEmpty() || version.length() > 30) {
            meta_issues.add(new ImportIssue(0, "Versión", "La versión es obligatoria (máximo 30 caracteres): "
                    + "permite importar versiones nuevas sin alterar las auditorías anteriores"));
        }
        if (trim(meta.ownerBody()).length() > 200) {
            meta_issues.add(new ImportIssue(0, "Organismo", "Supera los 200 caracteres"));
        }
        if (trim(meta.officialUrl()).length() > 400) {
            meta_issues.add(new ImportIssue(0, "Enlace oficial", "Supera los 400 caracteres"));
        } else if (!trim(meta.officialUrl()).isEmpty() && !isWebLink(trim(meta.officialUrl()))) {
            meta_issues.add(new ImportIssue(0, "Enlace oficial", "Debe ser una dirección web que empiece por "
                    + "http:// o https://"));
        }
        StandardCategory category = StandardCategory.GAP;
        if (!trim(meta.category()).isEmpty()) {
            try {
                category = StandardCategory.valueOf(meta.category().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                meta_issues.add(new ImportIssue(0, "Categoría", "Categoría no válida: " + meta.category()));
            }
        }
        String storedCode = storedCode(code, version);
        Long orgId = SecurityUtils.currentOrganizationId();
        if (CODE.matcher(code).matches() && !version.isEmpty()) {
            if (standards.existsByCodeAndOrganizationIdIsNull(storedCode) || standards.existsByCodeAndOrganizationIdIsNull(
                    code.toUpperCase(Locale.ROOT))) {
                meta_issues.add(new ImportIssue(0, "Código", "Ese código lo usa un estándar de demostración; "
                        + "elige uno propio"));
            } else if (standards.existsByCodeAndOrganizationId(storedCode, orgId)) {
                meta_issues.add(new ImportIssue(0, "Versión", "Ya has importado la versión «" + version
                        + "» de este estándar; usa otra versión"));
            }
        }
        String rule = null;
        try {
            rule = scoringRule(meta);
        } catch (IllegalArgumentException e) {
            meta_issues.add(new ImportIssue(0, "Regla de puntuación", e.getMessage()));
        }

        ChecklistParser.Parsed parsed = ChecklistParser.parse(fileName, content);
        List<ImportIssue> issues = new ArrayList<>(meta_issues);
        issues.addAll(parsed.issues());
        int total = meta_issues.size() + parsed.totalIssues();
        if (rule != null && meta.scoringType() != null && "MAJOR_MINOR".equals(meta.scoringType().toUpperCase(Locale.ROOT))
                && parsed.points().stream().noneMatch(p -> p.level() == ControlLevel.MAJOR_MUST
                || p.level() == ControlLevel.MINOR_MUST) && total == meta_issues.size() + parsed.totalIssues()
                && !parsed.points().isEmpty()) {
            issues.add(new ImportIssue(0, "Regla de puntuación", "La regla «mayores y menores» necesita puntos con "
                    + "nivel Obligación mayor u Obligación menor"));
            total++;
        }
        Map<String, Integer> levels = new LinkedHashMap<>();
        parsed.points().forEach(p -> levels.merge(p.level().name(), 1, Integer::sum));
        long chapters = parsed.points().stream().map(ChecklistParser.ParsedPoint::chapterCode).distinct().count();
        List<PreviewPoint> sample = parsed.points().stream().limit(10).map(p -> new PreviewPoint(p.row(),
                p.chapterCode(), p.ref(), p.title(), p.level().name())).toList();
        ImportPreview preview = new ImportPreview(total == 0, storedCode, (int) chapters, parsed.points().size(),
                levels, issues.size() > 200 ? issues.subList(0, 200) : issues, total, sample);
        return new Validation(preview, storedCode, category, rule, parsed.points());
    }

    /** Código con el que se guarda: el elegido más la versión (una versión nueva es un estándar nuevo). */
    static String storedCode(String code, String version) {
        String c = code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9._-]", "_");
        String v = version.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9.]", "_");
        return v.isEmpty() ? c : c + "_V" + v;
    }

    static String scoringRule(ImportMetadata meta) {
        String type = trim(meta.scoringType()).toUpperCase(Locale.ROOT);
        return switch (type) {
            case "MAJOR_MINOR" -> {
                double major = pct(meta.majorMustMinPct(), "mayores");
                double minor = pct(meta.minorMustMinPct(), "menores");
                yield "{\"type\":\"MAJOR_MINOR\",\"majorMustMinPct\":" + number(major)
                        + ",\"minorMustMinPct\":" + number(minor)
                        + ",\"levels\":[\"MAJOR_MUST\",\"MINOR_MUST\",\"RECOMMENDATION\"]}";
            }
            case "LEVEL_COMPLETION" -> "{\"type\":\"LEVEL_COMPLETION\",\"reportOnly\":true}";
            case "PILLAR" -> "{\"type\":\"PILLAR\",\"reportOnly\":true}";
            case "REPORT_ONLY" -> "{\"type\":\"REPORT_ONLY\",\"reportOnly\":true}";
            default -> throw new IllegalArgumentException("Elige una regla: mayores y menores, nivel de cumplimiento, "
                    + "pilares o solo informe");
        };
    }

    private static double pct(Double value, String what) {
        if (value == null || value < 0 || value > 100) {
            throw new IllegalArgumentException("El porcentaje mínimo de " + what + " debe estar entre 0 y 100");
        }
        return value;
    }

    private static String number(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    /** Solo direcciones web completas: el enlace se muestra como «Fuente oficial» y no debe ejecutar nada. */
    static boolean isWebLink(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

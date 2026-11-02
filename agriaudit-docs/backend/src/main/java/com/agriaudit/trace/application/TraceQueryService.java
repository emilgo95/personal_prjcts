package com.agriaudit.trace.application;

import com.agriaudit.common.NotFoundException;
import com.agriaudit.trace.ActorDirectory;
import com.agriaudit.trace.ActorProvider;
import com.agriaudit.trace.FieldChange;
import com.agriaudit.trace.TraceActor;
import com.agriaudit.trace.TraceAudience;
import com.agriaudit.trace.TraceDtos.TraceEntryDto;
import com.agriaudit.trace.TraceDtos.TraceFilter;
import com.agriaudit.trace.TraceDtos.TracePage;
import com.agriaudit.trace.infrastructure.persistence.StoredEntry;
import com.agriaudit.trace.infrastructure.persistence.TraceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Consulta de la traza con el alcance de cada rol: administración y calidad ven toda la organización; la auditora,
 * solo lo de sus auditorías; el lector, nada (lo impide el controlador).
 */
@Service
@Transactional(readOnly = true)
public class TraceQueryService {

    private static final TypeReference<List<FieldChange>> CHANGES = new TypeReference<>() {
    };

    private final TraceRepository repository;
    private final ActorProvider actors;
    private final TraceAudience audience;
    private final ActorDirectory directory;
    private final JsonMapper json;

    public TraceQueryService(TraceRepository repository, ActorProvider actors, TraceAudience audience,
                             ActorDirectory directory, JsonMapper json) {
        this.directory = directory;
        this.repository = repository;
        this.actors = actors;
        this.audience = audience;
        this.json = json;
    }

    public TracePage search(TraceFilter filter, int page, int size) {
        return search(filter, page, size, false);
    }

    /** {@code related}: incluye las entradas cuyo padre es la entidad filtrada (historial de una NC). */
    public TracePage search(TraceFilter filter, int page, int size, boolean related) {
        TraceActor actor = actors.current();
        Set<Long> visible = visibleAudits(actor);
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);
        TraceFilter effective = resolveUser(actor.organizationId(), filter);
        long total = repository.count(actor.organizationId(), effective, visible, related);
        List<TraceEntryDto> content = toDtos(repository.search(actor.organizationId(), effective, visible, related,
                safePage * safeSize, safeSize));
        return new TracePage(content, safePage, safeSize, total, (int) Math.ceil(total / (double) safeSize));
    }

    /** Historial de una auditoría; una auditora que no la ve recibe «no encontrada». */
    public TracePage forAudit(Long auditId, int page, int size) {
        TraceActor actor = actors.current();
        Set<Long> visible = visibleAudits(actor);
        if (visible != null && !visible.contains(auditId)) {
            throw new NotFoundException("Auditoría no encontrada: " + auditId);
        }
        return search(new TraceFilter(auditId, null, null, null, null, null, null), page, size);
    }

    /** Todas las entradas que cumplen el filtro (exportación), en orden cronológico, sin cargar todo en memoria. */
    public void export(TraceFilter filter, Consumer<TraceEntryDto> sink) {
        TraceActor actor = actors.current();
        Set<Long> visible = visibleAudits(actor);
        TraceFilter effective = resolveUser(actor.organizationId(), filter);
        int page = 0;
        while (true) {
            List<StoredEntry> batch = repository.search(actor.organizationId(), effective, visible, false,
                    page * 500, 500);
            if (batch.isEmpty()) {
                return;
            }
            List<StoredEntry> ordered = new ArrayList<>(batch);
            java.util.Collections.reverse(ordered);
            toDtos(ordered).forEach(sink);
            page++;
        }
    }

    /** El texto de usuario se busca en el directorio actual de usuarios (la traza solo guarda el código). */
    private TraceFilter resolveUser(Long organizationId, TraceFilter f) {
        String text = f.username();
        if (text == null || text.isBlank()) {
            return f;
        }
        boolean system = "sistema".contains(text.trim().toLowerCase());
        return new TraceFilter(f.auditId(), f.entityType(), f.entityId(), f.action(), text, f.from(), f.to(),
                directory.userIdsMatching(organizationId, text.trim()), system);
    }

    /** {@code null} = toda la organización; si no, las auditorías visibles. */
    private Set<Long> visibleAudits(TraceActor actor) {
        return switch (actor.role()) {
            case "ADMIN", "QUALITY" -> null;
            case "AUDITOR" -> audience.auditIdsOf(actor.organizationId(), actor.userId(), actor.fullName());
            default -> Set.of();
        };
    }

    /** Pasa las entradas a DTO buscando de una vez el nombre actual de cada usuario (los bloqueados salen ocultos). */
    private List<TraceEntryDto> toDtos(List<StoredEntry> entries) {
        Set<Long> ids = new java.util.HashSet<>();
        entries.forEach(e -> {
            if (e.userId() != null) {
                ids.add(e.userId());
            }
        });
        Map<Long, ActorDirectory.ActorName> names = ids.isEmpty() ? Map.of() : directory.names(ids);
        return entries.stream().map(e -> toDto(e, e.userId() == null ? null : names.get(e.userId()))).toList();
    }

    private TraceEntryDto toDto(StoredEntry e, ActorDirectory.ActorName name) {
        // El nombre sale siempre del directorio, nunca de lo guardado en la entrada (las anteriores al formato 2 lo
        // conservan, pero no se muestra): así un usuario bloqueado queda oculto también en el historial antiguo.
        String username = name != null ? name.username()
                : e.userId() == null ? (e.username() != null ? e.username() : "sistema") : "usuario-" + e.userId();
        String fullName = name != null ? name.fullName() : e.userId() == null ? "Sistema" : null;
        return new TraceEntryDto(e.id(), e.seq(), e.occurredAt(), e.createdAt(), e.userId(), username, fullName,
                e.userRole(), e.entityType(), e.entityId(), e.entityRef(), e.parentType(), e.parentId(), e.auditId(),
                e.action(), e.summary(), json.readValue(e.changes(), CHANGES), e.prevHash().trim(), e.hash().trim(),
                e.signature().trim());
    }
}

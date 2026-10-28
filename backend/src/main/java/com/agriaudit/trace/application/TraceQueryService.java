package com.agriaudit.trace.application;

import com.agriaudit.common.NotFoundException;
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
    private final JsonMapper json;

    public TraceQueryService(TraceRepository repository, ActorProvider actors, TraceAudience audience,
                             JsonMapper json) {
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
        long total = repository.count(actor.organizationId(), filter, visible, related);
        List<TraceEntryDto> content = repository.search(actor.organizationId(), filter, visible, related,
                safePage * safeSize, safeSize).stream().map(this::toDto).toList();
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
        int page = 0;
        while (true) {
            List<StoredEntry> batch = repository.search(actor.organizationId(), filter, visible, false,
                    page * 500, 500);
            if (batch.isEmpty()) {
                return;
            }
            List<StoredEntry> ordered = new ArrayList<>(batch);
            java.util.Collections.reverse(ordered);
            ordered.forEach(e -> sink.accept(toDto(e)));
            page++;
        }
    }

    /** {@code null} = toda la organización; si no, las auditorías visibles. */
    private Set<Long> visibleAudits(TraceActor actor) {
        return switch (actor.role()) {
            case "ADMIN", "QUALITY" -> null;
            case "AUDITOR" -> audience.auditIdsOf(actor.organizationId(), actor.userId(), actor.fullName());
            default -> Set.of();
        };
    }

    private TraceEntryDto toDto(StoredEntry e) {
        return new TraceEntryDto(e.id(), e.seq(), e.occurredAt(), e.createdAt(), e.userId(), e.username(),
                e.userFullName(), e.userRole(), e.entityType(), e.entityId(), e.entityRef(), e.parentType(),
                e.parentId(), e.auditId(), e.action(), e.summary(), json.readValue(e.changes(), CHANGES),
                e.prevHash().trim(), e.hash().trim(), e.signature().trim());
    }
}

package com.agriaudit.trace;

import java.util.List;

/**
 * Lo que un módulo anota en la traza. El actor (usuario y rol) y la organización los pone la traza a partir del
 * usuario autenticado.
 *
 * @param entityType  tipo de entidad ({@link TraceEntity})
 * @param entityRef   referencia legible (p. ej. «NC-003», «AUD-2026-0007», «4.2.1»)
 * @param parentType  entidad padre (p. ej. la NC de una acción correctiva); puede ser {@code null}
 * @param auditId     auditoría a la que pertenece el cambio, si la hay (determina quién lo ve)
 * @param summary     frase en español: «Cerró la no conformidad NC-003»
 * @param changes     campos que cambiaron, con su valor anterior y nuevo
 */
public record ChangeEntry(String entityType, Long entityId, String entityRef, String parentType, Long parentId,
                          Long auditId, TraceAction action, String summary, List<FieldChange> changes) {

    public ChangeEntry {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }

    public static ChangeEntry of(String entityType, Long entityId, String entityRef, Long auditId,
                                 TraceAction action, String summary, List<FieldChange> changes) {
        return new ChangeEntry(entityType, entityId, entityRef, null, null, auditId, action, summary, changes);
    }
}

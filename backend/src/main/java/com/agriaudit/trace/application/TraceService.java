package com.agriaudit.trace.application;

import com.agriaudit.trace.ChangeEntry;
import com.agriaudit.trace.ChangeRecorder;
import com.agriaudit.trace.TraceActor;
import com.agriaudit.trace.ActorProvider;
import com.agriaudit.trace.infrastructure.persistence.StoredEntry;
import com.agriaudit.trace.infrastructure.persistence.TraceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Alta de entradas en la cadena de la organización. Bloquea la cabeza de la cadena, numera la entrada, calcula
 * su huella SHA-512 (sobre su contenido y la huella anterior) y la firma con HMAC-SHA-512.
 */
@Service
@Transactional
public class TraceService implements ChangeRecorder {

    private final TraceRepository repository;
    private final ActorProvider actors;
    private final TraceCrypto crypto;
    private final JsonMapper json;

    public TraceService(TraceRepository repository, ActorProvider actors, TraceCrypto crypto, JsonMapper json) {
        this.repository = repository;
        this.actors = actors;
        this.crypto = crypto;
        this.json = json;
    }

    @Override
    public void record(ChangeEntry entry) {
        record(actors.current(), entry);
    }

    /** Variante con actor explícito (procesos internos y datos de demostración, sin usuario en contexto). */
    public void record(TraceActor actor, ChangeEntry entry) {
        long org = actor.organizationId();
        TraceRepository.Head head = repository.lockHead(org, TraceCrypto.GENESIS_HASH);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        StoredEntry draft = new StoredEntry(null, org, head.lastSeq() + 1, now, now, actor.userId(),
                actor.username(), actor.fullName(), actor.role(), entry.entityType(), entry.entityId(),
                entry.entityRef(), entry.parentType(), entry.parentId(), entry.auditId(), entry.action().name(),
                entry.summary(), json.writeValueAsString(entry.changes()), head.lastHash(), null, null);
        String hash = crypto.hash(draft.canonical());
        repository.insert(new StoredEntry(null, org, draft.seq(), now, now, draft.userId(), draft.username(),
                draft.userFullName(), draft.userRole(), draft.entityType(), draft.entityId(), draft.entityRef(),
                draft.parentType(), draft.parentId(), draft.auditId(), draft.action(), draft.summary(),
                draft.changes(), draft.prevHash(), hash, crypto.sign(hash)));
    }
}

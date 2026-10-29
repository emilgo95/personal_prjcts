package com.agriaudit.identity.application;

import com.agriaudit.identity.domain.BlockedIdentity;
import com.agriaudit.identity.infrastructure.persistence.BlockedIdentityRepository;
import com.agriaudit.trace.ChangeEntry;
import com.agriaudit.trace.ChangeRecorder;
import com.agriaudit.trace.Changes;
import com.agriaudit.trace.TraceAction;
import com.agriaudit.trace.TraceActor;
import com.agriaudit.trace.TraceEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Destruye los datos identificativos bloqueados cuyo plazo de conservación ha vencido. */
@Component
public class BlockedIdentityDestroyer {

    private static final Logger log = LoggerFactory.getLogger(BlockedIdentityDestroyer.class);

    private final BlockedIdentityRepository blocked;
    private final ChangeRecorder trace;

    public BlockedIdentityDestroyer(BlockedIdentityRepository blocked, ChangeRecorder trace) {
        this.blocked = blocked;
        this.trace = trace;
    }

    @Scheduled(cron = "${agriaudit.privacy.destroy-cron:0 20 0 * * *}", zone = "Europe/Madrid")
    public void destroyExpired() {
        int n = destroyExpired(Instant.now());
        if (n > 0) {
            log.info("Destruidos los datos identificativos bloqueados de {} usuarios (plazo vencido)", n);
        }
    }

    /** Destruye lo vencido a la fecha indicada y deja constancia en la traza; devuelve cuántos. */
    @Transactional
    public int destroyExpired(Instant now) {
        List<BlockedIdentity> expired = blocked.findByDestroyAfterBefore(now);
        for (BlockedIdentity b : expired) {
            blocked.delete(b);
            trace.recordAs(new TraceActor(null, "sistema", "Sistema", "SYSTEM", b.getOrganizationId()),
                    ChangeEntry.of(TraceEntity.USER, b.getUserId(), "usuario " + b.getUserId(), null,
                            TraceAction.DELETE,
                            "Destruyó los datos identificativos bloqueados del usuario n.º " + b.getUserId()
                                    + " al vencer el plazo de conservación",
                            Changes.create().personal("identity", "Datos identificativos", "x", null).list()));
        }
        return expired.size();
    }
}

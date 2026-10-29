package com.agriaudit.identity.application;

import com.agriaudit.common.BadRequestException;
import com.agriaudit.common.NotFoundException;
import com.agriaudit.identity.SecurityUtils;
import com.agriaudit.identity.infrastructure.persistence.BlockedIdentityRepository;
import com.agriaudit.identity.infrastructure.persistence.OrganizationRepository;
import com.agriaudit.trace.ChangeEntry;
import com.agriaudit.trace.ChangeRecorder;
import com.agriaudit.trace.Changes;
import com.agriaudit.trace.TraceAction;
import com.agriaudit.trace.TraceEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Ajustes de privacidad de la organización: plazo de conservación de los datos bloqueados. */
@Service
@Transactional
public class OrganizationPrivacyService {

    public record PrivacySettings(int blockedRetentionMonths, Integer organizationValue, int defaultMonths,
                                  int minMonths, int maxMonths, long blockedCount, Instant nextDestruction) {
    }

    private final OrganizationRepository organizations;
    private final BlockedIdentityRepository blocked;
    private final PrivacyRetention retention;
    private final ChangeRecorder trace;

    public OrganizationPrivacyService(OrganizationRepository organizations, BlockedIdentityRepository blocked,
                                      PrivacyRetention retention, ChangeRecorder trace) {
        this.organizations = organizations;
        this.blocked = blocked;
        this.retention = retention;
        this.trace = trace;
    }

    @Transactional(readOnly = true)
    public PrivacySettings get() {
        Long orgId = SecurityUtils.currentOrganizationId();
        var org = organizations.findById(orgId).orElseThrow(() -> new NotFoundException("Organización no encontrada"));
        return new PrivacySettings(retention.monthsFor(orgId), org.getBlockedRetentionMonths(),
                retention.defaultMonths(), PrivacyRetention.MIN_MONTHS, PrivacyRetention.MAX_MONTHS,
                blocked.countByOrganizationId(orgId),
                blocked.findFirstByOrganizationIdOrderByDestroyAfterAsc(orgId).map(b -> b.getDestroyAfter()).orElse(null));
    }

    /** Fija el plazo de la organización ({@code null} = usar el general). Solo afecta a los bloqueos futuros. */
    public PrivacySettings setRetention(Integer months) {
        if (months != null && (months < PrivacyRetention.MIN_MONTHS || months > PrivacyRetention.MAX_MONTHS)) {
            throw new BadRequestException("El plazo debe estar entre " + PrivacyRetention.MIN_MONTHS + " y "
                    + PrivacyRetention.MAX_MONTHS + " meses");
        }
        Long orgId = SecurityUtils.currentOrganizationId();
        var org = organizations.findById(orgId).orElseThrow(() -> new NotFoundException("Organización no encontrada"));
        Integer before = org.getBlockedRetentionMonths();
        org.setBlockedRetentionMonths(months);
        organizations.save(org);
        Changes c = Changes.create().track("blockedRetentionMonths", "Plazo de conservación de datos bloqueados (meses)",
                before, months);
        if (!c.isEmpty()) {
            trace.record(ChangeEntry.of(TraceEntity.ORGANIZATION, orgId, "organización " + orgId, null,
                    TraceAction.UPDATE, "Cambió el plazo de conservación de los datos bloqueados", c.list()));
        }
        return get();
    }
}

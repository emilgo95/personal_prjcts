package com.agriaudit.trace;

import java.util.Set;

/** Puerto: auditorías «suyas» de una auditora (las que creó o donde figura como líder). Lo implementa {@code audit}. */
public interface TraceAudience {

    Set<Long> auditIdsOf(Long organizationId, Long userId, String fullName);
}

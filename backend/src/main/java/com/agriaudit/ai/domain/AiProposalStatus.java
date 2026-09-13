package com.agriaudit.ai.domain;

/** Estado de una propuesta de IA (RT-A02). Solo {@link #PENDING} admite transición. */
public enum AiProposalStatus {
    PENDING,
    ACCEPTED,
    EDITED,
    REJECTED
}

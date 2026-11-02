package com.agriaudit.ai.port;

import com.agriaudit.ai.domain.AiProposal;
import com.agriaudit.ai.domain.AiProposalStatus;

import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de propuestas de IA; toda lectura acotada por organización. */
public interface AiProposalRepository {

    AiProposal save(AiProposal proposal);

    Optional<AiProposal> findById(long id, long organizationId);

    /** Página de propuestas en un estado, las más recientes primero. */
    List<AiProposal> findByStatus(long organizationId, AiProposalStatus status, int page, int size);

    long countByStatus(long organizationId, AiProposalStatus status);
}

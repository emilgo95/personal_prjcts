package com.agriaudit.ai.infrastructure.persistence;

import com.agriaudit.ai.domain.AiAssistant;
import com.agriaudit.ai.domain.AiProposal;
import com.agriaudit.ai.domain.AiProposalStatus;
import com.agriaudit.ai.domain.AiUsage;
import com.agriaudit.ai.port.AiProposalRepository;
import com.agriaudit.common.NotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Adaptador JPA/PostgreSQL del puerto {@link AiProposalRepository}. */
@Component
class AiProposalPersistenceAdapter implements AiProposalRepository {

    private final AiProposalJpaRepository jpa;

    AiProposalPersistenceAdapter(AiProposalJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public AiProposal save(AiProposal p) {
        AiProposalJpaEntity e;
        if (p.id() == null) {
            e = new AiProposalJpaEntity();
            e.organizationId = p.organizationId();
            e.assistant = p.assistant().name();
            e.inputRefs = p.inputRefs();
            e.promptRef = p.promptRef();
            e.output = p.output();
            e.modelId = p.usage().modelId();
            e.inputTokens = p.usage().inputTokens();
            e.outputTokens = p.usage().outputTokens();
            e.costUsd = p.usage().costUsd();
            e.latencyMs = p.usage().latencyMs();
            e.inferenceGeo = p.usage().inferenceGeo();
            e.createdAt = p.createdAt();
        } else {
            e = jpa.findByIdAndOrganizationId(p.id(), p.organizationId())
                    .orElseThrow(() -> new NotFoundException("Propuesta de IA no encontrada: " + p.id()));
        }
        // Tras el alta, solo la revisión cambia: la salida original y la traza son inmutables.
        e.status = p.status().name();
        e.editedOutput = p.editedOutput();
        e.reviewedBy = p.reviewedBy();
        e.reviewedAt = p.reviewedAt();
        return toDomain(jpa.saveAndFlush(e));
    }

    @Override
    public Optional<AiProposal> findById(long id, long organizationId) {
        return jpa.findByIdAndOrganizationId(id, organizationId).map(AiProposalPersistenceAdapter::toDomain);
    }

    @Override
    public List<AiProposal> findByStatus(long organizationId, AiProposalStatus status, int page, int size) {
        return jpa.findByOrganizationIdAndStatus(organizationId, status.name(),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                .stream().map(AiProposalPersistenceAdapter::toDomain).toList();
    }

    @Override
    public long countByStatus(long organizationId, AiProposalStatus status) {
        return jpa.countByOrganizationIdAndStatus(organizationId, status.name());
    }

    private static AiProposal toDomain(AiProposalJpaEntity e) {
        AiUsage usage = new AiUsage(e.modelId, e.inputTokens, e.outputTokens, e.latencyMs, e.costUsd, e.inferenceGeo);
        return AiProposal.restore(e.id, e.organizationId, AiAssistant.valueOf(e.assistant), e.inputRefs, e.promptRef,
                e.output, usage, e.createdAt, AiProposalStatus.valueOf(e.status), e.reviewedBy, e.reviewedAt,
                e.editedOutput);
    }
}

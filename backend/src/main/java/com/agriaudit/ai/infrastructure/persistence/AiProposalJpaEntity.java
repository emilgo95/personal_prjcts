package com.agriaudit.ai.infrastructure.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity(name = "AiProposal")
@Table(name = "ai_proposals")
class AiProposalJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "organization_id", nullable = false)
    Long organizationId;

    @Column(nullable = false)
    String assistant;

    @Column(nullable = false)
    String status;

    @Column(name = "input_refs", columnDefinition = "text")
    String inputRefs;

    @Column(name = "prompt_ref", nullable = false)
    String promptRef;

    @Column(nullable = false, columnDefinition = "text")
    String output;

    @Column(name = "edited_output", columnDefinition = "text")
    String editedOutput;

    @Column(name = "model_id", nullable = false)
    String modelId;

    @Column(name = "input_tokens", nullable = false)
    long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    long outputTokens;

    @Column(name = "cost_usd")
    BigDecimal costUsd;

    @Column(name = "latency_ms", nullable = false)
    long latencyMs;

    @Column(name = "inference_geo")
    String inferenceGeo;

    @Column(name = "reviewed_by")
    String reviewedBy;

    @Column(name = "reviewed_at")
    Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;
}

package com.agriaudit.ai.application;

import com.agriaudit.ai.domain.AiAssistant;
import com.agriaudit.ai.domain.AiProposal;
import com.agriaudit.ai.domain.AiProposalStatus;
import com.agriaudit.ai.domain.AiUsage;
import com.agriaudit.ai.domain.LlmRequest;
import com.agriaudit.ai.domain.LlmResponse;
import com.agriaudit.ai.domain.ModelTier;
import com.agriaudit.ai.domain.PromptTemplate;
import com.agriaudit.ai.port.AiProposalRepository;
import com.agriaudit.ai.port.LlmPort;
import com.agriaudit.ai.port.PromptRegistry;
import com.agriaudit.common.NotFoundException;
import com.agriaudit.common.ServiceUnavailableException;
import com.agriaudit.identity.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Patrón «propuesta» (RT-A02): llama al modelo con un prompt versionado, guarda la salida como
 * {@link AiProposal} pendiente con su traza (modelo, prompt, entradas, tokens, coste, latencia) y gestiona
 * la revisión humana. Ningún caso de uso aplica una salida de IA sin pasar por aquí.
 */
@Service
@Transactional
public class AiProposalService {

    private static final Logger log = LoggerFactory.getLogger(AiProposalService.class);
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private static final int MAX_PAGE_SIZE = 100;

    private final LlmPort llm;
    private final PromptRegistry prompts;
    private final AiProposalRepository proposals;
    private final AiSettings settings;
    private final Clock clock;

    public AiProposalService(LlmPort llm, PromptRegistry prompts, AiProposalRepository proposals,
                             AiSettings settings, Clock clock) {
        this.llm = llm;
        this.prompts = prompts;
        this.proposals = proposals;
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * Genera una propuesta pendiente de revisión.
     *
     * @param inputRefs referencias a las entradas (p. ej. {@code attachment:42}), nunca su contenido
     * @throws ServiceUnavailableException si la IA no está configurada o el proveedor falla; el llamante
     *                                     debe dejar seguir el flujo manual (RS-IA-10)
     */
    public AiProposal propose(ProposalRequest request) {
        if (!llm.isAvailable()) {
            throw new ServiceUnavailableException("Los asistentes de IA no están disponibles en este entorno");
        }
        PromptTemplate prompt = prompts.active(request.promptId());
        LlmRequest llmRequest = new LlmRequest(request.tier(), prompt.body(), request.instruction(),
                request.untrustedContent(), request.maxTokens(), request.jsonSchema());

        LlmResponse response = llm.complete(llmRequest);
        AiUsage usage = new AiUsage(response.modelId(), response.inputTokens(), response.outputTokens(),
                response.latencyMs(), cost(response), response.inferenceGeo());
        logCall(request, prompt, usage, response.refused());
        if (response.refused()) {
            throw new ServiceUnavailableException("El asistente de IA no ha podido generar una propuesta para esta petición");
        }

        return proposals.save(AiProposal.pending(request.organizationId(), request.assistant(), request.inputRefs(),
                prompt.ref(), response.text(), usage, clock.instant()));
    }

    @Transactional(readOnly = true)
    public AiProposal get(Long id) {
        return require(id);
    }

    @Transactional(readOnly = true)
    public List<AiProposal> list(AiProposalStatus status, int page, int size) {
        int boundedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return proposals.findByStatus(SecurityUtils.currentOrganizationId(), status, Math.max(page, 0), boundedSize);
    }

    @Transactional(readOnly = true)
    public long count(AiProposalStatus status) {
        return proposals.countByStatus(SecurityUtils.currentOrganizationId(), status);
    }

    public AiProposal accept(Long id) {
        AiProposal p = require(id);
        p.accept(SecurityUtils.currentUsername(), clock.instant());
        logReview(p);
        return proposals.save(p);
    }

    public AiProposal acceptEdited(Long id, String editedOutput) {
        AiProposal p = require(id);
        p.acceptEdited(SecurityUtils.currentUsername(), editedOutput, clock.instant());
        logReview(p);
        return proposals.save(p);
    }

    public AiProposal reject(Long id) {
        AiProposal p = require(id);
        p.reject(SecurityUtils.currentUsername(), clock.instant());
        logReview(p);
        return proposals.save(p);
    }

    private AiProposal require(Long id) {
        return proposals.findById(id, SecurityUtils.currentOrganizationId())
                .orElseThrow(() -> new NotFoundException("Propuesta de IA no encontrada: " + id));
    }

    private BigDecimal cost(LlmResponse r) {
        AiSettings.Price price = settings.pricing().get(r.modelId());
        if (price == null) {
            return null;
        }
        return price.inputPerMillion().multiply(BigDecimal.valueOf(r.inputTokens()))
                .add(price.outputPerMillion().multiply(BigDecimal.valueOf(r.outputTokens())))
                .divide(MILLION, MathContext.DECIMAL64);
    }

    /** Traza estructurada por llamada (RT-A08). Sin contenido: ni prompt, ni documento, ni salida (RS-LOG-05). */
    private void logCall(ProposalRequest request, PromptTemplate prompt, AiUsage usage, boolean refused) {
        log.info("ai.call assistant={} prompt={} model={} tokensIn={} tokensOut={} costUsd={} latencyMs={} "
                        + "inferenceGeo={} organizationId={} refused={}",
                request.assistant(), prompt.ref(), usage.modelId(), usage.inputTokens(), usage.outputTokens(),
                usage.costUsd(), usage.latencyMs(), usage.inferenceGeo(), request.organizationId(), refused);
    }

    private void logReview(AiProposal p) {
        log.info("ai.review proposalId={} assistant={} prompt={} status={} reviewer={} organizationId={}",
                p.id(), p.assistant(), p.promptRef(), p.status(), p.reviewedBy(), p.organizationId());
    }

    /**
     * @param instruction      tarea concreta, redactada por el caso de uso
     * @param untrustedContent contenido a analizar (documento, OCR…), que se enviará delimitado como dato
     */
    public record ProposalRequest(long organizationId, AiAssistant assistant, String promptId, ModelTier tier,
                                  String instruction, String untrustedContent, String inputRefs, int maxTokens,
                                  Map<String, Object> jsonSchema) {
    }
}

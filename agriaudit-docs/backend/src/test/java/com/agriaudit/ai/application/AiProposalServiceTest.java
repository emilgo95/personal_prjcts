package com.agriaudit.ai.application;

import com.agriaudit.ai.application.AiProposalService.ProposalRequest;
import com.agriaudit.ai.domain.AiAssistant;
import com.agriaudit.ai.domain.AiProposal;
import com.agriaudit.ai.domain.AiProposalStatus;
import com.agriaudit.ai.domain.LlmResponse;
import com.agriaudit.ai.domain.ModelTier;
import com.agriaudit.ai.domain.PromptTemplate;
import com.agriaudit.ai.port.AiProposalRepository;
import com.agriaudit.ai.port.LlmPort;
import com.agriaudit.ai.port.PromptRegistry;
import com.agriaudit.common.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiProposalServiceTest {

    private LlmPort llm;
    private AiProposalRepository repository;
    private AiProposalService service;

    @BeforeEach
    void setUp() {
        llm = mock(LlmPort.class);
        repository = mock(AiProposalRepository.class);
        PromptRegistry prompts = id -> new PromptTemplate(id, 3, "Eres un asistente de auditoría.");
        AiSettings settings = new AiSettings("fake", Map.of(),
                Map.of("claude-haiku-4-5", new AiSettings.Price(new BigDecimal("1.00"), new BigDecimal("5.00"))),
                Map.of(), null);
        service = new AiProposalService(llm, prompts, repository, settings,
                Clock.fixed(Instant.parse("2026-09-13T10:00:00Z"), ZoneOffset.UTC));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void guardaLaSalidaComoPropuestaPendienteConTrazaYCoste() {
        when(llm.isAvailable()).thenReturn(true);
        when(llm.complete(any())).thenReturn(
                new LlmResponse("{\"tipo\":\"ANALISIS_AGUA\"}", false, "claude-haiku-4-5", 1_000, 200, 420, null));

        AiProposal p = service.propose(request());

        assertThat(p.status()).isEqualTo(AiProposalStatus.PENDING);
        assertThat(p.promptRef()).isEqualTo("evidence-classification.v3");
        assertThat(p.inputRefs()).isEqualTo("attachment:42");
        assertThat(p.output()).isEqualTo("{\"tipo\":\"ANALISIS_AGUA\"}");
        // 1000 × 1 $/M + 200 × 5 $/M = 0.002 $
        assertThat(p.usage().costUsd()).isEqualByComparingTo("0.002");
        assertThat(p.usage().latencyMs()).isEqualTo(420);
    }

    @Test
    void sinProveedorFallaDeFormaControladaYNoLlamaNiGuarda() {
        when(llm.isAvailable()).thenReturn(false);

        assertThatThrownBy(() -> service.propose(request())).isInstanceOf(ServiceUnavailableException.class);
        verify(llm, never()).complete(any());
        verify(repository, never()).save(any());
    }

    @Test
    void siElModeloDeclinaNoSeGuardaPropuesta() {
        when(llm.isAvailable()).thenReturn(true);
        when(llm.complete(any())).thenReturn(new LlmResponse("", true, "claude-haiku-4-5", 50, 0, 100, null));

        assertThatThrownBy(() -> service.propose(request())).isInstanceOf(ServiceUnavailableException.class);
        verify(repository, never()).save(any());
    }

    private static ProposalRequest request() {
        return new ProposalRequest(1L, AiAssistant.EVIDENCE_CLASSIFICATION, "evidence-classification",
                ModelTier.FAST, "Clasifica el documento", "Boletín de análisis de agua", "attachment:42", 512,
                Map.of("type", "object"));
    }
}

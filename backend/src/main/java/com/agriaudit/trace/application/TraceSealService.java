package com.agriaudit.trace.application;

import com.agriaudit.trace.TraceDtos.SealDto;
import com.agriaudit.trace.infrastructure.persistence.TraceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Sellos de la cadena: cada noche (y a petición) se firma la última huella de cada organización. Un sello es un
 * anclaje: sin la clave HMAC no se puede rehacer, y descargado fuera de la aplicación permite demostrar que la
 * cadena no se reescribió desde ese momento.
 */
@Service
public class TraceSealService {

    private static final Logger log = LoggerFactory.getLogger(TraceSealService.class);

    private final TraceRepository repository;
    private final TraceCrypto crypto;

    public TraceSealService(TraceRepository repository, TraceCrypto crypto) {
        this.repository = repository;
        this.crypto = crypto;
    }

    static String sealText(long organizationId, long lastSeq, String lastHash) {
        return TraceCrypto.canonical("seal", organizationId, lastSeq, lastHash);
    }

    /** Sella la cadena de la organización si hay entradas nuevas desde el último sello. */
    @Transactional
    public Optional<SealDto> seal(long organizationId) {
        var head = repository.head(organizationId);
        if (head.isEmpty() || head.get().lastSeq() == 0) {
            return Optional.empty();
        }
        var latest = repository.latestSeal(organizationId);
        if (latest.isPresent() && latest.get().lastSeq() == head.get().lastSeq()) {
            return Optional.empty();
        }
        var seal = new TraceRepository.SealRow(null, organizationId, Instant.now().truncatedTo(ChronoUnit.MICROS),
                head.get().lastSeq(), head.get().lastHash(),
                crypto.sign(sealText(organizationId, head.get().lastSeq(), head.get().lastHash())));
        repository.insertSeal(seal);
        return repository.latestSeal(organizationId).map(TraceSealService::toDto);
    }

    @Transactional(readOnly = true)
    public List<SealDto> list(long organizationId) {
        return repository.seals(organizationId).stream().map(TraceSealService::toDto).toList();
    }

    /** Sello nocturno de todas las organizaciones. */
    @Scheduled(cron = "${agriaudit.trace.seal-cron:0 5 0 * * *}", zone = "Europe/Madrid")
    public void sealAll() {
        for (Long org : repository.organizationsWithEntries()) {
            try {
                seal(org).ifPresent(s -> log.info("Sello de trazabilidad de la organización {}: entrada {}", org,
                        s.lastSeq()));
            } catch (RuntimeException e) {
                log.error("No se pudo sellar la trazabilidad de la organización {}", org, e);
            }
        }
    }

    private static SealDto toDto(TraceRepository.SealRow s) {
        return new SealDto(s.id(), s.sealedAt(), s.lastSeq(), s.lastHash().trim(), s.signature().trim());
    }
}

package com.agriaudit.trace.application;

import com.agriaudit.trace.TraceDtos.IntegrityReport;
import com.agriaudit.trace.infrastructure.persistence.StoredEntry;
import com.agriaudit.trace.infrastructure.persistence.TraceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Comprueba la cadena de una organización: numeración sin huecos, enlace de cada entrada con la anterior,
 * huella y firma recalculadas, la cabeza de la cadena y los sellos guardados.
 */
@Service
@Transactional(readOnly = true)
public class TraceIntegrityService {

    private static final int BATCH = 1000;

    private final TraceRepository repository;
    private final TraceCrypto crypto;

    public TraceIntegrityService(TraceRepository repository, TraceCrypto crypto) {
        this.repository = repository;
        this.crypto = crypto;
    }

    public IntegrityReport verify(long organizationId) {
        var seals = repository.seals(organizationId);
        Map<Long, TraceRepository.SealRow> sealsBySeq = new TreeMap<>();
        seals.forEach(s -> sealsBySeq.put(s.lastSeq(), s));

        long expectedSeq = 1;
        long checked = 0;
        String previousHash = TraceCrypto.GENESIS_HASH;
        long afterSeq = 0;
        while (true) {
            List<StoredEntry> batch = repository.chainAfter(organizationId, afterSeq, BATCH);
            if (batch.isEmpty()) {
                break;
            }
            for (StoredEntry e : batch) {
                String problem = null;
                if (e.seq() != expectedSeq) {
                    problem = "Falta la entrada " + expectedSeq + " (se esperaba antes de la " + e.seq() + ")";
                } else if (!e.prevHash().trim().equals(previousHash)) {
                    problem = "La entrada " + e.seq() + " no enlaza con la anterior";
                } else if (!crypto.hash(e.canonical()).equals(e.hash().trim())) {
                    problem = "El contenido de la entrada " + e.seq() + " no coincide con su huella";
                } else if (!crypto.sign(e.hash().trim()).equals(e.signature().trim())) {
                    problem = "La firma de la entrada " + e.seq() + " no es válida";
                }
                if (problem != null) {
                    return fail(checked, seals.size(), e.seq(), problem);
                }
                TraceRepository.SealRow seal = sealsBySeq.get(e.seq());
                if (seal != null && !seal.lastHash().trim().equals(e.hash().trim())) {
                    return fail(checked, seals.size(), e.seq(), "La entrada " + e.seq()
                            + " no coincide con el sello guardado");
                }
                previousHash = e.hash().trim();
                expectedSeq++;
                checked++;
                afterSeq = e.seq();
            }
        }
        var head = repository.head(organizationId);
        long lastSeq = expectedSeq - 1;
        if (head.isPresent() && (head.get().lastSeq() != lastSeq
                || (lastSeq > 0 && !head.get().lastHash().equals(previousHash)))) {
            return fail(checked, seals.size(), lastSeq + 1, "La cabeza de la cadena indica " + head.get().lastSeq()
                    + " entradas y hay " + lastSeq + ": se han eliminado entradas al final");
        }
        for (TraceRepository.SealRow s : seals) {
            if (s.lastSeq() > lastSeq) {
                return fail(checked, seals.size(), s.lastSeq(), "El sello de la entrada " + s.lastSeq()
                        + " apunta a una entrada que ya no existe");
            }
            if (!crypto.sign(TraceSealService.sealText(s.organizationId(), s.lastSeq(), s.lastHash().trim()))
                    .equals(s.signature().trim())) {
                return fail(checked, seals.size(), s.lastSeq(), "La firma del sello de la entrada " + s.lastSeq()
                        + " no es válida");
            }
        }
        return new IntegrityReport(true, checked, seals.size(), null,
                "La cadena es íntegra: " + checked + " entradas y " + seals.size() + " sellos comprobados",
                Instant.now());
    }

    private static IntegrityReport fail(long checked, int seals, long seq, String message) {
        return new IntegrityReport(false, checked, seals, seq, message, Instant.now());
    }
}

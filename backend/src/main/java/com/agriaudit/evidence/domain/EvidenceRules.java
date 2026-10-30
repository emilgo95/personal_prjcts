package com.agriaudit.evidence.domain;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Reglas de vigencia y de salud de las evidencias: Java puro, sin framework ni persistencia (hexagonal estricto). */
public final class EvidenceRules {

    public static final String CURRENT = "CURRENT";
    public static final String DUE_SOON = "DUE_SOON";
    public static final String EXPIRED = "EXPIRED";
    public static final String UNFAVORABLE = "UNFAVORABLE";
    public static final String MISSING = "MISSING";

    private EvidenceRules() {
    }

    /** Lo que importa de un registro para calcular su estado. */
    public record Facts(Long id, LocalDate issuedOn, LocalDate validUntil, String outcome) {
    }

    /** Estado de un tipo en una explotación y el registro que lo respalda (si lo hay). */
    public record Health(String status, Long recordId, LocalDate validUntil) {
    }

    /** Vigencia de un registro: sin fecha de fin se considera vigente; «por renovar» dentro de la ventana de aviso. */
    public static String statusOf(LocalDate validUntil, LocalDate today, int windowDays) {
        if (validUntil == null) {
            return CURRENT;
        }
        if (validUntil.isBefore(today)) {
            return EXPIRED;
        }
        return validUntil.isAfter(today.plusDays(windowDays)) ? CURRENT : DUE_SOON;
    }

    /**
     * Estado de un tipo de evidencia en una explotación a partir de sus registros: vale el mejor registro no
     * desfavorable (vigente, si no por renovar, si no vencido); si solo hay desfavorables, «desfavorable»; si no hay,
     * «falta».
     */
    public static Health health(List<Facts> records, LocalDate today, int windowDays) {
        if (records.isEmpty()) {
            return new Health(MISSING, null, null);
        }
        for (String wanted : List.of(CURRENT, DUE_SOON, EXPIRED)) {
            Optional<Facts> best = records.stream()
                    .filter(r -> !UNFAVORABLE.equals(r.outcome()))
                    .filter(r -> wanted.equals(statusOf(r.validUntil(), today, windowDays)))
                    .max(Comparator.comparing(Facts::issuedOn).thenComparing(Facts::id));
            if (best.isPresent()) {
                return new Health(wanted, best.get().id(), best.get().validUntil());
            }
        }
        Facts latest = records.stream().max(Comparator.comparing(Facts::issuedOn).thenComparing(Facts::id)).orElseThrow();
        return new Health(UNFAVORABLE, latest.id(), latest.validUntil());
    }
}

package com.agriaudit.ai.domain;

import java.util.Objects;

/**
 * A qué corpus y a qué organización pertenece un fragmento o se dirige una búsqueda. Garantiza la regla
 * de aislamiento en origen: el catálogo compartido no lleva organización y la evidencia siempre la lleva
 * (RS-IA-06).
 */
public record CorpusScope(Corpus corpus, Long organizationId) {

    public CorpusScope {
        Objects.requireNonNull(corpus, "corpus");
        if (corpus.isShared() && organizationId != null) {
            throw new IllegalArgumentException("El corpus compartido " + corpus + " no admite organización");
        }
        if (!corpus.isShared() && organizationId == null) {
            throw new IllegalArgumentException("El corpus " + corpus + " exige organización");
        }
    }

    public static CorpusScope regulatoryCatalog() {
        return new CorpusScope(Corpus.REGULATORY_CATALOG, null);
    }

    public static CorpusScope evidenceOf(long organizationId) {
        return new CorpusScope(Corpus.ORGANIZATION_EVIDENCE, organizationId);
    }
}

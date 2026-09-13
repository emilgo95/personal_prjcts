package com.agriaudit.ai.application;

import com.agriaudit.ai.domain.CorpusScope;
import com.agriaudit.ai.domain.EmbeddedChunk;
import com.agriaudit.ai.domain.RetrievedChunk;
import com.agriaudit.ai.port.EmbeddingPort;
import com.agriaudit.ai.port.VectorSearchPort;
import com.agriaudit.common.ServiceUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Indexado y recuperación semántica (RT-A06): trocea ya hecho por el llamante, embebe con {@link EmbeddingPort}
 * y guarda o busca en {@link VectorSearchPort} dentro de un {@link CorpusScope}. Base del copiloto normativo
 * (F1) y de la búsqueda de evidencia; el troceado y los índices concretos llegan en WS-5.
 */
@Service
@Transactional
public class SemanticIndexService {

    private final EmbeddingPort embeddings;
    private final VectorSearchPort vectors;

    public SemanticIndexService(EmbeddingPort embeddings, VectorSearchPort vectors) {
        this.embeddings = embeddings;
        this.vectors = vectors;
    }

    /** Sustituye los fragmentos de un origen dentro del ámbito. */
    public void index(CorpusScope scope, String sourceType, String sourceId, List<String> chunks) {
        requireAvailable();
        vectors.deleteSource(scope, sourceType, sourceId);
        if (chunks.isEmpty()) {
            return;
        }
        List<float[]> vs = embeddings.embed(chunks);
        List<EmbeddedChunk> out = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            out.add(new EmbeddedChunk(scope, sourceType, sourceId, i, chunks.get(i), embeddings.model(), vs.get(i)));
        }
        vectors.upsert(out);
    }

    @Transactional(readOnly = true)
    public List<RetrievedChunk> search(CorpusScope scope, String question, int limit) {
        requireAvailable();
        float[] query = embeddings.embed(List.of(question)).get(0);
        return vectors.search(scope, embeddings.model(), query, limit);
    }

    public void remove(CorpusScope scope, String sourceType, String sourceId) {
        vectors.deleteSource(scope, sourceType, sourceId);
    }

    private void requireAvailable() {
        if (!embeddings.isAvailable()) {
            throw new ServiceUnavailableException("La búsqueda semántica no está configurada en este entorno");
        }
    }
}

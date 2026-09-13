package com.agriaudit.ai.port;

import com.agriaudit.ai.domain.CorpusScope;
import com.agriaudit.ai.domain.EmbeddedChunk;
import com.agriaudit.ai.domain.RetrievedChunk;

import java.util.List;

/**
 * Almacén y búsqueda vectorial (RT-A06). Contrato de aislamiento (RS-IA-06): una búsqueda con un
 * {@link CorpusScope} solo puede devolver fragmentos de ese mismo corpus y organización, y el filtro se
 * aplica dentro de la consulta, no sobre los resultados. Lo fija {@code VectorSearchContract}.
 */
public interface VectorSearchPort {

    /** Inserta o sustituye (mismo ámbito, origen, índice y modelo) los fragmentos. */
    void upsert(List<EmbeddedChunk> chunks);

    /** Los {@code limit} fragmentos más parecidos del ámbito, generados con {@code model}. */
    List<RetrievedChunk> search(CorpusScope scope, String model, float[] query, int limit);

    /** Borra todos los fragmentos de un origen dentro del ámbito (p. ej. al borrar un adjunto). */
    void deleteSource(CorpusScope scope, String sourceType, String sourceId);
}

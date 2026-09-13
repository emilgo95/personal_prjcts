package com.agriaudit.ai.infrastructure.pgvector;

import com.agriaudit.ai.domain.CorpusScope;
import com.agriaudit.ai.domain.EmbeddedChunk;
import com.agriaudit.ai.domain.RetrievedChunk;
import com.agriaudit.ai.port.VectorSearchPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adaptador de {@link VectorSearchPort} sobre PostgreSQL + pgvector (RT-A06): sin servicios nuevos, en la
 * base de datos existente. Distancia coseno con índice HNSW.
 *
 * <p>Aislamiento (RS-IA-06): el corpus y la organización van siempre en el {@code WHERE} de la consulta
 * vectorial, con dos consultas explícitas (catálogo compartido / evidencia de una organización) en vez de
 * comparaciones con {@code NULL} que un parámetro mal pasado podría abrir.
 */
@Component
class PgVectorSearchAdapter implements VectorSearchPort {

    static final int DIMENSIONS = 1024;

    private static final String UPSERT = """
            INSERT INTO ai_embeddings (corpus, organization_id, source_type, source_id, chunk_index, content,
                                       embedding_model, embedding)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::vector)
            ON CONFLICT (corpus, organization_id, source_type, source_id, chunk_index, embedding_model)
            DO UPDATE SET content = EXCLUDED.content, embedding = EXCLUDED.embedding, created_at = now()
            """;

    private static final String SEARCH_SHARED = """
            SELECT source_type, source_id, chunk_index, content, 1 - (embedding <=> ?::vector) AS score
            FROM ai_embeddings
            WHERE corpus = ? AND organization_id IS NULL AND embedding_model = ?
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """;

    private static final String SEARCH_ORGANIZATION = """
            SELECT source_type, source_id, chunk_index, content, 1 - (embedding <=> ?::vector) AS score
            FROM ai_embeddings
            WHERE corpus = ? AND organization_id = ? AND embedding_model = ?
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """;

    private static final RowMapper<RetrievedChunk> ROW = (rs, i) -> new RetrievedChunk(
            rs.getString("source_type"), rs.getString("source_id"), rs.getInt("chunk_index"),
            rs.getString("content"), rs.getDouble("score"));

    private final JdbcTemplate jdbc;

    PgVectorSearchAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsert(List<EmbeddedChunk> chunks) {
        for (EmbeddedChunk c : chunks) {
            jdbc.update(UPSERT, c.scope().corpus().name(), c.scope().organizationId(), c.sourceType(), c.sourceId(),
                    c.chunkIndex(), c.content(), c.model(), literal(c.vector()));
        }
    }

    @Override
    public List<RetrievedChunk> search(CorpusScope scope, String model, float[] query, int limit) {
        String vector = literal(query);
        int bounded = Math.min(Math.max(limit, 1), 50);
        if (scope.corpus().isShared()) {
            return jdbc.query(SEARCH_SHARED, ROW, vector, scope.corpus().name(), model, vector, bounded);
        }
        return jdbc.query(SEARCH_ORGANIZATION, ROW, vector, scope.corpus().name(), scope.organizationId(), model,
                vector, bounded);
    }

    @Override
    public void deleteSource(CorpusScope scope, String sourceType, String sourceId) {
        if (scope.corpus().isShared()) {
            jdbc.update("DELETE FROM ai_embeddings WHERE corpus = ? AND organization_id IS NULL "
                    + "AND source_type = ? AND source_id = ?", scope.corpus().name(), sourceType, sourceId);
        } else {
            jdbc.update("DELETE FROM ai_embeddings WHERE corpus = ? AND organization_id = ? "
                            + "AND source_type = ? AND source_id = ?",
                    scope.corpus().name(), scope.organizationId(), sourceType, sourceId);
        }
    }

    /** Literal de pgvector ({@code [0.1,0.2,…]}), independiente del locale. */
    static String literal(float[] vector) {
        if (vector.length != DIMENSIONS) {
            throw new IllegalArgumentException("Se esperaba un vector de " + DIMENSIONS + " dimensiones y llegó uno de "
                    + vector.length);
        }
        StringBuilder sb = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(Float.toString(vector[i]));
        }
        return sb.append(']').toString();
    }
}

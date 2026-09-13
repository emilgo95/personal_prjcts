package com.agriaudit.ai.infrastructure.pgvector;

import com.agriaudit.ai.application.SemanticIndexService;
import com.agriaudit.ai.domain.CorpusScope;
import com.agriaudit.ai.domain.EmbeddedChunk;
import com.agriaudit.ai.domain.RetrievedChunk;
import com.agriaudit.ai.infrastructure.fake.FakeEmbeddingAdapter;
import com.agriaudit.support.TestPostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * pgvector real (Testcontainers + migraciones Flyway): recuperación por similitud y, sobre todo, el contrato
 * de aislamiento del RAG (RS-IA-06) — la evidencia de una organización nunca aparece en búsquedas de otra
 * organización ni del catálogo compartido.
 */
@JdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PgVectorSearchAdapterTest {

    @Container
    static PostgreSQLContainer<?> postgres = TestPostgres.container();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    private PgVectorSearchAdapter vectors;
    private SemanticIndexService index;
    private long orgA;
    private long orgB;

    @BeforeEach
    void setUp() {
        vectors = new PgVectorSearchAdapter(jdbc);
        index = new SemanticIndexService(new FakeEmbeddingAdapter(), vectors);
        orgA = jdbc.queryForObject("INSERT INTO organizations (name) VALUES ('RAG A') "
                + "ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name RETURNING id", Long.class);
        orgB = jdbc.queryForObject("INSERT INTO organizations (name) VALUES ('RAG B') "
                + "ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name RETURNING id", Long.class);
    }

    @Test
    void recuperaPrimeroElFragmentoMasParecidoDelCatalogo() {
        index.index(CorpusScope.regulatoryCatalog(), "CONTROL_POINT", "CB-5.1", List.of(
                "Análisis del agua de riego en laboratorio acreditado con frecuencia anual"));
        index.index(CorpusScope.regulatoryCatalog(), "CONTROL_POINT", "AF-3.2", List.of(
                "Formación de los trabajadores en higiene y primeros auxilios"));

        List<RetrievedChunk> hits = index.search(CorpusScope.regulatoryCatalog(), "análisis del agua de riego", 2);

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).sourceId()).isEqualTo("CB-5.1");
        assertThat(hits.get(0).score()).isGreaterThan(hits.get(1).score());
    }

    @Test
    void laEvidenciaDeUnaOrganizacionNoSeRecuperaDesdeOtraNiDesdeElCatalogo() {
        String secreto = "Boletín de análisis de agua de la finca de Pedro con resultado no apto";
        index.index(CorpusScope.evidenceOf(orgA), "ATTACHMENT", "att-1", List.of(secreto));

        assertThat(index.search(CorpusScope.evidenceOf(orgA), "análisis de agua no apto", 5))
                .extracting(RetrievedChunk::sourceId).containsExactly("att-1");
        assertThat(index.search(CorpusScope.evidenceOf(orgB), "análisis de agua no apto", 5)).isEmpty();
        assertThat(index.search(CorpusScope.regulatoryCatalog(), "análisis de agua no apto", 5))
                .extracting(RetrievedChunk::content).doesNotContain(secreto);
    }

    @Test
    void reindexarUnOrigenSustituyeSusFragmentosSinDuplicar() {
        CorpusScope scope = CorpusScope.evidenceOf(orgA);
        index.index(scope, "ATTACHMENT", "att-2", List.of("versión uno del documento", "segundo trozo"));
        index.index(scope, "ATTACHMENT", "att-2", List.of("versión dos del documento"));

        assertThat(countFor("att-2")).isEqualTo(1);
        assertThat(index.search(scope, "versión documento", 5)).extracting(RetrievedChunk::content)
                .containsExactly("versión dos del documento");
    }

    @Test
    void borrarUnOrigenSoloAfectaASuAmbito() {
        index.index(CorpusScope.evidenceOf(orgA), "ATTACHMENT", "att-3", List.of("certificado de formación"));
        index.index(CorpusScope.evidenceOf(orgB), "ATTACHMENT", "att-3", List.of("certificado de formación"));

        index.remove(CorpusScope.evidenceOf(orgA), "ATTACHMENT", "att-3");

        assertThat(index.search(CorpusScope.evidenceOf(orgA), "certificado formación", 5)).isEmpty();
        assertThat(index.search(CorpusScope.evidenceOf(orgB), "certificado formación", 5)).hasSize(1);
    }

    @Test
    void noMezclaVectoresDeModelosDistintos() {
        index.index(CorpusScope.regulatoryCatalog(), "REGULATION", "REG-1107", List.of("fitosanitarios autorizados"));

        float[] query = new FakeEmbeddingAdapter().embed(List.of("fitosanitarios autorizados")).get(0);
        assertThat(vectors.search(CorpusScope.regulatoryCatalog(), "otro-modelo", query, 5)).isEmpty();
    }

    @Test
    void elAmbitoYElTamanoDelVectorSeValidan() {
        assertThatThrownBy(() -> new CorpusScope(com.agriaudit.ai.domain.Corpus.ORGANIZATION_EVIDENCE, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CorpusScope(com.agriaudit.ai.domain.Corpus.REGULATORY_CATALOG, orgA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> vectors.upsert(List.of(new EmbeddedChunk(CorpusScope.regulatoryCatalog(), "X", "1", 0,
                "texto", "m", new float[3])))).isInstanceOf(IllegalArgumentException.class);
    }

    private long countFor(String sourceId) {
        return jdbc.queryForObject("SELECT count(*) FROM ai_embeddings WHERE source_id = ?", Long.class, sourceId);
    }
}

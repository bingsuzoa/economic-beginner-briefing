package com.economicbriefing.economicflow.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PrincipleVectorRepository {
    private final JdbcClient jdbc;

    public PrincipleVectorRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<EmbeddingProfile> embeddingProfile() {
        return jdbc.sql("""
                SELECT embedding_model, embedding_dimensions
                FROM economic_principle_chunk
                GROUP BY embedding_model, embedding_dimensions
                ORDER BY count(*) DESC
                LIMIT 1
                """).query((rs, row) -> new EmbeddingProfile(rs.getString(1), rs.getInt(2))).optional();
    }

    public List<SearchResult> search(String embedding, String model, int dimensions, int limit) {
        return jdbc.sql("""
                SELECT chunk_id, text, source, section_title, page_start, page_end,
                       1 - (embedding <=> CAST(:embedding AS vector)) AS similarity
                FROM economic_principle_chunk
                WHERE embedding_model = :model AND embedding_dimensions = :dimensions
                ORDER BY embedding <=> CAST(:embedding AS vector)
                LIMIT :limit
                """).param("embedding", embedding).param("model", model).param("dimensions", dimensions).param("limit", limit)
                .query((rs, row) -> new SearchResult(rs.getString("chunk_id"), rs.getString("text"),
                        rs.getString("source"), rs.getString("section_title"), rs.getInt("page_start"),
                        rs.getInt("page_end"), rs.getDouble("similarity"))).list();
    }

    public record EmbeddingProfile(String model, int dimensions) {}
    public record SearchResult(String chunkId, String text, String source, String sectionTitle,
                               int pageStart, int pageEnd, double similarity) {}
}

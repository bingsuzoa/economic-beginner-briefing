package com.economicbriefing.briefing;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PrincipleStore {
    private final JdbcTemplate jdbc;

    public PrincipleStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Principle> find(float[] vector, String model, int limit) {
        return jdbc.query("""
                SELECT chunk_id, source, section_title, text,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM economic_principle_chunk
                WHERE embedding_model = ? AND embedding_dimensions = 1536
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """, (rs, row) -> new Principle(rs.getString("chunk_id"), rs.getString("source"),
                        rs.getString("section_title"), rs.getString("text"), rs.getDouble("similarity")),
                ObservationStore.vectorLiteral(vector), model, ObservationStore.vectorLiteral(vector), limit);
    }

    public record Principle(String chunkId, String source, String title, String text, double similarity) {}
}

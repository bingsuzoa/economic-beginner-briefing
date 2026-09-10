package com.economicbriefing.briefing;

import java.sql.Array;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ObservationStore {
    private final JdbcTemplate jdbc;

    public ObservationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public List<Observation> replace(String articleId, List<Draft> drafts, String model, String promptVersion) {
        jdbc.update("DELETE FROM article_observations WHERE article_id = ?", articleId);
        List<Observation> result = new ArrayList<>();
        for (int index = 0; index < drafts.size(); index++) {
            int number = index + 1;
            Draft draft = drafts.get(index);
            String id = articleId + ":O" + number;
            jdbc.update(connection -> {
                var statement = connection.prepareStatement("""
                        INSERT INTO article_observations
                          (id, article_id, observation_no, observation_text, source_span_ids, model_name, prompt_version)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """);
                statement.setString(1, id);
                statement.setString(2, articleId);
                statement.setInt(3, number);
                statement.setString(4, draft.text());
                Array spans = connection.createArrayOf("text", draft.spanIds().toArray(String[]::new));
                statement.setArray(5, spans);
                statement.setString(6, model);
                statement.setString(7, promptVersion);
                return statement;
            });
            result.add(new Observation(id, articleId, number, draft.text(), draft.spanIds(), null, null));
        }
        return result;
    }

    public void saveEmbedding(String id, float[] vector, String model) {
        jdbc.update("UPDATE article_observations SET embedding = ?::vector, embedding_model = ?, updated_at = now() WHERE id = ?",
                vectorLiteral(vector), model, id);
    }

    public List<Observation> findReusable(String articleId, String model, String promptVersion) {
        return jdbc.query("""
                SELECT id, article_id, observation_no, observation_text, source_span_ids,
                       embedding::text AS embedding
                FROM article_observations
                WHERE article_id = ? AND model_name = ? AND prompt_version = ?
                ORDER BY observation_no
                """, (rs, row) -> {
                    Object raw = rs.getArray("source_span_ids").getArray();
                    String[] spans = raw instanceof String[] values ? values : new String[0];
                    return new Observation(rs.getString("id"), rs.getString("article_id"),
                            rs.getInt("observation_no"), rs.getString("observation_text"), List.of(spans),
                            null, parseVector(rs.getString("embedding")));
                }, articleId, model, promptVersion);
    }

    public List<PastMatch> findPast(float[] vector, OffsetDateTime before, String currentArticleId,
            String currentObservationId, int limit) {
        return jdbc.query("""
                SELECT o.id, o.article_id, o.observation_no, o.observation_text, o.source_span_ids,
                       a.published_at, 1 - (o.embedding <=> ?::vector) AS similarity
                FROM article_observations o
                JOIN articles a ON a.id = o.article_id
                WHERE a.published_at < ? AND o.article_id <> ? AND o.embedding IS NOT NULL
                ORDER BY o.embedding <=> ?::vector
                LIMIT ?
                """, (rs, row) -> {
                    Object raw = rs.getArray("source_span_ids").getArray();
                    String[] spans = raw instanceof String[] values ? values : new String[0];
                    Observation observation = new Observation(rs.getString("id"), rs.getString("article_id"),
                            rs.getInt("observation_no"), rs.getString("observation_text"), List.of(spans),
                            rs.getObject("published_at", OffsetDateTime.class), null);
                    return new PastMatch(currentObservationId, observation, rs.getDouble("similarity"));
                }, vectorLiteral(vector), before, currentArticleId, vectorLiteral(vector), limit);
    }

    static String vectorLiteral(float[] vector) {
        StringBuilder value = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) value.append(',');
            value.append(vector[i]);
        }
        return value.append(']').toString();
    }

    static float[] parseVector(String value) {
        if (value == null || value.length() < 2) return null;
        String[] parts = value.substring(1, value.length() - 1).split(",");
        float[] result = new float[parts.length];
        for (int index = 0; index < parts.length; index++) result[index] = Float.parseFloat(parts[index]);
        return result;
    }

    public record Draft(String text, List<String> spanIds) {}
    public record Observation(String id, String articleId, int number, String text, List<String> spanIds,
                              OffsetDateTime publishedAt, float[] vector) {
        public Observation withVector(float[] value) {
            return new Observation(id, articleId, number, text, spanIds, publishedAt, value);
        }
        public Observation withPublishedAt(OffsetDateTime value) {
            return new Observation(id, articleId, number, text, spanIds, value, vector);
        }
    }
    public record PastMatch(String currentObservationId, Observation observation, double similarity) {}
}

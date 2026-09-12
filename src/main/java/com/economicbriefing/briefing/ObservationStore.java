package com.economicbriefing.briefing;

import com.economicbriefing.article.ArticleEntity;
import com.economicbriefing.article.ParagraphSplitter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ObservationStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ParagraphSplitter splitter;

    public ObservationStore(JdbcTemplate jdbc, ObjectMapper json, ParagraphSplitter splitter) {
        this.jdbc = jdbc; this.json = json; this.splitter = splitter;
    }

    @Transactional
    public List<Observation> replace(ArticleEntity article, List<Draft> drafts, String model, String promptVersion) {
        jdbc.update("DELETE FROM article_observations WHERE article_id = ?", article.getId());
        List<Observation> result = new ArrayList<>();
        for (int index = 0; index < drafts.size(); index++) {
            Draft draft = drafts.get(index);
            Observation observation = fromDraft(article, index + 1, draft);
            jdbc.update(connection -> {
                var statement = connection.prepareStatement("""
                        INSERT INTO article_observations
                          (id, article_id, observation_no, observation_text, source_span_ids, model_name,
                           prompt_version, memory_eligible, source_snapshot)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                        """);
                statement.setString(1, observation.id());
                statement.setString(2, article.getId());
                statement.setInt(3, observation.number());
                statement.setString(4, draft.text());
                statement.setArray(5, connection.createArrayOf("text", draft.spanIds().toArray(String[]::new)));
                statement.setString(6, model);
                statement.setString(7, promptVersion);
                statement.setBoolean(8, draft.remember());
                statement.setString(9, observation.snapshot().toString());
                return statement;
            });
            result.add(observation);
        }
        return List.copyOf(result);
    }

    public Observation fromDraft(ArticleEntity article, int number, Draft draft) {
        Map<String, String> paragraphs = splitter.split(article.getBody());
        ObjectNode snapshot = json.createObjectNode();
        snapshot.put("title", article.getTitle()); snapshot.put("url", article.getUrl());
        snapshot.put("source", article.getSource()); snapshot.put("publishedAt", article.getPublishedAt().toString());
        snapshot.put("bodyHash", bodyHash(article.getBody()));
        ObjectNode spans = snapshot.putObject("spans");
        for (String id : draft.spanIds()) {
            if (!paragraphs.containsKey(id) || !splitter.usableEvidence(paragraphs.get(id)))
                throw new IllegalArgumentException("invalid source span: " + id);
            spans.put(id, paragraphs.get(id));
        }
        return new Observation(article.getId() + ":O" + number, article.getId(), number, draft.text(),
                draft.spanIds(), article.getPublishedAt(), null, draft.remember(), snapshot);
    }

    public void saveEmbedding(String id, float[] vector, String model) {
        jdbc.update("UPDATE article_observations SET embedding = ?::vector, embedding_model = ?, updated_at = now() WHERE id = ?",
                vectorLiteral(vector), model, id);
    }

    public List<Observation> findReusable(ArticleEntity article, String model, String promptVersion) {
        return jdbc.query("""
                SELECT *, embedding::text AS stored_vector FROM article_observations
                WHERE article_id = ? AND model_name = ? AND prompt_version = ?
                  AND source_snapshot->>'bodyHash' = ? AND source_snapshot->>'url' = ?
                  AND (source_snapshot->>'publishedAt')::timestamptz = ?
                ORDER BY observation_no
                """, (rs, row) -> read(rs), article.getId(), model, promptVersion,
                bodyHash(article.getBody()), article.getUrl(), article.getPublishedAt());
    }

    public List<PastMatch> findPast(float[] vector, OffsetDateTime before, String currentArticleId,
            String currentObservationId, String embeddingModel, int limit) {
        return jdbc.query("""
                SELECT *, embedding::text AS stored_vector, 1 - (embedding <=> ?::vector) AS similarity
                FROM article_observations
                WHERE memory_eligible AND source_snapshot IS NOT NULL
                  AND (source_snapshot->>'publishedAt')::timestamptz < ?
                  AND article_id <> ? AND embedding IS NOT NULL AND embedding_model = ?
                ORDER BY embedding <=> ?::vector LIMIT ?
                """, (rs, row) -> new PastMatch(currentObservationId, read(rs), rs.getDouble("similarity")),
                vectorLiteral(vector), before, currentArticleId, embeddingModel, vectorLiteral(vector), limit);
    }

    public List<Observation> findMatchingMemory(List<String> terms, OffsetDateTime before, int limit) {
        if (terms.isEmpty()) return List.of();
        List<Object> args = new ArrayList<>(terms);
        args.add(before); args.add(limit);
        return jdbc.query("SELECT *, embedding::text AS stored_vector FROM article_observations WHERE memory_eligible AND "
                + matchingSql("observation_text || ' ' || (source_snapshot->>'title')", terms.size())
                + " AND (source_snapshot->>'publishedAt')::timestamptz < ? ORDER BY (source_snapshot->>'publishedAt')::timestamptz DESC LIMIT ?",
                (rs, row) -> read(rs), args.toArray());
    }

    public List<String> findArticleIds(List<String> terms, OffsetDateTime before, int limit) {
        if (terms.isEmpty()) return List.of();
        List<Object> args = new ArrayList<>(terms);
        args.add(before); args.add(limit);
        return jdbc.query("SELECT id FROM articles WHERE " + matchingSql("title || ' ' || summary", terms.size())
                + " AND published_at < ? ORDER BY published_at DESC LIMIT ?", (rs, row) -> rs.getString("id"), args.toArray());
    }

    private static String matchingSql(String expression, int count) {
        return String.join(" AND ", java.util.Collections.nCopies(count, "position(lower(?) in lower(" + expression + ")) > 0"));
    }

    private Observation read(ResultSet rs) throws SQLException {
        try {
            JsonNode snapshot = json.readTree(rs.getString("source_snapshot"));
            return new Observation(rs.getString("id"), rs.getString("article_id"), rs.getInt("observation_no"),
                    rs.getString("observation_text"), List.of((String[]) rs.getArray("source_span_ids").getArray()),
                    OffsetDateTime.parse(snapshot.path("publishedAt").asText()), parseVector(rs.getString("stored_vector")),
                    rs.getBoolean("memory_eligible"), snapshot);
        } catch (Exception e) { throw new SQLException("invalid observation source snapshot", e); }
    }

    static String bodyHash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
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

    public record Draft(String text, List<String> spanIds, boolean remember, String memoryReason) {}
    public record Observation(String id, String articleId, int number, String text, List<String> spanIds,
                              OffsetDateTime publishedAt, float[] vector, boolean remember, JsonNode snapshot) {
        public Observation withVector(float[] value) {
            return new Observation(id, articleId, number, text, spanIds, publishedAt, value, remember, snapshot);
        }
    }
    public record PastMatch(String currentObservationId, Observation observation, double similarity) {}
}

package com.economicbriefing.economicflow;

import com.economicbriefing.classifier.EmbeddingService;
import com.economicbriefing.economicflow.repository.PrincipleVectorRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EconomicPrincipleRetriever {
    private static final int TOP_K = 3;
    private final PrincipleVectorRepository chunks;
    private final EmbeddingService embeddings;

    public EconomicPrincipleRetriever(PrincipleVectorRepository chunks, EmbeddingService embeddings) {
        this.chunks = chunks;
        this.embeddings = embeddings;
    }

    @Transactional(readOnly = true)
    public Context retrieve(List<Query> queries) {
        if (queries == null || queries.isEmpty()) return new Context(List.of());
        var profile = chunks.embeddingProfile();
        if (profile.isEmpty()) return new Context(List.of());
        return new Context(queries.stream().map(query -> new QueryResult(query, search(query, profile.get())))
                .filter(result -> !result.results().isEmpty()).toList());
    }

    private List<Chunk> search(Query query, PrincipleVectorRepository.EmbeddingProfile profile) {
        var vector = embeddings.embed(query.query(), profile.model(), profile.dimensions());
        return chunks.search(EmbeddingService.toVectorString(vector), profile.model(), profile.dimensions(), TOP_K)
                .stream().map(result -> new Chunk(result.chunkId(), result.text(), result.source(),
                        result.sectionTitle(), result.pageStart() + "-" + result.pageEnd(), result.similarity())).toList();
    }

    public record Query(String origin, String sourceReference, String query) {}
    public record Context(List<QueryResult> queries) {}
    public record QueryResult(Query request, List<Chunk> results) {}
    public record Chunk(String chunkId, String content, String sourceType, String sourceTitle,
            String section, double score) {}
}

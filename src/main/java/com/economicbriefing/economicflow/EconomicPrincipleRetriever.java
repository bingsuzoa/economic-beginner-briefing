package com.economicbriefing.economicflow;

import java.util.*;
import java.util.stream.Collectors;
import com.economicbriefing.economicflow.repository.EconomicPrincipleChunkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EconomicPrincipleRetriever {
    private final EconomicPrincipleChunkRepository chunks;

    public EconomicPrincipleRetriever(EconomicPrincipleChunkRepository chunks) { this.chunks = chunks; }

    @Transactional(readOnly = true)
    public Context retrieve(List<Query> queries) {
        if (queries == null || queries.isEmpty()) return new Context(List.of());
        var available = chunks.findByActiveTrue();
        return new Context(queries.stream().map(query -> new QueryResult(query,
                available.stream().map(chunk -> new Scored(chunk, score(query.query(), searchable(chunk))))
                        .filter(item -> item.score >= 2).sorted(Comparator.comparingInt(Scored::score).reversed())
                        .limit(3).map(item -> new Chunk(item.chunk.getId(), item.chunk.getContent(),
                                item.chunk.getSourceType(), item.chunk.getSourceTitle(),
                                item.chunk.getSourceSection(), item.score)).toList()))
                .filter(result -> !result.results().isEmpty()).toList());
    }

    private static int score(String query, String document) {
        Set<String> terms = terms(query);
        Set<String> found = terms(document);
        terms.retainAll(found);
        return terms.size();
    }

    private static Set<String> terms(String text) {
        if (text == null) return new HashSet<>();
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_]+"))
                .filter(term -> term.length() >= 2)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static String searchable(com.economicbriefing.economicflow.entity.EconomicPrincipleChunkEntity chunk) {
        return String.join(" ", chunk.getContent(), chunk.getConcepts(),
                Objects.toString(chunk.getFromConcept(), ""), Objects.toString(chunk.getToConcept(), ""),
                Objects.toString(chunk.getMechanism(), ""));
    }

    private record Scored(com.economicbriefing.economicflow.entity.EconomicPrincipleChunkEntity chunk, int score) {}
    public record Query(String origin, String sourceReference, String query) {}
    public record Context(List<QueryResult> queries) {}
    public record QueryResult(Query request, List<Chunk> results) {}
    public record Chunk(Long chunkId, String content, String sourceType, String sourceTitle,
            String section, int score) {}
}

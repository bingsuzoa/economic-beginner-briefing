package com.economicbriefing.economicflow;

import java.util.*;
import org.springframework.stereotype.Service;

/** Bounded factual-history lookup; it deliberately performs no semantic ranking. */
@Service
public class EconomicFlowRetriever {
    private static final int DEPTH = 2;
    private final EconomicFlowGraphRepository graph;
    public EconomicFlowRetriever(EconomicFlowGraphRepository graph) { this.graph = graph; }

    public Context retrieve(List<Request> requests, Set<Long> anchors) {
        if (requests == null || requests.isEmpty() || anchors == null || anchors.isEmpty()) return new Context(List.of());
        var nodes = new LinkedHashMap<Long, EconomicFlowGraphRepository.NodeView>();
        var edges = new LinkedHashMap<String, EconomicFlowGraphRepository.EdgeView>();
        for (Long anchor : anchors) graph.loadAround(anchor, DEPTH).ifPresent(batch -> {
            batch.nodes().forEach(node -> nodes.putIfAbsent(node.nodeId(), node));
            batch.edges().forEach(edge -> edges.putIfAbsent(edge.fromNodeId() + ":" + edge.toNodeId() + ":" + edge.relationType(), edge));
        });
        var path = new Path(List.copyOf(nodes.values()), List.copyOf(edges.values()), edges.values().stream()
                .map(EconomicFlowGraphRepository.EdgeView::articleId).filter(Objects::nonNull).distinct().toList());
        return new Context(requests.stream().map(request -> new Result(request.requestId(), path)).toList());
    }

    public record Request(String requestId, String question) {}
    public record Context(List<Result> results) {}
    public record Result(String requestId, Path path) {}
    public record Path(List<EconomicFlowGraphRepository.NodeView> nodes,
                       List<EconomicFlowGraphRepository.EdgeView> relations, List<String> articleIds) {}
}

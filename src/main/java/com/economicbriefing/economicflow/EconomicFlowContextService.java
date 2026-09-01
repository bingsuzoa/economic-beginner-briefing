package com.economicbriefing.economicflow;

import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class EconomicFlowContextService {
    private static final int BATCH_DEPTH = 3;
    private static final int MAX_JUDGE_CALLS = 3;
    private static final int MAX_NODES = 100;
    private final EconomicFlowGraphRepository graph;
    private final EconomicFlowTraversalJudge judge;

    public EconomicFlowContextService(EconomicFlowGraphRepository graph, EconomicFlowTraversalJudge judge) {
        this.graph = graph; this.judge = judge;
    }

    public Context retrieve(String currentEvent, Set<Long> startNodeIds) {
        Set<Long> visited = new LinkedHashSet<>();
        Set<Long> frontier = new LinkedHashSet<>(startNodeIds);
        Map<Long, EconomicFlowGraphRepository.NodeView> nodes = new LinkedHashMap<>();
        Map<String, EconomicFlowGraphRepository.EdgeView> edges = new LinkedHashMap<>();
        for (int call = 0; call < MAX_JUDGE_CALLS && !frontier.isEmpty() && nodes.size() < MAX_NODES; call++) {
            var batch = graph.loadBackward(frontier, BATCH_DEPTH, visited);
            batch.nodes().forEach(n -> nodes.putIfAbsent(n.nodeId(), n));
            batch.edges().forEach(e -> edges.putIfAbsent(e.fromNodeId() + ":" + e.toNodeId()
                    + ":" + e.relationType(), e));
            visited.addAll(batch.nodes().stream().map(EconomicFlowGraphRepository.NodeView::nodeId).toList());
            if (batch.frontierNodeIds().isEmpty()) {
                return new Context(List.copyOf(nodes.values()), List.copyOf(edges.values()), null, true);
            }
            var result = judge.judge(currentEvent, List.copyOf(nodes.values()),
                    List.copyOf(edges.values()), batch.frontierNodeIds());
            if (result.result() == TraversalDecision.ECONOMIC_MECHANISM) {
                return new Context(List.copyOf(nodes.values()), List.copyOf(edges.values()),
                        result.principleQuery(), false);
            }
            frontier = new LinkedHashSet<>(result.unexplainedNodeIds());
        }
        return new Context(List.copyOf(nodes.values()), List.copyOf(edges.values()), null, true);
    }

    public record Context(List<EconomicFlowGraphRepository.NodeView> nodes,
            List<EconomicFlowGraphRepository.EdgeView> edges, String principleQuery, boolean graphExhausted) {}
}

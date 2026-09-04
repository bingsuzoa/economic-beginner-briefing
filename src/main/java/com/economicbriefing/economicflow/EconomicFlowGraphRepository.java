package com.economicbriefing.economicflow;

import java.util.*;
import com.economicbriefing.economicflow.repository.EventRelationRepository;
import com.economicbriefing.economicflow.repository.EconomicEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EconomicFlowGraphRepository {
    private static final int MAX_NODES = 50;
    private final EventRelationRepository relations;
    private final EconomicEventRepository events;

    public EconomicFlowGraphRepository(EventRelationRepository relations, EconomicEventRepository events) {
        this.relations = relations;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public GraphBatch loadBackward(Set<Long> startIds, int depth, Set<Long> visited) {
        Set<Long> frontier = new LinkedHashSet<>(startIds);
        Map<Long, NodeView> nodes = new LinkedHashMap<>();
        events.findAllById(startIds).forEach(node -> nodes.put(node.getId(), NodeView.from(node)));
        Map<String, EdgeView> edges = new LinkedHashMap<>();
        for (int hop = 0; hop < depth && !frontier.isEmpty(); hop++) {
            Set<Long> next = new LinkedHashSet<>();
            for (var edge : relations.findByToEvent_IdInAndProvenance(frontier, RelationProvenance.ARTICLE_EXPLICIT)) {
                if (edge.getRelationType() == EventRelationType.RELATED_TO) continue;
                var from = edge.getFromEvent();
                var to = edge.getToEvent();
                nodes.putIfAbsent(from.getId(), NodeView.from(from));
                nodes.putIfAbsent(to.getId(), NodeView.from(to));
                edges.putIfAbsent(from.getId() + ":" + to.getId() + ":" + edge.getRelationType(),
                        new EdgeView(from.getId(), to.getId(), edge.getRelationType(), edge.getEvidenceArticleId()));
                if (!visited.contains(from.getId())) next.add(from.getId());
            }
            frontier = next;
        }
        frontier.removeAll(visited);
        return new GraphBatch(List.copyOf(nodes.values()), List.copyOf(edges.values()), Set.copyOf(frontier));
    }

    @Transactional(readOnly = true)
    public Optional<GraphBatch> loadAround(long centerId, int depth) {
        var center = events.findById(centerId);
        if (center.isEmpty()) return Optional.empty();

        Set<Long> frontier = new LinkedHashSet<>(Set.of(centerId));
        Set<Long> visited = new LinkedHashSet<>();
        Map<Long, NodeView> nodes = new LinkedHashMap<>();
        nodes.put(centerId, NodeView.from(center.get()));
        Map<String, EdgeView> edges = new LinkedHashMap<>();

        for (int hop = 0; hop < depth && !frontier.isEmpty() && nodes.size() < MAX_NODES; hop++) {
            List<com.economicbriefing.economicflow.entity.EventRelationEntity> connected = new ArrayList<>();
            connected.addAll(relations.findByFromEvent_IdIn(frontier));
            connected.addAll(relations.findByToEvent_IdIn(frontier));
            Set<Long> next = new LinkedHashSet<>();

            for (var edge : connected) {
                var from = edge.getFromEvent();
                var to = edge.getToEvent();
                if (!nodes.containsKey(from.getId()) && nodes.size() < MAX_NODES) {
                    nodes.put(from.getId(), NodeView.from(from));
                }
                if (!nodes.containsKey(to.getId()) && nodes.size() < MAX_NODES) {
                    nodes.put(to.getId(), NodeView.from(to));
                }
                if (!nodes.containsKey(from.getId()) || !nodes.containsKey(to.getId())) continue;

                String key = from.getId() + ":" + to.getId() + ":" + edge.getRelationType();
                edges.putIfAbsent(key, new EdgeView(from.getId(), to.getId(), edge.getRelationType(), edge.getEvidenceArticleId()));
                if (!visited.contains(from.getId())) next.add(from.getId());
                if (!visited.contains(to.getId())) next.add(to.getId());
            }
            visited.addAll(frontier);
            next.removeAll(visited);
            frontier = next;
        }
        return Optional.of(new GraphBatch(
                List.copyOf(nodes.values()), List.copyOf(edges.values()), Set.copyOf(frontier)));
    }

    @Transactional(readOnly = true)
    public GraphBatch loadOverview() {
        Map<Long, NodeView> nodes = new LinkedHashMap<>();
        events.findTop50ByOrderByIdDesc().forEach(node -> nodes.put(node.getId(), NodeView.from(node)));
        Map<String, EdgeView> edges = new LinkedHashMap<>();
        if (!nodes.isEmpty()) {
            for (var edge : relations.findByFromEvent_IdIn(nodes.keySet())) {
                var fromId = edge.getFromEvent().getId();
                var toId = edge.getToEvent().getId();
                if (!nodes.containsKey(toId)) continue;
                String key = fromId + ":" + toId + ":" + edge.getRelationType();
                edges.putIfAbsent(key, new EdgeView(fromId, toId, edge.getRelationType(), edge.getEvidenceArticleId()));
            }
        }
        return new GraphBatch(List.copyOf(nodes.values()), List.copyOf(edges.values()), Set.of());
    }

    public record GraphBatch(List<NodeView> nodes, List<EdgeView> edges, Set<Long> frontierNodeIds) {}
    public record EdgeView(Long fromNodeId, Long toNodeId, EventRelationType relationType, String articleId) {
        public EdgeView(Long fromNodeId, Long toNodeId, EventRelationType relationType) {
            this(fromNodeId, toNodeId, relationType, null);
        }
    }
    public record NodeView(Long nodeId, NodeKind nodeKind, String scopeKey, String subjectKey,
            String slotKey, String valueKey, String title, java.time.LocalDate eventDate) {
        static NodeView from(com.economicbriefing.economicflow.entity.EconomicEventEntity e) {
            return new NodeView(e.getId(), e.getNodeKind(), e.getScopeKey(), e.getSubjectKey(),
                    e.getSlot() == null ? null : e.getSlot().getSlotKey(),
                    e.getSlotValue() == null ? null : e.getSlotValue().getValueKey(),
                    e.getTitle(), e.getEventDate());
        }
    }
}

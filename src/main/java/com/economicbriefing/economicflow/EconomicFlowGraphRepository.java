package com.economicbriefing.economicflow;

import java.util.*;
import com.economicbriefing.economicflow.repository.EventRelationRepository;
import com.economicbriefing.economicflow.repository.EconomicEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EconomicFlowGraphRepository {
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
                        new EdgeView(from.getId(), to.getId(), edge.getRelationType()));
                if (!visited.contains(from.getId())) next.add(from.getId());
            }
            frontier = next;
        }
        frontier.removeAll(visited);
        return new GraphBatch(List.copyOf(nodes.values()), List.copyOf(edges.values()), Set.copyOf(frontier));
    }

    public record GraphBatch(List<NodeView> nodes, List<EdgeView> edges, Set<Long> frontierNodeIds) {}
    public record EdgeView(Long fromNodeId, Long toNodeId, EventRelationType relationType) {}
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

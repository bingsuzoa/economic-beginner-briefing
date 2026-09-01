package com.economicbriefing.economicflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class FlowNodeResolverTest {
    private final FlowNodeResolver.Comparison input = new FlowNodeResolver.Comparison("신규 노드",
            List.of(new FlowNodeResolver.ExistingNode(10L, "기존 노드")));

    @Test
    void shouldIgnoreMatchedNodeForNonSameDecisions() {
        for (var decision : List.of(FlowResolverDecision.RELATED_BUT_DISTINCT, FlowResolverDecision.NO_MATCH)) {
            var normalized = FlowNodeResolver.validate(new FlowNodeResolver.Decision("신규 노드", decision, 10L), input);
            assertEquals(decision, normalized.decision());
            assertNull(normalized.matchedNodeId());
        }
    }

    @Test
    void shouldReuseOnlyKnownSameNode() {
        var valid = FlowNodeResolver.validate(
                new FlowNodeResolver.Decision("신규 노드", FlowResolverDecision.SAME, 10L), input);
        assertEquals(10L, valid.matchedNodeId());
        assertThrows(IllegalArgumentException.class, () -> FlowNodeResolver.validate(
                new FlowNodeResolver.Decision("신규 노드", FlowResolverDecision.SAME, null), input));
        assertThrows(IllegalArgumentException.class, () -> FlowNodeResolver.validate(
                new FlowNodeResolver.Decision("신규 노드", FlowResolverDecision.SAME, 99L), input));
    }
}

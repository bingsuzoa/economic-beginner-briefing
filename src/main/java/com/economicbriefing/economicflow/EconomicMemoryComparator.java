package com.economicbriefing.economicflow;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.economicbriefing.analyzer.openai.OpenAiClient;
import com.economicbriefing.analyzer.openai.util.RetryExecutor;
import com.economicbriefing.config.AppProperties;
import com.economicbriefing.config.OpenAiProperties;
import com.economicbriefing.economicflow.entity.EconomicEventEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class EconomicMemoryComparator {
    private static final String SYSTEM_PROMPT = """
            You classify normalized economic memory candidates. Do not analyze economics or create relations.
            Choose exactly one decision per candidate: REPEATED_STATE, STATE_CHANGED, or NEW_EVENT.
            Use only supplied node IDs. NEW_EVENT requires matchedNodeId=null; other decisions require a supplied ID.
            """;
    private static final String SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"decisions":{"type":"array","items":{
              "type":"object","additionalProperties":false,"properties":{
                "candidateKey":{"type":"string"},
                "decision":{"type":"string","enum":["REPEATED_STATE","STATE_CHANGED","NEW_EVENT"]},
                "matchedNodeId":{"type":["integer","null"]},
                "reason":{"type":"string"}},
              "required":["candidateKey","decision","matchedNodeId","reason"]}}},"required":["decisions"]}
            """;

    private final OpenAiClient client;
    private final ObjectMapper json;
    private final OpenAiProperties openAi;
    private final AppProperties app;

    public EconomicMemoryComparator(ObjectProvider<OpenAiClient> client, ObjectMapper json,
            OpenAiProperties openAi, AppProperties app) {
        this.client = client.getIfAvailable();
        this.json = json;
        this.openAi = openAi;
        this.app = app;
    }

    public Map<String, Decision> compare(List<Comparison> comparisons) {
        if (comparisons.isEmpty()) return Map.of();
        if (client == null) return comparisons.stream().collect(Collectors.toMap(
                c -> c.candidate().candidateKey(), this::deterministicFallback));
        return RetryExecutor.execute(() -> call(comparisons), app.retry());
    }

    private Map<String, Decision> call(List<Comparison> comparisons) {
        try {
            String content = client.completeWithSchema(SYSTEM_PROMPT,
                    json.writeValueAsString(comparisons), openAi.economicFlowComparisonModel(), 0,
                    "economic_memory_decisions", SCHEMA);
            Response response = json.readValue(content, Response.class);
            Map<String, Comparison> inputs = comparisons.stream().collect(
                    Collectors.toMap(c -> c.candidate().candidateKey(), c -> c));
            if (response.decisions() == null || response.decisions().size() != inputs.size()) {
                throw new IllegalArgumentException("Memory decision count mismatch");
            }
            Map<String, Decision> result = response.decisions().stream().collect(
                    Collectors.toMap(Decision::candidateKey, d -> validate(d, inputs.get(d.candidateKey()))));
            if (!result.keySet().equals(inputs.keySet())) throw new IllegalArgumentException("Unknown candidateKey");
            return result;
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException invalid) throw invalid;
            throw new IllegalArgumentException("Invalid memory comparison response", e);
        }
    }

    private Decision validate(Decision decision, Comparison input) {
        if (input == null || decision.decision() == null) throw new IllegalArgumentException("Invalid decision");
        var ids = input.existingNodes().stream().map(ExistingNode::nodeId).collect(Collectors.toSet());
        if (decision.decision() == MemoryDecision.NEW_EVENT) {
            if (decision.matchedNodeId() != null) throw new IllegalArgumentException("NEW_EVENT cannot match node");
        } else if (decision.matchedNodeId() == null || !ids.contains(decision.matchedNodeId())) {
            throw new IllegalArgumentException("Decision matched unknown node");
        }
        return decision;
    }

    private Decision deterministicFallback(Comparison comparison) {
        if (comparison.candidate().nodeKind() == NodeKind.STATE && !comparison.existingNodes().isEmpty()) {
            return new Decision(comparison.candidate().candidateKey(), MemoryDecision.STATE_CHANGED,
                    comparison.existingNodes().getFirst().nodeId(), "dry-run state change");
        }
        return new Decision(comparison.candidate().candidateKey(), MemoryDecision.NEW_EVENT, null, "dry-run new event");
    }

    public static Comparison comparison(EventCandidate candidate, List<EconomicEventEntity> events) {
        return new Comparison(candidate, events.stream().map(e -> new ExistingNode(e.getId(),
                e.getNodeKind(), e.getScopeKey(), e.getSubjectKey(), e.getSlot().getSlotKey(),
                e.getSlotValue() == null ? null : e.getSlotValue().getValueKey(), e.getEventDate(), e.getEndedAt()))
                .toList());
    }

    public record Comparison(EventCandidate candidate, List<ExistingNode> existingNodes) {}
    public record ExistingNode(Long nodeId, NodeKind nodeKind, String scopeKey, String subjectKey,
            String slotKey, String valueKey, java.time.LocalDate startedAt, java.time.LocalDate endedAt) {}
    public record Decision(String candidateKey, MemoryDecision decision, Long matchedNodeId, String reason) {}
    private record Response(List<Decision> decisions) {}
}

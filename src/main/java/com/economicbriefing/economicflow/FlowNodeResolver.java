package com.economicbriefing.economicflow;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.economicbriefing.analyzer.openai.OpenAiClient;
import com.economicbriefing.analyzer.openai.util.RetryExecutor;
import com.economicbriefing.config.AppProperties;
import com.economicbriefing.config.OpenAiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class FlowNodeResolver {
    private static final String SYSTEM_PROMPT = """
            Compare each new economic flow node only with its supplied existing candidates.
            Decide whether it has the same economic meaning, is related but distinct, or has no match.
            SAME means wording differs but the economic fact/state/event is identical.
            matchedNodeId is required only for SAME. For RELATED_BUT_DISTINCT and NO_MATCH return null.
            Never force a match. Do not analyze causes and do not create relations.
            """;
    private static final String SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"decisions":{"type":"array","items":{
              "type":"object","additionalProperties":false,"properties":{
                "newNode":{"type":"string"},
                "decision":{"type":"string","enum":["SAME","RELATED_BUT_DISTINCT","NO_MATCH"]},
                "matchedNodeId":{"type":["integer","null"]}},
              "required":["newNode","decision","matchedNodeId"]}}},"required":["decisions"]}
            """;

    private final OpenAiClient client;
    private final ObjectMapper json;
    private final OpenAiProperties openAi;
    private final AppProperties app;

    public FlowNodeResolver(ObjectProvider<OpenAiClient> client, ObjectMapper json,
            OpenAiProperties openAi, AppProperties app) {
        this.client = client.getIfAvailable();
        this.json = json;
        this.openAi = openAi;
        this.app = app;
    }

    public Map<String, Decision> resolve(List<Comparison> comparisons) {
        if (comparisons.isEmpty()) return Map.of();
        if (client == null) return comparisons.stream().collect(Collectors.toMap(
                Comparison::newNode, c -> new Decision(c.newNode(),
                        FlowResolverDecision.NO_MATCH, null)));
        return RetryExecutor.execute(() -> call(comparisons), app.retry());
    }

    private Map<String, Decision> call(List<Comparison> comparisons) {
        try {
            String content = client.completeWithSchema(SYSTEM_PROMPT, json.writeValueAsString(comparisons),
                    openAi.economicFlowComparisonModel(), 0, "flow_node_resolution", SCHEMA);
            Response response = json.readValue(content, Response.class);
            Map<String, Comparison> inputs = comparisons.stream().collect(
                    Collectors.toMap(Comparison::newNode, c -> c));
            if (response.decisions() == null || response.decisions().size() != inputs.size()) {
                throw new IllegalArgumentException("Flow resolver decision count mismatch");
            }
            Map<String, Decision> result = response.decisions().stream().collect(Collectors.toMap(
                    Decision::newNode, decision -> validate(decision, inputs.get(decision.newNode()))));
            if (!result.keySet().equals(inputs.keySet())) throw new IllegalArgumentException("Unknown newNode");
            return result;
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException invalid) throw invalid;
            throw new IllegalArgumentException("Invalid flow resolver response", e);
        }
    }

    static Decision validate(Decision decision, Comparison input) {
        if (input == null || decision.decision() == null) throw new IllegalArgumentException("Invalid decision");
        var ids = input.existingNodes().stream().map(ExistingNode::nodeId).collect(Collectors.toSet());
        if (decision.decision() == FlowResolverDecision.SAME) {
            if (decision.matchedNodeId() == null || !ids.contains(decision.matchedNodeId())) {
                throw new IllegalArgumentException("SAME matched unknown node");
            }
        } else if (decision.matchedNodeId() != null) {
            return new Decision(decision.newNode(), decision.decision(), null);
        }
        return decision;
    }

    public record Comparison(String newNode, List<ExistingNode> existingNodes) {}
    public record ExistingNode(Long nodeId, String text) {}
    public record Decision(String newNode, FlowResolverDecision decision, Long matchedNodeId) {}
    private record Response(List<Decision> decisions) {}
}

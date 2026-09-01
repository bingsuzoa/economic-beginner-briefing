package com.economicbriefing.economicflow;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.economicbriefing.analyzer.openai.OpenAiClient;
import com.economicbriefing.analyzer.openai.util.RetryExecutor;
import com.economicbriefing.config.AppProperties;
import com.economicbriefing.config.OpenAiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class EconomicFlowTraversalJudge {
    private static final String SYSTEM_PROMPT = """
            Decide whether the supplied evidence-backed real-world graph is sufficient for the current event.
            Never add or infer events, causes, nodes, or edges.
            Return MISSING_REAL_WORLD_CAUSE only with IDs from frontierNodeIds when more historical facts are needed.
            Return ECONOMIC_MECHANISM when only a general mechanism between verified nodes remains.
            """;
    private static final String SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{
              "result":{"type":"string","enum":["MISSING_REAL_WORLD_CAUSE","ECONOMIC_MECHANISM"]},
              "unexplainedNodeIds":{"type":"array","items":{"type":"integer"}},
              "principleQuery":{"type":["string","null"]}},
              "required":["result","unexplainedNodeIds","principleQuery"]}
            """;
    private final OpenAiClient client;
    private final ObjectMapper json;
    private final OpenAiProperties openAi;
    private final AppProperties app;

    public EconomicFlowTraversalJudge(ObjectProvider<OpenAiClient> client, ObjectMapper json,
            OpenAiProperties openAi, AppProperties app) {
        this.client = client.getIfAvailable(); this.json = json; this.openAi = openAi; this.app = app;
    }

    public Result judge(String currentEvent, List<EconomicFlowGraphRepository.NodeView> nodes,
            List<EconomicFlowGraphRepository.EdgeView> edges, Set<Long> frontier) {
        if (frontier.isEmpty() || client == null) {
            return new Result(TraversalDecision.ECONOMIC_MECHANISM, Set.of(),
                    edges.isEmpty() ? null : "검증된 경제 흐름 관계의 일반적인 작동 메커니즘");
        }
        return RetryExecutor.execute(() -> call(currentEvent, nodes, edges, frontier), app.retry());
    }

    private Result call(String currentEvent, List<EconomicFlowGraphRepository.NodeView> nodes,
            List<EconomicFlowGraphRepository.EdgeView> edges, Set<Long> frontier) {
        try {
            String prompt = json.writeValueAsString(new Input(currentEvent, nodes, edges, frontier));
            Result result = json.readValue(client.completeWithSchema(SYSTEM_PROMPT, prompt,
                    openAi.economicFlowTraversalModel(), 0, "economic_flow_traversal", SCHEMA), Result.class);
            if (result.result() == null || result.unexplainedNodeIds() == null
                    || !frontier.containsAll(result.unexplainedNodeIds())) {
                throw new IllegalArgumentException("Traversal returned invalid frontier IDs");
            }
            if (result.result() == TraversalDecision.MISSING_REAL_WORLD_CAUSE
                    && (result.unexplainedNodeIds().isEmpty() || result.principleQuery() != null)) {
                throw new IllegalArgumentException("Missing cause requires frontier IDs only");
            }
            if (result.result() == TraversalDecision.ECONOMIC_MECHANISM
                    && (!result.unexplainedNodeIds().isEmpty() || blank(result.principleQuery()))) {
                throw new IllegalArgumentException("Mechanism requires principleQuery only");
            }
            return result;
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException invalid) throw invalid;
            throw new IllegalArgumentException("Invalid traversal response", e);
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private record Input(String currentEvent, List<EconomicFlowGraphRepository.NodeView> nodes,
            List<EconomicFlowGraphRepository.EdgeView> edges, Set<Long> frontierNodeIds) {}
    public record Result(TraversalDecision result, Set<Long> unexplainedNodeIds, String principleQuery) {}
}

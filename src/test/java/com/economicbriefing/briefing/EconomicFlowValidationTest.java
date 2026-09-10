package com.economicbriefing.briefing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.economicbriefing.article.ParagraphSplitter;
import com.economicbriefing.briefing.DailyBriefingService.Context;
import com.economicbriefing.briefing.DailyBriefingService.Evidence;
import com.economicbriefing.briefing.EconomicFlowLlm.PlanFlow;
import com.economicbriefing.briefing.EconomicFlowLlm.WrittenFlow;
import com.economicbriefing.briefing.ObservationStore.Observation;
import com.economicbriefing.config.OpenAiProperties;
import com.economicbriefing.llm.OpenAiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EconomicFlowValidationTest {
    @Test
    void readsStoredPgVectorForRetryReuse() {
        assertArrayEquals(new float[] { .25f, -.5f }, ObservationStore.parseVector("[0.25,-0.5]"));
    }

    @Test
    void observationRequiresRealSpansAndNumbersFromEvidence() {
        EconomicFlowLlm llm = new EconomicFlowLlm(mock(OpenAiClient.class), mock(OpenAiProperties.class),
                new ObjectMapper(), new ParagraphSplitter());
        ObjectMapper json = new ObjectMapper();
        ArrayNode raw = json.createArrayNode();
        raw.addObject().put("text", "물가가 3% 올랐다").putArray("spanIds").add("P001");
        raw.addObject().put("text", "물가가 9% 올랐다").putArray("spanIds").add("P001");
        raw.addObject().put("text", "근거 없는 문장").putArray("spanIds").add("P999");

        var result = llm.validateObservations(raw, Map.of("P001", "통계청은 물가가 3% 올랐다고 밝혔다."));

        assertEquals(1, result.size());
        assertEquals("물가가 3% 올랐다", result.getFirst().text());
    }

    @Test
    void planMustCoverEverySelectedArticleWithCurrentEvidence() {
        Observation first = new Observation("a:O1", "a", 1, "첫 관측", List.of("P001"), null, null);
        Observation second = new Observation("b:O1", "b", 1, "둘째 관측", List.of("P001"), null, null);
        Map<String, Evidence> current = new LinkedHashMap<>();
        current.put("C01", new Evidence(first, false, null, 1));
        current.put("C02", new Evidence(second, false, null, 1));
        Context context = new Context(current, current, List.of(), List.of(), Map.of());

        assertTrue(DailyBriefingService.validatePlan(
                List.of(new PlanFlow(List.of("C01"), List.of(), "연결")), context)
                .contains("article coverage mismatch"));
        assertTrue(DailyBriefingService.validatePlan(
                List.of(new PlanFlow(List.of("C01", "C02"), List.of(), "연결")), context).isEmpty());
    }

    @Test
    void writerCannotInventNumbers() {
        List<PlanFlow> plan = List.of(new PlanFlow(List.of("C01"), List.of(), "금리는 3%다"));
        assertTrue(DailyBriefingService.validateWriting(
                List.of(new WrittenFlow("F01", "금리 9%", "설명", List.of())), plan, "금리는 3%다")
                .getFirst().contains("numbers outside evidence"));
    }
}

package com.economicbriefing.briefing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;

import com.economicbriefing.article.ParagraphSplitter;
import com.economicbriefing.briefing.DailyBriefingService.Context;
import com.economicbriefing.briefing.DailyBriefingService.Evidence;
import com.economicbriefing.briefing.EconomicFlowLlm.PlanFlow;
import com.economicbriefing.briefing.EconomicFlowLlm.WrittenFlow;
import com.economicbriefing.briefing.EconomicFlowLlm.Writing;
import com.economicbriefing.briefing.EconomicFlowLlm.Question;
import com.economicbriefing.briefing.EconomicFlowLlm.Answer;
import com.economicbriefing.briefing.DailyBriefingService.QuestionContext;
import com.economicbriefing.article.ArticleEntity;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
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
    void plannerSeesSourceActionsOmittedByCompressedObservationsOnlyOnce() {
        ArticleEntity article = new ArticleEntity(); article.setId("a"); article.setBodyStatus("FULL_TEXT");
        article.setBody("발표 후 국채 매도가 이어지면서 금리가 올랐다.\n채권 수익률이라는 용어가 등장했다.\n이 문단은 인접하지 않는다.");
        var snapshot = new ObjectMapper().createObjectNode().put("bodyHash", ObservationStore.bodyHash(article.getBody()));
        snapshot.putObject("spans").put("P001", "발표 후 국채 매도가 이어지면서 금리가 올랐다.");
        var observation = new Observation("a:O1", "a", 1, "금리가 올랐다.", List.of("P001"), null, null, false, snapshot);
        var current = Map.of("C01", new Evidence(observation, false, null, 1), "C02", new Evidence(observation, false, null, 1));
        var context = new Context(current, current, List.of(), List.of(), Map.of());
        String input = DailyBriefingService.plannerInput(context, List.of(article), new ParagraphSplitter());
        assertTrue(input.contains("국채 매도가 이어지면서"));
        assertEquals(1, input.split("P001", -1).length - 1);
        assertTrue(input.contains("채권 수익률이라는 용어"));
        assertTrue(!input.contains("이 문단은 인접하지 않는다"));
        article.setBody(article.getBody() + "\n후대 수정");
        assertTrue(!DailyBriefingService.plannerInput(context, List.of(article), new ParagraphSplitter()).contains("채권 수익률이라는 용어"));
        article.setBody("발표 후 국채 매도가 이어지면서 금리가 올랐다.\n" + "긴 문단".repeat(1000));
        snapshot.put("bodyHash", ObservationStore.bodyHash(article.getBody()));
        assertTrue(!DailyBriefingService.plannerInput(context, List.of(article), new ParagraphSplitter()).contains("긴 문단"));
    }

    @Test
    void questionRetrievalKeepsLateReasonsInShortArticles() {
        var splitter = new ParagraphSplitter();
        var paragraphs = splitter.split("[사진 설명]\n정책 발표를 전했다.\n시장 반응을 전했다.\n시행 시점을 전했다.\n거래 규모를 전했다.\n후속 일정을 전했다.\n추가 발언을 전했다.\n앞선 장관 발언이 기대를 키웠다고 설명했다.");
        var selected = splitter.questionEvidence(paragraphs, List.of("정책"));
        assertTrue(selected.stream().anyMatch(p -> p.getKey().equals("P008")));
        assertTrue(selected.stream().noneMatch(p -> p.getKey().equals("P001")));
        var longArticle = new LinkedHashMap<String, String>();
        for (int i = 1; i <= 8; i++) longArticle.put("P" + i, "긴 배경 설명입니다. ".repeat(40));
        longArticle.put("P009", "정책의 핵심 제약을 설명했다.");
        var bounded = splitter.questionEvidence(longArticle, List.of("정책"));
        assertEquals(6, bounded.size());
        assertEquals("P009", bounded.getFirst().getKey());
    }

    @Test
    void insufficientBudgetStopsBeforeAnyPaidCall() {
        var articles = mock(com.economicbriefing.article.ArticleRepository.class);
        var briefings = mock(DailyBriefingRepository.class);
        when(briefings.save(any())).thenAnswer(call -> call.getArgument(0));
        var llm = mock(EconomicFlowLlm.class);
        var client = mock(OpenAiClient.class);
        var app = mock(com.economicbriefing.config.AppProperties.class);
        when(app.budget()).thenReturn(new com.economicbriefing.config.AppProperties.BudgetProperties(50000, 100000, 15000, .001));
        var service = new DailyBriefingService(articles, briefings, mock(com.economicbriefing.article.YonhapBodyFetcher.class),
                new ParagraphSplitter(), mock(ObservationStore.class), mock(PrincipleStore.class), llm, client,
                mock(OpenAiProperties.class), app, new ObjectMapper());
        ArticleEntity article = new ArticleEntity(); article.setId("a"); article.setTitle("국채 매입 발표");
        article.setPublishedAt(OffsetDateTime.parse("2026-09-10T06:41:06+09:00"));
        article.setBodyStatus("FULL_TEXT"); article.setBody("국채 매입 계획을 발표했다.");
        for (boolean selectedByUser : List.of(false, true)) {
            var error = assertThrows(IllegalStateException.class,
                    () -> service.review(java.time.LocalDate.parse("2026-09-11"), article, selectedByUser));
            assertTrue(error.getMessage().contains("daily cost ceiling"));
        }
        verifyNoInteractions(llm, client);
    }

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
        Observation first = new Observation("a:O1", "a", 1, "첫 관측", List.of("P001"), null, null, false, null);
        Observation second = new Observation("b:O1", "b", 1, "둘째 관측", List.of("P001"), null, null, false, null);
        Map<String, Evidence> current = new LinkedHashMap<>();
        current.put("C01", new Evidence(first, false, null, 1));
        current.put("C02", new Evidence(second, false, null, 1));
        Context context = new Context(current, current, List.of(), List.of(), Map.of());

        assertTrue(DailyBriefingService.validatePlan(
                List.of(new PlanFlow(List.of("C01"), List.of(), "연결", List.of())), context)
                .contains("article coverage mismatch"));
        assertTrue(DailyBriefingService.validatePlan(
                List.of(new PlanFlow(List.of("C01", "C02"), List.of(), "연결", List.of())), context).isEmpty());
    }

    @Test
    void writerRejectsUncitedNumbersForeignEvidenceAndConflicts() {
        Observation o = new Observation("a:O1", "a", 1, "금리는 3%다", List.of("P001"), null, null, true, null);
        Map<String, Evidence> evidence = Map.of("C01", new Evidence(o, false, null, 1));
        Context context = new Context(evidence, evidence, List.of(), List.of(), Map.of());
        Question q = new Question("왜?", "원리가 필요하다", List.of("C01"), "", "", List.of());
        List<PlanFlow> plan = List.of(new PlanFlow(List.of("C01"), List.of(), "금리는 9%다", List.of(q)));
        QuestionContext source = new QuestionContext(); source.evidence.putAll(evidence);
        Map<String, QuestionContext> questions = Map.of("F01:Q01", source);
        Writing good = new Writing(List.of(new WrittenFlow("F01", "금리", "3%예요.",
                List.of(new Answer("Q01", "3%예요.", List.of("C01"), List.of())))), List.of());
        assertTrue(DailyBriefingService.validateWriting(good, plan, context, questions).isEmpty());
        Writing bad = new Writing(List.of(new WrittenFlow("F01", "금리 9%", "설명",
                List.of(new Answer("Q01", "7%예요.", List.of("R999"), List.of())))), List.of("F01:C01"));
        var errors = DailyBriefingService.validateWriting(bad, plan, context, questions);
        assertTrue(errors.stream().anyMatch(e -> e.contains("numbers outside evidence")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("unknown answer evidence")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("source conflicts")));
    }

    @Test
    void memoryKeepsExactSourceWhenArticleChanges() {
        ArticleEntity article = new ArticleEntity(); article.setId("a"); article.setSource("연합뉴스");
        article.setTitle("투자"); article.setUrl("https://example.com/article");
        article.setPublishedAt(OffsetDateTime.parse("2026-09-09T10:00:00+09:00"));
        article.setBody("투자 이유를 설명한 원문이에요.");
        ObservationStore store = new ObservationStore(mock(JdbcTemplate.class), new ObjectMapper(), new ParagraphSplitter());
        var o = store.fromDraft(article, 1, new ObservationStore.Draft("투자 이유", List.of("P001"), true, "배경 설명"));
        article.setBody("나중에 변경된 원문이에요.");
        assertEquals("투자 이유를 설명한 원문이에요.", o.snapshot().path("spans").path("P001").asText());
        assertTrue(o.remember());
        assertTrue(!o.snapshot().path("bodyHash").asText().equals(ObservationStore.bodyHash(article.getBody())));
    }
}

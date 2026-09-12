package com.economicbriefing.briefing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

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
    void explicitQuestionEvidenceCompletesParentScopeWithoutAcceptingUnknownIds() {
        var a = new Observation("a:O1","a",1,"원인",List.of(),null,null,false,null);
        var b = new Observation("b:O1","b",1,"반대 압력",List.of(),null,null,false,null);
        var current = Map.of("C01",new Evidence(a,false,null,1),"C02",new Evidence(b,false,null,1));
        var context = new Context(current,current,List.of(),List.of(),Map.of());
        var question = new Question("왜 달라져요?","반대 압력",List.of("C02"),"","",List.of());
        var flow = new PlanFlow(List.of("C01"),List.of(),"두 압력이 맞선다",List.of(question));
        var completed = DailyBriefingService.includeQuestionEvidence(List.of(flow),context);
        assertEquals(List.of("C01","C02"),completed.getFirst().observationIds());
        assertTrue(DailyBriefingService.validatePlan(completed,context).isEmpty());
        var unknown = new Question("왜 달라져요?","미확인",List.of("C99"),"","",List.of());
        var invalid = DailyBriefingService.includeQuestionEvidence(List.of(
                new PlanFlow(List.of("C01"),List.of(),"관계",List.of(unknown))),context);
        assertEquals(List.of("C01"),invalid.getFirst().observationIds());
        assertTrue(DailyBriefingService.validatePlan(invalid,context).contains("question outside flow evidence"));
    }

    @Test
    void questionSourcesDoNotLeakLaterIndicatorsFromTheSameFlow() {
        var ppi = new Observation("a:O1","a",1,"장중 PPI 경계",List.of(),null,null,false,null);
        var cpi = new Observation("b:O1","b",1,"밤 CPI 결과",List.of(),null,null,false,null);
        var history = new Observation("past:O1","past",1,"과거 PPI",List.of(),null,null,true,null);
        var current = Map.of("C01",new Evidence(ppi,false,null,1),"C02",new Evidence(cpi,false,null,1));
        var aliases = new LinkedHashMap<>(current); aliases.put("H01",new Evidence(history,true,"C01",1));
        var context = new Context(current,aliases,List.of(),List.of(),Map.of());
        var flow = new PlanFlow(List.of("C01","C02","H01"),List.of(),"물가와 시장",List.of());
        var question = new Question("PPI가 왜 부담이에요?","높은 수준",List.of("C01"),"","",List.of());
        var source = DailyBriefingService.questionSources(flow,question,context);
        assertEquals(java.util.Set.of("C01","H01"),source.evidence.keySet());
        assertTrue(!source.evidence.containsKey("C02"));
    }

    @Test
    void writerSchemaRestrictsEvidenceForEachFlowAndQuestionIncludingEmptyScopes() {
        var llm = new EconomicFlowLlm(mock(OpenAiClient.class),mock(OpenAiProperties.class),new ObjectMapper(),new ParagraphSplitter());
        var scopes = List.of(new EconomicFlowLlm.WritingScope("F02",List.of(
                new EconomicFlowLlm.AnswerScope("Q01",List.of("C02"),List.of()),
                new EconomicFlowLlm.AnswerScope("Q02",List.of("C03"),List.of("K01")))),
                new EconomicFlowLlm.WritingScope("F03",List.of()));
        var flows = llm.writingSchema(scopes).path("properties").path("flows");
        assertEquals(2,flows.path("minItems").asInt());
        var choice = flows.path("items").path("anyOf").get(0).path("properties");
        assertEquals("F02",choice.path("flowId").path("enum").get(0).asText());
        var answers = choice.path("questions").path("items").path("anyOf");
        assertEquals("C02",answers.get(0).path("properties").path("evidenceIds").path("items").path("enum").get(0).asText());
        assertEquals(0,answers.get(0).path("properties").path("principleIds").path("maxItems").asInt(-1));
        assertEquals("C03",answers.get(1).path("properties").path("evidenceIds").path("items").path("enum").get(0).asText());
        assertEquals(0,flows.path("items").path("anyOf").get(1).path("properties").path("questions").path("maxItems").asInt(-1));
    }

    @Test
    void moreShortRelationshipsShareTheOriginalTotalTextBudget() {
        var json = new ObjectMapper(); var llm = new EconomicFlowLlm(mock(OpenAiClient.class),mock(OpenAiProperties.class),json,new ParagraphSplitter());
        for (int length : List.of(110,120)) {
            var raw = json.createArrayNode(); StringBuilder source = new StringBuilder();
            for (String letter : List.of("가","나","다","라","마","바")) {
                String text = letter.repeat(length); source.append(text);
                var item = raw.addObject().put("text",text).put("remember",false).put("memoryReason","관계");
                item.putArray("spanIds").add("P001");
            }
            var accepted = llm.validateObservations(raw,Map.of("P001",source.toString()));
            assertEquals(length==110 ? 6 : 5,accepted.size());
            assertTrue(accepted.stream().mapToInt(item->item.text().length()).sum()<=660);
        }
    }

    @Test
    void writerBatchesKeepGlobalFlowIdsAndOnlyTheirQuestionEvidence() {
        var first = new Observation("a:O1","a",1,"첫 근거 ".repeat(250),List.of(),null,null,false,null);
        var second = new Observation("b:O1","b",1,"둘째 근거 ".repeat(250),List.of(),null,null,false,null);
        var evidence = Map.of("C01",new Evidence(first,false,null,1),"C02",new Evidence(second,false,null,1));
        var context = new Context(evidence,evidence,List.of(),List.of(),Map.of());
        var q1 = new Question("왜 올라요?","가격 형성",List.of("C01"),"","",List.of());
        var q2 = new Question("왜 내려요?","반대 압력",List.of("C02"),"","",List.of());
        var plan = List.of(new PlanFlow(List.of("C01"),List.of(),"첫 연결",List.of(q1)),
                new PlanFlow(List.of("C02"),List.of(),"둘째 연결",List.of(q2)));
        var source1 = new QuestionContext(); source1.evidence.put("C01",evidence.get("C01"));
        var source2 = new QuestionContext(); source2.evidence.put("C02",evidence.get("C02"));
        var questions = Map.of("F01:Q01",source1,"F02:Q01",source2);
        var combined = DailyBriefingService.writerBatches(plan,context,questions,15000);
        assertEquals(1,combined.size());
        var separate = DailyBriefingService.writerBatches(plan,context,questions,1600);
        assertEquals(2,separate.size());
        assertTrue(separate.get(0).input().contains("<F01>") && !separate.get(0).input().contains("둘째 근거"));
        assertTrue(separate.get(1).input().contains("<F02>") && !separate.get(1).input().contains("첫 근거"));
        assertTrue(separate.stream().allMatch(batch -> DailyBriefingService.estimateTokens(batch.input()) <= 1600));
        assertThrows(IllegalStateException.class, () -> DailyBriefingService.writerBatches(plan,context,questions,100));
    }

    @Test
    void writerSharesIdenticalSourceSpansWithoutMergingEvidenceIds() {
        var snapshot = new ObjectMapper().createObjectNode().put("bodyHash", "snapshot");
        snapshot.putObject("spans").put("P001", "물가 우려와 금리 변화의 공통 근거 문단.");
        var first = new Observation("a:O1", "a", 1, "물가 우려", List.of("P001"), null, null, false, snapshot);
        var second = new Observation("a:O2", "a", 2, "금리 변화", List.of("P001"), null, null, false, snapshot);
        var evidence = new LinkedHashMap<String,Evidence>();
        evidence.put("C01", new Evidence(first,false,null,1)); evidence.put("C02", new Evidence(second,false,null,1));
        String input = DailyBriefingService.evidenceCatalog(evidence);
        assertEquals(1, input.split("공통 근거 문단", -1).length - 1);
        assertEquals(2, input.split("sources=S001", -1).length - 1);
        assertTrue(input.contains("C01\t") && input.contains("C02\t"));
        var otherRevision = new ObjectMapper().createObjectNode().put("bodyHash", "changed");
        otherRevision.putObject("spans").put("P001", "나중에 수정된 다른 근거.");
        evidence.put("C03", new Evidence(new Observation("a:O3","a",3,"수정",List.of("P001"),null,null,false,otherRevision),false,null,1));
        assertTrue(DailyBriefingService.evidenceCatalog(evidence).contains("sources=S002"));
    }

    @Test
    void extractionSchemaOnlyAllowsIndividualVisibleSourceParagraphs() {
        var json = new ObjectMapper(); var client = mock(OpenAiClient.class);
        when(client.complete(any(), any(), any(), any(), any(), any(), any(), anyInt())).thenAnswer(call -> {
            var allowed = call.getArgument(6, com.fasterxml.jackson.databind.JsonNode.class).path("properties")
                    .path("observations").path("items").path("properties").path("spanIds").path("items").path("enum");
            assertEquals(json.readTree("[\"P002\",\"P004\"]"), allowed);
            assertTrue(!call.getArgument(2, String.class).contains("사진 설명"));
            return new OpenAiClient.LlmResult(json.readTree("{\"observations\":[]}"), new OpenAiClient.Usage(0,0,0,0));
        });
        var llm = new EconomicFlowLlm(client, mock(OpenAiProperties.class), json, new ParagraphSplitter());
        var article = new ArticleEntity(); article.setTitle("금리 상승");
        var source = new LinkedHashMap<String,String>();
        source.put("P001", "[사진 설명]"); source.put("P002", "발표 전 금리 인상을 우려했다.");
        source.put("P003", "기자 reporter@example.com"); source.put("P004", "저녁에 물가가 발표됐다.");
        llm.extract(new EconomicFlowLlm.SelectedArticle(article,"시장 변화"), source);
    }

    @Test
    void writerAcceptsLocalAndFlowQualifiedQuestionIds() {
        assertEquals("Q03", EconomicFlowLlm.localQuestionId("Q03"));
        assertEquals("Q03", EconomicFlowLlm.localQuestionId("F02:Q03"));
    }

    @Test
    void writerSchemaPreservesTheVariableNumberOfPlannedFlows() {
        var json = new ObjectMapper();
        var client = mock(OpenAiClient.class);
        when(client.complete(any(), any(), any(), any(), any(), any(), any(), anyInt())).thenAnswer(call -> {
            int expected = Integer.parseInt(call.getArgument(2, String.class));
            var flows = call.getArgument(6, com.fasterxml.jackson.databind.JsonNode.class).path("properties").path("flows");
            assertEquals(expected, flows.path("minItems").asInt());
            assertEquals(expected, flows.path("maxItems").asInt());
            return new OpenAiClient.LlmResult(json.createObjectNode(), new OpenAiClient.Usage(0, 0, 0, 0));
        });
        var llm = new EconomicFlowLlm(client, mock(OpenAiProperties.class), json, new ParagraphSplitter());
        for (int count : List.of(1, 5)) llm.write(Integer.toString(count), count);
    }

    @Test
    void plannerSeesSourceActionsOmittedByCompressedObservationsOnlyOnce() {
        ArticleEntity article = new ArticleEntity(); article.setId("a"); article.setBodyStatus("FULL_TEXT");
        article.setBody("발표 후 국채 매도가 이어지면서 금리가 올랐다.\n채권 수익률이라는 용어가 등장했다.\n이 문단은 인접하지 않는다.");
        var snapshot = new ObjectMapper().createObjectNode().put("bodyHash", ObservationStore.bodyHash(article.getBody()));
        snapshot.putObject("spans").put("P001", "발표 후 국채 매도가 이어지면서 금리가 올랐다.");
        var publication = OffsetDateTime.parse("2026-09-11T23:02:52+09:00");
        var observation = new Observation("a:O1", "a", 1, "금리가 올랐다.", List.of("P001"), publication, null, false, snapshot);
        var current = Map.of("C01", new Evidence(observation, false, null, 1), "C02", new Evidence(observation, false, null, 1));
        var context = new Context(current, current, List.of(), List.of(), Map.of());
        String input = DailyBriefingService.plannerInput(context, List.of(article), new ParagraphSplitter());
        assertTrue(input.contains("A01\t2026-09-11T23:02:52+09:00\n"));
        assertEquals(1, input.split("2026-09-11T23:02:52", -1).length - 1);
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
    void plannerSharesBoundedVerbatimContextAcrossArticlesWithoutSplittingParagraphs() {
        var groups = new LinkedHashMap<String,List<String>>();
        groups.put("long", List.of("first\n", "second paragraph\n", "third\n"));
        groups.put("short", List.of("other\n"));
        var value = new StringBuilder();
        assertEquals(12, DailyBriefingService.appendBalancedEvidence(value, groups, 12));
        assertEquals("first\nother\n", value.toString());
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
    void writerAddsAnAllowedSourceWhenItsNumberWasNotCited() {
        Observation general = new Observation("a:O1", "a", 1, "탄소포집을 활용한다", List.of("P001"), null, null, true, null);
        Observation named = new Observation("a:O2", "a", 2, "SAN-7 사업이다", List.of("P002"), null, null, true, null);
        QuestionContext source = new QuestionContext();
        source.evidence.put("C01", new Evidence(general, false, null, 1));
        source.evidence.put("C02", new Evidence(named, false, null, 1));
        Writing raw = new Writing(List.of(new WrittenFlow("F01", "제목", "설명", List.of(
                new Answer("Q01", "SAN-7에서 활용해요.", List.of("C01"), List.of())))), List.of());
        Writing completed = DailyBriefingService.completeNumberCitations(raw, Map.of("F01:Q01", source));
        assertEquals(List.of("C01", "C02"), completed.flows().getFirst().questions().getFirst().evidenceIds());
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

package com.economicbriefing.briefing;

import com.economicbriefing.article.*;
import com.economicbriefing.config.*;
import com.economicbriefing.llm.OpenAiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MacroScreeningTest {
    private final ObjectMapper json = new ObjectMapper();
    private final EconomicFlowLlm llm = mock(EconomicFlowLlm.class);
    private final ArticleRepository articles = mock(ArticleRepository.class);
    private final DailyBriefingRepository briefings = mock(DailyBriefingRepository.class);
    private final YonhapBodyFetcher fetcher = mock(YonhapBodyFetcher.class);
    private final LocalDate date = LocalDate.of(2025, 4, 3);

    @Test void recallSafeguardSurvivesMergeButNeverBypassesSemanticSelection() throws Exception {
        var window = new ArrayList<ArticleEntity>();
        for (int i = 0; i < 90; i++) window.add(article("n" + i, "일반 후보 " + i, i / 30 * 6));
        var policy = article("policy", "금통위원들 물가 대응 필요성 설명", 1);
        var supply = article("supply", "송유관 중단에 원유 수급 차질 우려", 7);
        var bank = article("bank", "은행채 차환 부담 커져", 13);
        var company = article("company", "새로운 제품 출시", 13);
        window.addAll(List.of(policy, supply, bank, company));
        when(articles.findByPublishedAtGreaterThanEqualAndPublishedAtLessThanOrderByPublishedAtAsc(any(), any())).thenReturn(window);
        when(briefings.save(any())).thenAnswer(call -> call.getArgument(0));
        when(llm.prefilter(eq(date), anyList(), eq(40))).thenAnswer(call -> {
            List<ArticleEntity> group = call.getArgument(1);
            return new EconomicFlowLlm.Call<>(group.stream().map(ArticleEntity::getId).filter(id -> id.startsWith("n")).toList(), zero());
        });
        when(llm.prefilter(eq(date), anyList(), eq(80)))
                .thenReturn(new EconomicFlowLlm.Call<>(List.of("n0"), zero()));
        when(llm.select(eq(date), anyList())).thenAnswer(call -> {
            List<ArticleEntity> pool = call.getArgument(1);
            assertEquals(List.of("n0", "policy", "supply", "bank"), pool.stream().map(ArticleEntity::getId).toList());
            return new EconomicFlowLlm.Call<>(new EconomicFlowLlm.Selection(List.of(), List.of()), zero(), json.createObjectNode());
        });
        var run = service(50000, .14).run(date, "TEST", true).orElseThrow();
        var trace = json.readTree(run.getTraceJson());
        assertEquals("SUCCESS", run.getStatus());
        assertEquals(4, trace.path("prefilterRounds").size());
        assertEquals("dailyMerge", trace.path("prefilterRounds").get(3).path("stage").asText());
        assertEquals(List.of("policy", "supply", "bank"), json.convertValue(trace.path("hardSignalRescuedArticleIds"), List.class));
        assertTrue(trace.path("selectedArticleIds").isEmpty());
        verify(fetcher, times(4)).fetchBefore(anyString(), any());
    }

    @Test void screeningInputAndCostGuardsStopBeforePaidCalls() {
        when(articles.findByPublishedAtGreaterThanEqualAndPublishedAtLessThanOrderByPublishedAtAsc(any(), any()))
                .thenReturn(List.of(article("policy", "금통위 물가 대응 필요성 설명", 1)));
        when(briefings.save(any())).thenAnswer(call -> call.getArgument(0));
        assertThrows(IllegalStateException.class, () -> service(1, .14).run(date, "TEST", true));
        assertThrows(IllegalStateException.class, () -> service(50000, .0001).run(date, "TEST", true));
        verifyNoInteractions(llm, fetcher);
    }

    private DailyBriefingService service(int inputBudget, double cost) {
        var app = new AppProperties(new AppProperties.TimeoutProperties(Duration.ofSeconds(30)),
                new AppProperties.SchedulerProperties(false, "0 5 * * * *", "0 10 5 * * *"),
                new AppProperties.BudgetProperties(inputBudget, 100000, 15000, cost));
        return new DailyBriefingService(articles, briefings, fetcher, new ParagraphSplitter(), mock(ObservationStore.class),
                mock(PrincipleStore.class), llm, mock(OpenAiClient.class),
                new OpenAiProperties("test",Duration.ofSeconds(1),"luna","luna","terra","luna","embedding"), app, json);
    }
    private ArticleEntity article(String id, String title, int hour) {
        var a = new ArticleEntity(); a.setId(id); a.setSourceArticleId(id); a.setTitle(title); a.setUrl("https://example.test/"+id);
        a.setPublishedAt(date.minusDays(1).atTime(5,0).atOffset(ZoneOffset.ofHours(9)).plusHours(hour)); return a;
    }
    private OpenAiClient.Usage zero() { return new OpenAiClient.Usage(0,0,0,0); }
}

package com.economicbriefing.briefing;

import com.economicbriefing.article.ArticleEntity;
import com.economicbriefing.article.ParagraphSplitter;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SelectionEvidenceTest {
    @Test void capitalMovementInInterviewTailSurvivesGenericMarketCommentary() {
        String lead="재무장관이 청문회에서 채권시장 상황을 설명했다.";
        String filler="물가와 금리에 대한 우려가 시장에 영향을 미쳤다고 전망했다.";
        String tail="엔화 강세는 수출에 유리하고 일본의 달러 자산 매각을 줄여 자금 회수 부담을 낮춘다고 설명했다.";
        var a=article(lead+"\n"+(filler+"\n").repeat(30)+tail);
        String excerpt=SelectionEvidence.excerpt(a,new ParagraphSplitter(),400);
        assertTrue(excerpt.contains(tail));
        assertTrue(excerpt.contains(lead));
        assertTrue(excerpt.length()<=400);
    }

    @Test void selectionKeepsCalendarDateAndKstWhenDatabaseReturnsUtc() {
        var a=article("중앙은행은 기준금리를 인상했다.");
        a.setPublishedAt(OffsetDateTime.parse("2026-09-16T18:01:02Z"));
        String input=EconomicFlowLlm.selectionInput(LocalDate.of(2026,9,17),List.of(a));
        assertTrue(input.contains("09-17 03:01 KST"));
        assertFalse(input.contains("\t18:01\t"));
    }
    @Test void explanatoryTailSurvivesPriceListsWithoutInventingOrTruncatingEvidence() {
        String lead = "중앙은행의 정책 결정을 앞두고 채권시장이 움직였다.";
        String tail = "정책금리를 동결할 경우 물가 대응 신뢰가 약해져 장기금리가 오를 수 있다는 전망이다.";
        var a = article(lead + "\n" + ("만기별 거래 가격은 높은 수준을 기록했다.\n").repeat(45) + tail);
        var splitter = new ParagraphSplitter();
        String excerpt = SelectionEvidence.excerpt(a,splitter);
        assertTrue(excerpt.contains("P001: " + lead));
        assertTrue(excerpt.contains("P047: " + tail));
        assertTrue(excerpt.length() <= 900);
        for (String part : excerpt.split(" \\| ")) {
            String[] span = part.split(": ",2);
            assertEquals(splitter.split(a.getBody()).get(span[0]),span[1]);
        }
    }
    @Test void staleBodyIsNotPresentedAsEvidenceAndInputBudgetUsesActualContext() {
        var a = article("의사록은 지난 회의에서 환율과 물가를 검토했다고 밝혔다.");
        String input = EconomicFlowLlm.selectionInput(LocalDate.of(2025,4,3),List.of(a));
        assertTrue(input.contains("P001: 의사록"));
        a.setBodyStatus("RSS_ONLY");
        input = EconomicFlowLlm.selectionInput(LocalDate.of(2025,4,3),List.of(a));
        assertTrue(input.contains("원문 미확보"));
        assertFalse(input.contains("의사록은 지난 회의"));
    }
    @Test void largeCandidatePoolsBoundSourceContextWithoutTruncatingParagraphs() {
        var candidates = new ArrayList<ArticleEntity>();
        String paragraph = "금리와 물가의 반대 경로를 전문가가 조건부 전망으로 설명했다.";
        for (int i = 0; i < 80; i++) candidates.add(article((paragraph + "\n").repeat(40)));
        String input = EconomicFlowLlm.selectionInput(LocalDate.of(2025,4,3),candidates);
        int sourceChars = 0;
        for (String line : input.split("\n")) if (line.startsWith("A")) {
            String evidence = line.split("\t",-1)[4];
            sourceChars += evidence.length();
            for (String span : evidence.split(" \\| ")) assertEquals(paragraph,span.split(": ",2)[1]);
        }
        assertTrue(sourceChars <= 45_000);
        assertTrue(sourceChars > 30_000);
    }
    private ArticleEntity article(String body) {
        var a = new ArticleEntity(); a.setId("example"); a.setTitle("금리 변화"); a.setSummary("RSS 요약");
        a.setPublishedAt(OffsetDateTime.parse("2025-04-02T10:00:00+09:00"));
        a.setBody(body); a.setBodyStatus("FULL_TEXT"); return a;
    }
}

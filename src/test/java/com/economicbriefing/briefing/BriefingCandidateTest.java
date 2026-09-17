package com.economicbriefing.briefing;

import com.economicbriefing.article.ArticleEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BriefingCandidateTest {
    @Test void updatedEditionUsesExplicitCutoffWithoutMovingRegularWindow() {
        var date=java.time.LocalDate.parse("2026-09-17");
        var now=OffsetDateTime.parse("2026-09-17T22:00:00+09:00");
        assertEquals("2026-09-17T05:00+09:00",DailyBriefingService.windowEnd(date,null,now).toString());
        assertEquals("2026-09-17T10:00+09:00",DailyBriefingService.windowEnd(date,
                OffsetDateTime.parse("2026-09-17T01:00:00Z"),now).toString());
        for (String invalid:List.of("2026-09-17T04:59:00+09:00","2026-09-18T05:00:00+09:00","2026-09-17T22:01:00+09:00"))
            assertThrows(IllegalArgumentException.class,()->DailyBriefingService.windowEnd(date,OffsetDateTime.parse(invalid),now));
    }
    private ArticleEntity article(String id, String title, int hour) {
        var value = new ArticleEntity(); value.setId(id); value.setSourceArticleId(id);
        value.setTitle(title); value.setPublishedAt(OffsetDateTime.parse("2026-09-17T0" + hour + ":00:00+09:00"));
        return value;
    }

    @Test void actualDecisionBulletinIsNotLostWhileEarlierForecastRemains() {
        var forecast = article("forecast", "중앙은행 결정 앞두고 관망", 1);
        var decision = article("decision", "[1보] 중앙은행 기준금리 인상", 3);
        assertEquals(List.of(forecast, decision), DailyBriefingService.canonical(List.of(decision, forecast)));
    }

    @Test void sameWireKeepsItsLatestVersionWithoutDuplicatingTheBulletin() {
        var bulletin = article("decision", "[1보] 중앙은행 기준금리 인상", 3);
        var full = article("decision", "중앙은행 금리 인상…물가 우려(종합)", 4);
        assertEquals(List.of(full), DailyBriefingService.canonical(List.of(full, bulletin)));
    }
}

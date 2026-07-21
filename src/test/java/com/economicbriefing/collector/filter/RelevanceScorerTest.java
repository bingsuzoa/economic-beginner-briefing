package com.economicbriefing.collector.filter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RelevanceScorerTest {

    private final RelevanceScorer scorer = new RelevanceScorer();
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @Test
    void shouldScoreHighForDirectPolicyImpact() {
        Article article = createArticle("기준금리 인하 결정 발표", "기준금리 인하로 가계 대출에 영향");
        RelevanceScorer.RelevanceScoringResult result = scorer.score(List.of(article), 0);

        assertEquals(5, result.scores().get(0).score());
    }

    @Test
    void shouldScoreMediumForSignificantImpact() {
        Article article = createArticle("아파트 가격 상승세 지속", "집값 상승이 계속되고 있다");
        RelevanceScorer.RelevanceScoringResult result = scorer.score(List.of(article), 0);

        assertEquals(4, result.scores().get(0).score());
    }

    @Test
    void shouldScoreBaselineForEconomicCategoryOnly() {
        Article article = createArticle("경제 관련 일반 기사", "특별한 키워드 없음");
        RelevanceScorer.RelevanceScoringResult result = scorer.score(List.of(article), 0);

        assertEquals(2, result.scores().get(0).score());
    }

    @Test
    void shouldFilterByMinScore() {
        Article high = createArticle("기준금리 인하 결정", "정책 변경");
        Article low = createArticle("일반 경제 기사", "내용");

        RelevanceScorer.RelevanceScoringResult result = scorer.score(List.of(high, low), 3);

        assertEquals(1, result.filtered().size());
        assertEquals(1, result.excluded().size());
    }

    @Test
    void shouldTrackMatchedKeywords() {
        Article article = createArticle("기준금리 인하로 ETF 시장 변동", "원달러 환율 영향");
        RelevanceScorer.RelevanceScoringResult result = scorer.score(List.of(article), 0);

        assertFalse(result.scores().get(0).matchedKeywords().isEmpty());
    }

    private Article createArticle(String title, String summary) {
        return new Article("test-id", title, summary, "테스트소스",
                ArticleSourceType.NEWS_MEDIA,
                OffsetDateTime.of(2026, 7, 20, 12, 0, 0, 0, KST),
                OffsetDateTime.now(KST),
                "https://example.com/test",
                List.of(NewsCategory.INTEREST_RATE), "ko", null);
    }
}

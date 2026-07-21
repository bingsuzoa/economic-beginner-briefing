package com.economicbriefing.analyzer.openai.prompt;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.economicbriefing.domain.analysis.AudienceProfile;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisPromptBuilderTest {

    @Test
    void shouldBuildPromptWithArticles() {
        List<Article> articles = createArticles();
        AudienceProfile audience = new AudienceProfile(
                "beginner",
                List.of(NewsCategory.INTEREST_RATE, NewsCategory.HOUSING),
                List.of("신혼부부")
        );

        String prompt = AnalysisPromptBuilder.build(
                articles, LocalDate.of(2025, 1, 15), 5, audience);

        assertTrue(prompt.contains("대상 날짜: 2025-01-15"));
        assertTrue(prompt.contains("최대 선별 뉴스 수: 5"));
        assertTrue(prompt.contains("전체 기사 수: 2"));
    }

    @Test
    void shouldIncludeAudienceProfile() {
        List<Article> articles = createArticles();
        AudienceProfile audience = new AudienceProfile(
                "beginner",
                List.of(NewsCategory.INTEREST_RATE, NewsCategory.LOAN),
                List.of("신혼부부", "주택 구입 준비")
        );

        String prompt = AnalysisPromptBuilder.build(
                articles, LocalDate.of(2025, 1, 15), 5, audience);

        assertTrue(prompt.contains("경제 지식 수준: 초보자"));
        assertTrue(prompt.contains("interest_rate"));
        assertTrue(prompt.contains("loan"));
        assertTrue(prompt.contains("신혼부부"));
    }

    @Test
    void shouldFormatArticleDetails() {
        List<Article> articles = createArticles();
        AudienceProfile audience = new AudienceProfile(
                "beginner",
                List.of(NewsCategory.INTEREST_RATE),
                List.of("테스트")
        );

        String prompt = AnalysisPromptBuilder.build(
                articles, LocalDate.of(2025, 1, 15), 5, audience);

        assertTrue(prompt.contains("--- 기사 1 ---"));
        assertTrue(prompt.contains("ID: article-1"));
        assertTrue(prompt.contains("제목: 기준금리 인하"));
        assertTrue(prompt.contains("출처: 한국은행"));
        assertTrue(prompt.contains("--- 기사 2 ---"));
    }

    @Test
    void shouldIncludeContentWhenPresent() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(9));
        Article articleWithContent = new Article(
                "article-with-content", "제목", "요약",
                "출처", ArticleSourceType.NEWS_MEDIA,
                now, now, "https://example.com/test",
                List.of(NewsCategory.OTHER), "ko", "본문 내용입니다."
        );
        AudienceProfile audience = new AudienceProfile(
                "beginner", List.of(NewsCategory.OTHER), List.of("테스트"));

        String prompt = AnalysisPromptBuilder.build(
                List.of(articleWithContent), LocalDate.of(2025, 1, 15), 5, audience);

        assertTrue(prompt.contains("본문:\n본문 내용입니다."));
    }

    @Test
    void shouldIncludeInstructionSection() {
        List<Article> articles = createArticles();
        AudienceProfile audience = new AudienceProfile(
                "beginner", List.of(NewsCategory.INTEREST_RATE), List.of("테스트"));

        String prompt = AnalysisPromptBuilder.build(
                articles, LocalDate.of(2025, 1, 15), 5, audience);

        assertTrue(prompt.contains("## 요청사항"));
        assertTrue(prompt.contains("같은 사건에 대한 기사는 반드시 하나로 그룹화"));
        assertTrue(prompt.contains("JSON 형식으로 응답"));
    }

    private List<Article> createArticles() {
        OffsetDateTime publishedAt = LocalDate.of(2025, 1, 15)
                .atTime(10, 0).atOffset(ZoneOffset.ofHours(9));
        OffsetDateTime collectedAt = LocalDate.of(2025, 1, 15)
                .atTime(23, 0).atOffset(ZoneOffset.ofHours(9));

        return List.of(
                new Article("article-1", "기준금리 인하", "기준금리가 인하되었습니다.",
                        "한국은행", ArticleSourceType.GOVERNMENT,
                        publishedAt, collectedAt,
                        "https://example.com/1",
                        List.of(NewsCategory.INTEREST_RATE), "ko", null),
                new Article("article-2", "전세 시장 동향", "전세 시장이 변화하고 있습니다.",
                        "연합뉴스", ArticleSourceType.NEWS_MEDIA,
                        publishedAt, collectedAt,
                        "https://example.com/2",
                        List.of(NewsCategory.JEONSE_MONTHLY_RENT), "ko", null)
        );
    }
}

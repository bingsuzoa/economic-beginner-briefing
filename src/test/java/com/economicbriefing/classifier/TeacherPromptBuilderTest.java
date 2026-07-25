package com.economicbriefing.classifier;

import java.time.OffsetDateTime;
import java.util.List;

import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TeacherPromptBuilderTest {

    @Test
    void systemPromptShouldContainClassificationCriteria() {
        assertNotNull(TeacherPromptBuilder.SYSTEM_PROMPT);
        assertTrue(TeacherPromptBuilder.SYSTEM_PROMPT.contains("RELEVANT"));
        assertTrue(TeacherPromptBuilder.SYSTEM_PROMPT.contains("IRRELEVANT"));
        assertTrue(TeacherPromptBuilder.SYSTEM_PROMPT.contains("UNCERTAIN"));
        assertTrue(TeacherPromptBuilder.SYSTEM_PROMPT.contains("severity"));
    }

    @Test
    void userPromptShouldIncludeTitleAndBody() {
        Article article = new Article("a1", "금리 인상 발표", "요약입니다",
                "한국경제", ArticleSourceType.NEWS_MEDIA,
                OffsetDateTime.now(), OffsetDateTime.now(),
                "https://example.com/1", List.of(NewsCategory.INTEREST_RATE), "ko",
                "한국은행이 기준금리를 인상했습니다.");

        String prompt = TeacherPromptBuilder.buildUserPrompt(article);

        assertTrue(prompt.contains("금리 인상 발표"));
        assertTrue(prompt.contains("한국은행이 기준금리를 인상했습니다."));
        assertTrue(prompt.contains("한국경제"));
    }

    @Test
    void userPromptShouldTruncateLongBody() {
        String longBody = "A".repeat(10000);
        Article article = new Article("a2", "제목", null,
                "출처", ArticleSourceType.NEWS_MEDIA,
                null, OffsetDateTime.now(),
                "https://example.com/2", List.of(), "ko", longBody);

        String prompt = TeacherPromptBuilder.buildUserPrompt(article);

        assertTrue(prompt.contains("truncated"));
        assertTrue(prompt.length() < longBody.length());
    }

    @Test
    void userPromptShouldHandleNullContentGracefully() {
        Article article = new Article("a3", "제목만 있음", null,
                "출처", ArticleSourceType.NEWS_MEDIA,
                null, OffsetDateTime.now(),
                "https://example.com/3", List.of(), "ko", null);

        String prompt = TeacherPromptBuilder.buildUserPrompt(article);

        assertTrue(prompt.contains("제목만 있음"));
        assertFalse(prompt.contains("본문"));
    }
}

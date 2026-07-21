package com.economicbriefing.collector.filter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QualityValidatorTest {

    private final QualityValidator validator = new QualityValidator();
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @Test
    void shouldAcceptValidArticle() {
        Article valid = createArticle("유효한 제목입니다", "https://example.com/article");
        QualityValidator.QualityValidationResult result = validator.validate(List.of(valid));
        assertEquals(1, result.valid().size());
    }

    @Test
    void shouldRejectShortTitle() {
        Article shortTitle = createArticle("짧다", "https://example.com/article");
        QualityValidator.QualityValidationResult result = validator.validate(List.of(shortTitle));
        assertEquals(1, result.invalid().size());
    }

    @Test
    void shouldRejectInvalidUrl() {
        Article badUrl = createArticle("유효한 제목입니다", "not-a-url");
        QualityValidator.QualityValidationResult result = validator.validate(List.of(badUrl));
        assertEquals(1, result.invalid().size());
    }

    @Test
    void shouldRejectAdContent() {
        Article ad1 = createArticle("[AD] 광고 기사입니다", "https://example.com/ad");
        Article ad2 = createArticle("[광고] 이벤트 안내", "https://example.com/ad2");
        QualityValidator.QualityValidationResult result = validator.validate(List.of(ad1, ad2));
        assertEquals(0, result.valid().size());
        assertEquals(2, result.invalid().size());
    }

    private Article createArticle(String title, String url) {
        return new Article("test-id", title, "요약", "테스트소스",
                ArticleSourceType.NEWS_MEDIA,
                OffsetDateTime.of(2026, 7, 20, 12, 0, 0, 0, KST),
                OffsetDateTime.now(KST),
                url, List.of(NewsCategory.OTHER), "ko", null);
    }
}

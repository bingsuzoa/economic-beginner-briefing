package com.economicbriefing.collector.mock;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.economicbriefing.collector.NewsCollector;
import com.economicbriefing.collector.dto.CollectNewsRequest;
import com.economicbriefing.collector.dto.CollectNewsResult;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;
import com.economicbriefing.domain.article.SourceCollectionReport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "briefing.dry-run", havingValue = "true", matchIfMissing = true)
public class MockNewsCollector implements NewsCollector {

    @Override
    public CollectNewsResult collect(CollectNewsRequest request) {
        List<Article> articles = createMockArticles(request.targetDate());

        return new CollectNewsResult(
                request.targetDate(),
                articles,
                List.of(
                        new SourceCollectionReport("한국은행", SourceCollectionReport.CollectionStatus.SUCCESS,
                                1, 1, null, null, null, null, null, null, null),
                        new SourceCollectionReport("금융위원회", SourceCollectionReport.CollectionStatus.SUCCESS,
                                1, 1, null, null, null, null, null, null, null),
                        new SourceCollectionReport("연합뉴스", SourceCollectionReport.CollectionStatus.SUCCESS,
                                1, 1, null, null, null, null, null, null, null)
                ),
                3,
                3,
                0
        );
    }

    private List<Article> createMockArticles(LocalDate targetDate) {
        OffsetDateTime publishedAt = targetDate.atTime(10, 0).atOffset(ZoneOffset.ofHours(9));
        OffsetDateTime collectedAt = targetDate.atTime(23, 30).atOffset(ZoneOffset.ofHours(9));

        return List.of(
                new Article(
                        "mock-article-001",
                        "한국은행, 기준금리 0.25%p 인하 결정",
                        "한국은행 금융통화위원회가 기준금리를 연 3.25%에서 3.00%로 0.25%포인트 인하했다.",
                        "한국은행",
                        ArticleSourceType.GOVERNMENT,
                        publishedAt,
                        collectedAt,
                        "https://example.com/articles/base-rate-cut",
                        List.of(NewsCategory.INTEREST_RATE),
                        "ko",
                        null
                ),
                new Article(
                        "mock-article-002",
                        "전세보증금 별도관리 제도 검토 착수",
                        "금융위원회가 전세보증금을 집주인이 직접 보유하지 않고 별도 기관에서 관리하는 방안을 검토하기로 했다.",
                        "금융위원회",
                        ArticleSourceType.GOVERNMENT,
                        publishedAt,
                        collectedAt,
                        "https://example.com/articles/jeonse-deposit-management",
                        List.of(NewsCategory.JEONSE_MONTHLY_RENT, NewsCategory.HOUSING),
                        "ko",
                        null
                ),
                new Article(
                        "mock-article-003",
                        "주요 은행, 정기예금 금리 일제히 인하",
                        "기준금리 인하 영향으로 KB국민, 신한, 우리 등 주요 시중은행이 정기예금 금리를 0.1~0.2%p 내렸다.",
                        "연합뉴스",
                        ArticleSourceType.NEWS_MEDIA,
                        publishedAt,
                        collectedAt,
                        "https://example.com/articles/deposit-rate-drop",
                        List.of(NewsCategory.DEPOSIT_SAVING, NewsCategory.INTEREST_RATE),
                        "ko",
                        null
                )
        );
    }
}

package com.economicbriefing.analyzer.openai;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;

import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArticleBodyFetcherTest {

    @Test
    void extractsYonhapArticleBodyAndKeepsShortNormalArticle() {
        var extracted = ArticleBodyFetcher.extractYonhap(yonhapHtml());

        assertEquals(6, extracted.paragraphs());
        assertTrue(extracted.body().contains("정비사업 조합 설립 소요 기간을 기존 1년에서 4개월로 단축한다."));
        assertTrue(extracted.body().contains("표준 처리 기한이 365일에서 120일 수준으로 245일 앞당겨진다."));
        assertFalse(extracted.body().contains("txt-copyright"));
        assertFalse(extracted.body().contains("dkkim@yna.co.kr"));
        assertTrue(extracted.body().length() < 1_000);
    }

    @Test
    void enrichesSupportedYonhapArticleAndRemovesBoilerplate() {
        Article original = article("https://www.yna.co.kr/view/AKR20260818148000004", "RSS 요약", null);
        ArticleBodyFetcher fetcher = new ArticleBodyFetcher(null, null) {
            @Override protected String fetchHtml(URI uri) {
                return yonhapHtml();
            }
        };

        Article enriched = fetcher.enrich(original);

        assertNotSame(original, enriched);
        assertTrue(enriched.content().contains("서울시가 주택 공급 속도를 높이기 위해"));
        assertFalse(enriched.content().contains("RSS 피드"));
        assertFalse(enriched.content().contains("dkkim@yna.co.kr"));
    }

    @Test
    void fallsBackWhenHttpFetchFails() {
        Article original = article("https://www.yna.co.kr/view/AKR20260818148000004", "RSS 요약", "기존 본문");
        ArticleBodyFetcher fetcher = new ArticleBodyFetcher(null, null) {
            @Override protected String fetchHtml(URI uri) {
                throw new IllegalStateException("boom");
            }
        };

        assertSame(original, fetcher.enrich(original));
    }

    @Test
    void fallsBackForUnsupportedSourceWithoutFetching() {
        Article original = article("https://example.com/news/1", "RSS 요약", "기존 본문");
        ArticleBodyFetcher fetcher = new ArticleBodyFetcher(null, null) {
            @Override protected String fetchHtml(URI uri) {
                fail("unsupported source must not be fetched");
                return "";
            }
        };

        assertSame(original, fetcher.enrich(original));
    }

    @Test
    void fallsBackWhenExtractedBodyIsLowQuality() {
        Article original = article("https://www.yna.co.kr/view/AKR20260818148000004", "RSS 요약", "기존 본문");
        ArticleBodyFetcher fetcher = new ArticleBodyFetcher(null, null) {
            @Override protected String fetchHtml(URI uri) {
                return """
                        <div class="story-news article">
                        <p>RSS 피드는 개인 리더 이용 목적으로 허용 되어 있습니다.</p>
                        <p>news@example.com</p>
                        <p class="txt-copyright adrs">copyright</p>
                        </div>
                        """;
            }
        };

        assertSame(original, fetcher.enrich(original));
    }

    @Test
    void enrichesOnlyArticlesPassedByCaller() {
        Article selected = article("https://www.yna.co.kr/view/AKR20260818148000004", "RSS 요약", null);
        Article unselected = article("https://www.yna.co.kr/view/AKR20260818141151008", "RSS 요약", null);
        class CountingFetcher extends ArticleBodyFetcher {
            int calls;
            CountingFetcher() { super(null, null); }
            @Override protected String fetchHtml(URI uri) {
                calls++;
                return yonhapHtml();
            }
        }
        CountingFetcher fetcher = new CountingFetcher();

        List<Article> enriched = fetcher.enrich(List.of(selected));

        assertEquals(1, fetcher.calls);
        assertEquals(1, enriched.size());
        assertEquals(selected.id(), enriched.get(0).id());
        assertNotEquals(unselected.id(), enriched.get(0).id());
    }

    private static Article article(String url, String summary, String content) {
        return new Article(
                "article-" + Math.abs(url.hashCode()), "제목", summary, "연합뉴스",
                ArticleSourceType.NEWS_MEDIA, OffsetDateTime.now(), OffsetDateTime.now(),
                url, List.of(NewsCategory.HOUSING), "ko", content);
    }

    private static String yonhapHtml() {
        return """
                <div class="story-news article">
                <div class="writer-zone01">기자 프로필</div>
                <p> (서울=연합뉴스) 김동규 기자 = 서울시가 주택 공급 속도를 높이기 위해 정비사업 조합 설립 소요 기간을 기존 1년에서 4개월로 단축한다.</p>
                <p> 서울시는 이를 위해 지난 18일 '조합설립 공정 촉진 절차 개선방안'을 시행한다고 19일 밝혔다.</p>
                <aside>광고</aside><p> 시는 최근 법률 개정과 규제철폐안 시행으로 정비구역 지정 전 추진위원회 조기 구성이 가능해짐에 따라 빠른 사업 추진을 위해 개선방안을 마련했다고 설명했다.</p>
                <p> 개선안은 조합 설립을 위한 동의 요건(75% 이상)을 추진위 구성 단계에서 충족한 정비구역이라면 구역 지정 전 정관 작성과 임원 선정 등 조합 설립 업무를 병행 처리할 수 있도록 했다.</p>
                <p> 또한 구역 지정 이후 60일 이상 걸리던 추정 분담금 통지와 이의신청 기간을 주민 공람과 동일한 30일로 줄이고, 창립총회와 병행해 진행할 수 있도록 제도를 개선했다.</p>
                <p> 이에 따라 구역 지정 후 조합 설립까지 걸리는 표준 처리 기한이 365일에서 120일 수준으로 245일 앞당겨진다.</p>
                <p> dkkim@yna.co.kr<br/></p>
                <p class="txt-copyright adrs">저작권자(c) 연합뉴스, 무단 전재-재배포 금지</p>
                </div>
                """;
    }
}

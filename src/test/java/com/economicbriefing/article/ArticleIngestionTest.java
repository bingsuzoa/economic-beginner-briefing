package com.economicbriefing.article;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.List;
import java.util.Date;
import java.time.Instant;
import com.economicbriefing.collector.parser.RssItem;
import com.economicbriefing.collector.parser.RssParser;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;
import org.junit.jupiter.api.Test;

class ArticleIngestionTest {
    @Test
    void recoversRetainedArticlesAfterAnOutageWithoutCollectingOlderDaysOrFeedDuplicates() {
        var rss = mock(RssParser.class);
        var repository = mock(ArticleRepository.class);
        var items = new java.util.ArrayList<RssItem>();
        for (int hours : List.of(3, 25, 27)) {
            String url = "https://www.yna.co.kr/view/AKR20260913%04d00009".formatted(hours);
            items.add(new RssItem("국가 간 교역 변화 " + hours, url, "",
                    Date.from(Instant.now().minusSeconds(hours * 3600L)), "관세와 시장 접근", null, url));
        }
        when(rss.parse(anyString())).thenReturn(items);
        assertEquals(2, new YonhapArticleService(rss, repository).collectRecent());
        var saved = ArgumentCaptor.forClass(ArticleEntity.class);
        verify(repository, times(2)).save(saved.capture());
        assertEquals(List.of("YONHAP:AKR202609130025", "YONHAP:AKR202609130003"),
                saved.getAllValues().stream().map(ArticleEntity::getId).toList());
    }

    @Test
    void keepsStableParagraphIdsAndRejectsCaptionsAsEvidence() {
        ParagraphSplitter splitter = new ParagraphSplitter();
        Map<String, String> paragraphs = splitter.split("첫 문단입니다.\n[그래픽] 설명\n둘째 문단입니다.");

        assertEquals("첫 문단입니다.", paragraphs.get("P001"));
        assertEquals("둘째 문단입니다.", paragraphs.get("P003"));
        assertTrue(!splitter.usableEvidence(paragraphs.get("P002")));
    }

    @Test
    void extractsOnlyYonhapStoryParagraphs() {
        String html = "<div class=\"story-news article\"><p>첫 번째 본문은 충분히 길게 작성되어 있습니다. 경제 상황의 변화를 설명하는 문장입니다.</p>"
                + "<p>두 번째 본문도 충분히 길게 작성되어 있습니다. 시장의 반응과 전달 경로를 다룹니다.</p>"
                + "<p>세 번째 본문은 전체 길이 검증을 통과하도록 사실을 조금 더 자세하게 서술합니다. 경제 주체의 선택과 제약도 함께 설명합니다. "
                + "기업의 투자와 가계의 소비가 물가와 금리에 어떤 압력을 줄 수 있는지 관측된 범위 안에서 다룹니다. 이후 확인할 지표도 구분합니다.</p>"
                + "<p>마지막 문단은 기사에 실제로 적힌 변화와 전망을 구분하고, 관측된 수치가 어떤 기간을 가리키는지 독자가 확인할 수 있도록 설명합니다.</p>"
                + "<p class=\"txt-copyright\">copyright</p></div>";

        String body = YonhapBodyFetcher.extract(html);
        assertTrue(body.startsWith("첫 번째 본문"));
        assertTrue(!body.contains("copyright"));
        assertThrows(IllegalArgumentException.class, () -> YonhapBodyFetcher.extract("<html/>"));
    }

    @Test
    void usesYonhapStoryKeyWhenPresent() {
        assertEquals("AKR202609101234", YonhapArticleService.storyKey(
                "https://www.yna.co.kr/view/AKR20260910123400001", null));
    }
}

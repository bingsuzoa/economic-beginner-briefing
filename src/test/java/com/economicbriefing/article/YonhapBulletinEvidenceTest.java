package com.economicbriefing.article;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class YonhapBulletinEvidenceTest {
    private String page(String title, String modified) {
        return "<script>{\"datePublished\":\"2026-09-17T03:01:02+09:00\",\"dateModified\":\""
                + modified + "\"}</script><h1 class=\"tit01\">" + title
                + "</h1><div class=\"story-news article\"><p>[자료사진]</p><p class=\"txt-copyright\">";
    }

    @Test void confirmedHeadlineSurvivesWithoutInventingBodyOrUsingCaption() {
        String headline = "[1보] 중앙은행, 기준금리 0.25%p 인상";
        String body = YonhapBodyFetcher.extractBefore(page(headline, "2026-09-17T03:01:02+09:00"),
                OffsetDateTime.parse("2026-09-17T05:00:00+09:00"));
        assertEquals("속보 제목: " + headline, body);
        assertTrue(new ParagraphSplitter().usableEvidence(body));
    }

    @Test void ordinaryMissingBodyAndPostCutoffBulletinStillFail() {
        var cutoff = OffsetDateTime.parse("2026-09-17T05:00:00+09:00");
        assertThrows(IllegalArgumentException.class, () -> YonhapBodyFetcher.extractBefore(
                page("금리 결정 해설", "2026-09-17T03:01:02+09:00"), cutoff));
        assertThrows(IllegalArgumentException.class, () -> YonhapBodyFetcher.extractBefore(
                page("[속보] 중앙은행 기준금리 결정", "2026-09-17T05:00:00+09:00"), cutoff));
    }
}

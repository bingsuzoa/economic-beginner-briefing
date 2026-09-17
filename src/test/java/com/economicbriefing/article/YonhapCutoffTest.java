package com.economicbriefing.article;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class YonhapCutoffTest {
    @Test void acceptsOnlyPageVersionsBeforeExclusiveCutoff() {
        var cutoff = OffsetDateTime.parse("2025-04-03T05:00:00+09:00");
        String body = "<div class=\"story-news article\"><p>"+"원문 근거 문장입니다. ".repeat(20)+"</p><p>두 번째 문단입니다.</p><p class=\"txt-copyright";
        String published = "{\"datePublished\":\"2025-04-02T10:00:00+09:00\",\"dateModified\":\"%s\"}";
        assertTrue(YonhapBodyFetcher.extractBefore(published.formatted("2025-04-03T04:59:59+09:00")+body,cutoff).contains("원문 근거"));
        assertThrows(IllegalArgumentException.class,()->YonhapBodyFetcher.extractBefore(published.formatted("2025-04-03T05:00:00+09:00")+body,cutoff));
        assertThrows(IllegalArgumentException.class,()->YonhapBodyFetcher.extractBefore(published.formatted("2025-04-03T10:00:00+09:00")+body,cutoff));
        assertThrows(IllegalArgumentException.class,()->YonhapBodyFetcher.extractBefore(body,cutoff));
    }
}

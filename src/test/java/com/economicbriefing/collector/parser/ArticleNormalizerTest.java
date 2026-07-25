package com.economicbriefing.collector.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArticleNormalizerTest {

    // Real title observed from the 머니투데이 feed, which reached the browser as-is.
    @Test
    void shouldDecodeNumericEntitiesInFeedTitles() {
        String raw = "&#34;집주인 대출 찬스 갑니다&#34; &#34;6억 가능&#34;...서울→경기→부산 번진다";

        assertEquals("\"집주인 대출 찬스 갑니다\" \"6억 가능\"...서울→경기→부산 번진다",
                ArticleNormalizer.decodeEntities(raw));
    }

    @Test
    void shouldDecodeNamedEntities() {
        assertEquals("<주가> & \"환율\"",
                ArticleNormalizer.decodeEntities("&lt;주가&gt; &amp; &quot;환율&quot;"));
    }

    @Test
    void shouldDecodeHexEntities() {
        assertEquals("\"금리\"", ArticleNormalizer.decodeEntities("&#x22;금리&#x22;"));
    }

    /** Feeds escape twice, so a single decode pass leaves "&#34;" visible. */
    @Test
    void shouldDecodeDoubleEscapedEntities() {
        assertEquals("\"코스피\"", ArticleNormalizer.decodeEntities("&amp;#34;코스피&amp;#34;"));
    }

    @Test
    void shouldLeaveOrdinaryTextUntouched() {
        assertEquals("기준금리 인상", ArticleNormalizer.decodeEntities("기준금리 인상"));
        assertNull(ArticleNormalizer.decodeEntities(null));
    }

    /** An unknown entity must survive rather than be silently dropped. */
    @Test
    void shouldKeepUnknownEntities() {
        assertEquals("A&unknown;B", ArticleNormalizer.decodeEntities("A&unknown;B"));
    }
}

package com.economicbriefing.analyzer.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManualEconomicFlowDebugManifestTest {
    @Test
    void preservesTheUsersManifestOrder() throws Exception {
        var manifest = new ObjectMapper().readValue("""
                {"articles":[
                  {"label":"first","articleId":"1","title":"one","publishedAt":"2026-08-26T00:00:00Z","body":"body"},
                  {"label":"second","url":"https://www.yna.co.kr/view/AKR_REPLACE_ME"}
                ]}
                """, ManualEconomicFlowDebugTest.Manifest.class);

        assertEquals(java.util.List.of("first", "second"),
                manifest.articles().stream().map(ManualEconomicFlowDebugTest.ManifestArticle::label).toList());
    }
}

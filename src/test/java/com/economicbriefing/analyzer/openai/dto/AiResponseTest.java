package com.economicbriefing.analyzer.openai.dto;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeValidAiResponse() throws Exception {
        String json = """
                {
                  "overallSummary": ["요약 1", "요약 2"],
                  "news": [
                    {
                      "id": "news-1",
                      "easyTitle": "기준금리가 내려갔어요",
                      "category": "interest_rate",
                      "importance": 5,
                      "threeLineSummary": ["핵심 1", "핵심 2", "핵심 3"],
                      "whatHappened": "기준금리가 인하되었습니다",
                      "whyItHappened": "경기 둔화 우려",
                      "economicImpact": "시중금리 하락",
                      "householdImpact": "대출 이자 감소",
                      "affectedPeople": ["변동금리 대출자"],
                      "positiveImpact": "대출 이자 부담 감소",
                      "negativeImpact": "예금 이자 수입 감소",
                      "actionItems": ["변동금리 대출 이자 확인"],
                      "terms": [
                        {"term": "기준금리", "explanation": "설명"}
                      ],
                      "evidenceStatus": "confirmed",
                      "uncertainties": [],
                      "sources": [
                        {"articleId": "article-1", "isPrimary": true}
                      ]
                    }
                  ],
                  "glossary": [
                    {"term": "DSR", "explanation": "총부채원리금상환비율", "example": "예시"}
                  ]
                }
                """;

        AiResponse response = objectMapper.readValue(json, AiResponse.class);

        assertEquals(2, response.overallSummary().size());
        assertEquals(1, response.news().size());
        assertEquals("news-1", response.news().get(0).id());
        assertEquals("기준금리가 내려갔어요", response.news().get(0).easyTitle());
        assertEquals("interest_rate", response.news().get(0).category());
        assertEquals(5, response.news().get(0).importance());
        assertEquals(3, response.news().get(0).threeLineSummary().size());
        assertEquals(1, response.news().get(0).terms().size());
        assertEquals(1, response.glossary().size());
        assertEquals("DSR", response.glossary().get(0).term());
        assertEquals("예시", response.glossary().get(0).example());
    }

    @Test
    void shouldIgnoreUnknownFields() throws Exception {
        String json = """
                {
                  "overallSummary": ["요약"],
                  "news": [
                    {
                      "id": "news-1",
                      "easyTitle": "제목",
                      "category": "other",
                      "importance": 3,
                      "threeLineSummary": ["핵심"],
                      "whatHappened": "사건",
                      "whyItHappened": "원인",
                      "economicImpact": "영향",
                      "householdImpact": "생활영향",
                      "affectedPeople": [],
                      "positiveImpact": "긍정",
                      "negativeImpact": "부정",
                      "actionItems": [],
                      "terms": [],
                      "evidenceStatus": "expected",
                      "uncertainties": [],
                      "sources": [{"articleId": "a-1", "isPrimary": true}],
                      "unknownField": "value"
                    }
                  ],
                  "glossary": [],
                  "extraField": "ignored"
                }
                """;

        AiResponse response = objectMapper.readValue(json, AiResponse.class);

        assertNotNull(response);
        assertEquals(1, response.news().size());
    }

    @Test
    void shouldHandleOptionalFields() throws Exception {
        String json = """
                {
                  "overallSummary": ["요약"],
                  "news": [
                    {
                      "id": "news-1",
                      "easyTitle": "제목",
                      "category": "housing",
                      "importance": 4,
                      "threeLineSummary": ["핵심"],
                      "whatHappened": "사건",
                      "evidenceStatus": "proposed",
                      "sources": [{"articleId": "a-1", "isPrimary": true}]
                    }
                  ]
                }
                """;

        AiResponse response = objectMapper.readValue(json, AiResponse.class);

        assertNull(response.news().get(0).uncertainties());
        assertNull(response.news().get(0).terms());
        assertNull(response.glossary());
    }
}

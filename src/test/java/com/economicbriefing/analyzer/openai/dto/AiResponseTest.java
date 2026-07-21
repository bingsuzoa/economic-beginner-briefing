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
                      "representativeTitle": "기준금리 인하",
                      "category": "interest_rate",
                      "importance": 5,
                      "whyImportant": "대출 이자 변화",
                      "oneLineSummary": "기준금리가 인하되었습니다",
                      "explanation": "해설 내용",
                      "evidenceStatus": "confirmed",
                      "economicTerms": [
                        {"term": "기준금리", "explanation": "설명"}
                      ],
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
        assertEquals("interest_rate", response.news().get(0).category());
        assertEquals(5, response.news().get(0).importance());
        assertEquals(1, response.news().get(0).economicTerms().size());
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
                      "representativeTitle": "제목",
                      "category": "other",
                      "importance": 3,
                      "whyImportant": "이유",
                      "oneLineSummary": "결론",
                      "explanation": "해설",
                      "evidenceStatus": "expected",
                      "economicTerms": [],
                      "sources": [{"articleId": "a-1", "isPrimary": true}],
                      "targetAudience": {"mustRead": ["전세 세입자"], "notRelevant": []},
                      "impactAssessment": [{"target": "일반 가계", "score": 3, "reason": "이유"}],
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
                      "representativeTitle": "제목",
                      "category": "housing",
                      "importance": 4,
                      "whyImportant": "이유",
                      "oneLineSummary": "결론",
                      "explanation": "해설",
                      "evidenceStatus": "proposed",
                      "sources": [{"articleId": "a-1", "isPrimary": true}]
                    }
                  ]
                }
                """;

        AiResponse response = objectMapper.readValue(json, AiResponse.class);

        assertNull(response.news().get(0).uncertaintyNote());
        assertNull(response.news().get(0).economicTerms());
        assertNull(response.glossary());
    }
}

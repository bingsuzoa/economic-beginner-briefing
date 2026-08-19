package com.economicbriefing.analyzer.openai;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import com.economicbriefing.analyzer.dto.ArticleValidationResult;
import com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArticleValidationIntegrityTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Article article = new Article(
            "article-1", "제목", "요약", "연합뉴스", ArticleSourceType.NEWS_MEDIA,
            OffsetDateTime.now(), OffsetDateTime.now(), "https://example.com",
            List.of(NewsCategory.LOAN), "ko", "금융당국은 금리가 오를 것으로 전망했다.");
    private final ArticleAnalysisResponse baseline = new ArticleAnalysisResponse(List.of(
            new ArticleAnalysisResponse.ArticleAnalysis("article-1", List.of(
                    new ArticleAnalysisResponse.Issue(
                            "금리", List.of("금리가 변동했다."), List.of(),
                            List.of(new ArticleAnalysisResponse.Relation(
                                    "금리", "대출", ArticleAnalysisResponse.RelationType.CAUSE_OR_RESULT,
                                    "금리가 대출에 영향을 준다.",
                                    ArticleAnalysisResponse.StatementType.FACT, null)),
                            List.of(), List.of("금리"))))));
    private final Set<ArticleValidationResult.FindingType> allowed = Set.of(
            ArticleValidationResult.FindingType.WRONG_TYPE);

    @Test
    void rejectsDuplicateJsonKeys() {
        String json = validFinding().replace(
                "\"type\":\"WRONG_TYPE\"",
                "\"type\":\"WRONG_TYPE\",\"type\":\"INACCURATE\"");
        assertThrows(Exception.class, () -> parse(json));
    }

    @Test
    void rejectsUnknownFields() {
        String json = validFinding().replace(
                "\"description\":\"분류 오류\"",
                "\"description\":\"분류 오류\",\"descriptions\":[]");
        assertThrows(Exception.class, () -> parse(json));
    }

    @Test
    void rejectsInvalidTargetReferenceAndCurrentValue() {
        assertThrows(Exception.class, () -> parse(validFinding().replace(
                "relations[0].evidenceType", "relations[9].evidenceType")));
        assertThrows(Exception.class, () -> parse(validFinding().replace(
                "\"currentValue\":\"FACT\"", "\"currentValue\":\"CLAIM\"")));
    }

    @Test
    void rejectsWrongTargetAndSuggestedEnumAxis() {
        assertThrows(Exception.class, () -> parse(validFinding().replace(
                "\"targetType\":\"RELATION\"", "\"targetType\":\"STATEMENT\"")));
        assertThrows(Exception.class, () -> parse(validFinding().replace(
                "\"suggestedValue\":\"INTERPRETATION\"", "\"suggestedValue\":\"PURPOSE\"")));
        assertThrows(Exception.class, () -> parse(validFinding().replace(
                "\"suggestedValue\":\"INTERPRETATION\"", "\"suggestedValue\":{}")));
    }

    @Test
    void rejectsInvalidFindingShapeAndRemovesExactDuplicates() throws Exception {
        assertThrows(Exception.class, () -> parse(validFinding().replace(
                "\"suggestedValue\":\"INTERPRETATION\"", "\"suggestedValue\":null")));

        String finding = validFindingObject();
        String duplicated = "{\"articles\":[{\"articleId\":\"article-1\",\"findings\":["
                + finding + "," + finding + "]}]}";
        assertEquals(1, parse(duplicated).articles().get(0).findings().size());
    }

    @Test
    void removesItemFindingWhenCurrentAndSuggestedAlreadyMatch() throws Exception {
        String json = validFinding().replace(
                "\"suggestedValue\":\"INTERPRETATION\"", "\"suggestedValue\":\"FACT\"");

        assertTrue(parse(json).articles().get(0).findings().isEmpty());
    }

    @Test
    void removesItemFindingWhenDescriptionAffirmsCurrentValue() throws Exception {
        String json = validFinding().replace(
                "\"description\":\"분류 오류\"",
                "\"description\":\"장기물 금리 상승과 금리차 확대의 관계는 기사에서 해석된 것이 아니라 사실로 제시되었다.\"");

        assertTrue(parse(json).articles().get(0).findings().isEmpty());
    }

    @Test
    void keepsItemFindingWhenDescriptionSupportsSuggestedValue() throws Exception {
        String json = validFinding().replace(
                "\"description\":\"분류 오류\"",
                "\"description\":\"해당 문장은 기사 해석이므로 FACT보다 INTERPRETATION이 적절하다.\"");

        assertEquals(1, parse(json).articles().get(0).findings().size());
    }

    private ArticleValidationResult parse(String json) throws Exception {
        return ArticleValidationIntegrity.parseAndValidate(
                mapper, json, List.of(article), baseline, allowed);
    }

    private String validFinding() {
        return "{\"articles\":[{\"articleId\":\"article-1\",\"findings\":["
                + validFindingObject() + "]}]}";
    }

    private String validFindingObject() {
        return "{\"type\":\"WRONG_TYPE\",\"issue\":\"금리\",\"targetType\":\"RELATION\","
                + "\"targetReference\":\"issues[0].relations[0].evidenceType\","
                + "\"description\":\"분류 오류\",\"currentValue\":\"FACT\","
                + "\"suggestedValue\":\"INTERPRETATION\",\"evidence\":\"원문\"}";
    }
}

package com.economicbriefing.analyzer.openai;

import java.time.OffsetDateTime;
import java.util.List;

import com.economicbriefing.analyzer.dto.ArticleValidationResult;
import com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse;
import com.economicbriefing.analyzer.openai.prompt.ArticleAnalyzerPromptBuilder;
import com.economicbriefing.analyzer.openai.prompt.ArticleValidatorPromptBuilder;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArticleAnalyzerTest {

    @Test
    void shouldPreserveArticleEvidenceInPromptAndResponse() throws Exception {
        Article article = new Article(
                "article-1", "대출 관리 목표 상향", "신규 대출여력이 늘어난다.", "연합뉴스",
                ArticleSourceType.NEWS_MEDIA, OffsetDateTime.now(), OffsetDateTime.now(),
                "https://example.com/1", List.of(NewsCategory.LOAN), "ko",
                "총량 증가율 목표를 1.5%에서 3.0%로 높여 신규 대출여력 30조원이 확보됐다.");

        String prompt = ArticleAnalyzerPromptBuilder.build(List.of(article));
        assertTrue(prompt.contains(article.content()));
        assertTrue(ArticleAnalyzerPromptBuilder.SYSTEM_PROMPT.contains("외부 지식"));
        assertTrue(ArticleAnalyzerPromptBuilder.SYSTEM_PROMPT.contains("articleExplanation"));
        assertTrue(ArticleAnalyzerPromptBuilder.SYSTEM_PROMPT.contains("명시된 before를 null로 만들지 마세요"));
        assertTrue(ArticleAnalyzerPromptBuilder.SYSTEM_PROMPT.contains("EXPECTED_PROCESS"));
        assertTrue(ArticleAnalyzerPromptBuilder.SYSTEM_PROMPT.contains("현재 실제로 검토"));
        assertTrue(ArticleAnalyzerPromptBuilder.SYSTEM_PROMPT.contains("대표 예시 목록이 아닙니다"));
        assertTrue(ArticleAnalyzerPromptBuilder.SYSTEM_PROMPT.contains("부정·차단 구조의 방향"));
        assertTrue(ArticleAnalyzerPromptBuilder.SYSTEM_PROMPT.contains("대상→조건→결과"));
        assertTrue(ArticleAnalyzerPromptBuilder.SYSTEM_PROMPT.contains("명시되지 않은 목적 관계로 강화하지 마세요"));
        assertTrue(ArticleAnalyzerPromptBuilder.SYSTEM_PROMPT.contains("예정된 절차가 아니므로 EXPECTED_PROCESS"));
        assertTrue(ArticleAnalyzerPromptBuilder.SYSTEM_PROMPT.contains("CLAIM이며 FACT로 승격하지 마세요"));
        assertTrue(ArticleAnalyzerPromptBuilder.SYSTEM_PROMPT.contains("INTERPRETATION입니다"));
        assertTrue(ArticleAnalyzerPromptBuilder.SYSTEM_PROMPT.contains("추출 순서(순서를 바꾸거나"));
        assertTrue(ArticleAnalyzerPromptBuilder.SYSTEM_PROMPT.contains("관계로 만들지 않더라도 문장을 버리지 마세요"));
        String validatorPrompt = ArticleValidatorPromptBuilder.build(List.of(article), "{\"articles\":[]}");
        assertTrue(validatorPrompt.contains("읽기 전용 Article Analyzer 결과"));
        assertTrue(validatorPrompt.contains("findings만 출력"));
        assertTrue(ArticleValidatorPromptBuilder.ITEM_VALIDATION_SYSTEM_PROMPT.contains("WRONG_SPEAKER"));
        assertTrue(ArticleValidatorPromptBuilder.ITEM_VALIDATION_SYSTEM_PROMPT.contains("MISSING을 찾거나 출력하지 마세요"));
        assertTrue(ArticleValidatorPromptBuilder.ITEM_VALIDATION_SYSTEM_PROMPT.contains("INACCURATE"));
        assertTrue(ArticleValidatorPromptBuilder.ITEM_VALIDATION_SYSTEM_PROMPT.contains("두 축을 바꾸어 제안하지 마세요"));
        assertTrue(ArticleValidatorPromptBuilder.ITEM_VALIDATION_SYSTEM_PROMPT.contains("정확히 보존했다면"));
        assertTrue(ArticleValidatorPromptBuilder.ITEM_VALIDATION_SYSTEM_PROMPT.contains("UNDER_CONSIDERATION 같은 새 값을 제안하지 마세요"));
        assertTrue(ArticleValidatorPromptBuilder.ITEM_VALIDATION_SYSTEM_PROMPT.contains("표현만 바꾸어 CLAIM으로 수정하지 마세요"));
        assertTrue(ArticleValidatorPromptBuilder.ITEM_VALIDATION_SYSTEM_PROMPT.contains("같은 집단이나 인물을 가리키면"));
        assertTrue(ArticleValidatorPromptBuilder.ITEM_VALIDATION_SYSTEM_PROMPT.contains("Finding Self-Consistency 검수"));
        assertTrue(ArticleValidatorPromptBuilder.MISSING_REVIEW_SYSTEM_PROMPT.contains("MISSING finding만 출력"));
        assertTrue(ArticleValidatorPromptBuilder.MISSING_REVIEW_SYSTEM_PROMPT.contains("절대 MISSING으로 보고하지 마세요"));
        assertTrue(ArticleValidatorPromptBuilder.MISSING_REVIEW_SYSTEM_PROMPT.contains("적용 대상→특례/예외→적용 결과"));
        assertTrue(ArticleValidatorPromptBuilder.MISSING_REVIEW_SYSTEM_PROMPT.contains("DETAIL OMISSION"));
        assertTrue(ArticleValidatorPromptBuilder.MISSING_REVIEW_SYSTEM_PROMPT.contains("필드 중복을 요구하지 마세요"));
        assertTrue(ArticleValidatorPromptBuilder.MISSING_REVIEW_SYSTEM_PROMPT.contains("개별 구성원을 하나씩 MISSING으로 열거하지 마세요"));

        String validationJson = """
                {"articles":[{"articleId":"article-1","findings":[{
                  "type":"MISSING","issue":"가계대출","targetType":"RELATION",
                  "targetReference":null,"description":"관계 누락","currentValue":null,
                  "suggestedValue":{"from":"A","to":"B"},"evidence":"A 때문에 B"
                }]}]}
                """;
        ArticleValidationResult validation = new ObjectMapper()
                .readValue(validationJson, ArticleValidationResult.class);
        assertEquals("A", validation.articles().get(0).findings().get(0)
                .suggestedValue().get("from").asText());
        assertEquals(ArticleValidationResult.FindingType.INACCURATE,
                ArticleValidationResult.FindingType.valueOf("INACCURATE"));

        String json = """
                {"articles":[{
                  "articleId":"article-1",
                  "issues":[{
                    "name":"가계대출",
                    "mainFacts":["총량 증가율 목표가 상향됐다."],
                    "changes":[{"target":"총량 증가율 목표","before":"1.5%","after":"3.0%","status":"CONFIRMED"}],
                    "relations":[{"from":"목표 상향","to":"대출여력 증가","relationType":"CAUSE_OR_RESULT","articleExplanation":"기사에 제시된 산식에 따른다.","evidenceType":"FACT","speaker":null}],
                    "statements":[{"type":"PREDICTION","speaker":"금융당국","content":"대출난이 누그러질 것으로 봤다."}],
                    "keyTerms":["가계부채 총량관리"]
                  }]
                }]}
                """;

        ArticleAnalysisResponse response = new ObjectMapper().readValue(json, ArticleAnalysisResponse.class);
        var issue = response.articles().get(0).issues().get(0);
        assertEquals("기사에 제시된 산식에 따른다.", issue.relations().get(0).articleExplanation());
        assertEquals(ArticleAnalysisResponse.StatementType.FACT, issue.relations().get(0).evidenceType());
        assertEquals("금융당국", issue.statements().get(0).speaker());
        assertEquals(ArticleAnalysisResponse.StatementType.PREDICTION,
                issue.statements().get(0).type());
        assertEquals(List.of("가계부채 총량관리"), issue.keyTerms());

        var duplicate = new ArticleValidationResult.Finding(
                ArticleValidationResult.FindingType.MISSING, "가계대출",
                ArticleValidationResult.TargetType.MAIN_FACT, null,
                "총량 증가율 목표가 상향됐다.", null, null, "원문");
        var emptyValidation = new ArticleValidationResult(List.of(
                new ArticleValidationResult.ArticleValidation("article-1", List.of())));
        var duplicateValidation = new ArticleValidationResult(List.of(
                new ArticleValidationResult.ArticleValidation("article-1", List.of(duplicate))));
        assertTrue(ArticleValidationMerger.merge(List.of(article), response, emptyValidation, duplicateValidation)
                .articles().get(0).findings().isEmpty());

        var nodes = new ObjectMapper().getNodeFactory();
        var unidentifiedUnsupported = new ArticleValidationResult.Finding(
                ArticleValidationResult.FindingType.UNSUPPORTED, "가계대출",
                ArticleValidationResult.TargetType.CHANGE, null,
                "근거 없음", null, null, null);
        var mixedAxes = new ArticleValidationResult.Finding(
                ArticleValidationResult.FindingType.INACCURATE, "가계대출",
                ArticleValidationResult.TargetType.RELATION, null,
                "축 혼동", nodes.textNode("CLAIMED_EFFECT"), nodes.textNode("CLAIM"), "원문");
        var sourceSupported = new ArticleValidationResult.Finding(
                ArticleValidationResult.FindingType.UNSUPPORTED, "가계대출",
                ArticleValidationResult.TargetType.MAIN_FACT, "issues[0].mainFacts[0]",
                "원문 근거 없음", nodes.textNode(article.content()), null, null);
        var validType = new ArticleValidationResult.Finding(
                ArticleValidationResult.FindingType.WRONG_TYPE, "가계대출",
                ArticleValidationResult.TargetType.RELATION, "issues[0].relations[0].evidenceType",
                "근거 성격 오류", nodes.textNode("FACT"), nodes.textNode("INTERPRETATION"), "원문");
        var itemValidation = new ArticleValidationResult(List.of(
                new ArticleValidationResult.ArticleValidation(
                        "article-1", List.of(unidentifiedUnsupported, mixedAxes, sourceSupported, validType))));
        var filtered = ArticleValidationMerger.merge(List.of(article), response, itemValidation, emptyValidation)
                .articles().get(0).findings();
        assertEquals(List.of(validType), filtered);

        var marketIssue = new ArticleAnalysisResponse.Issue(
                "시장 금리", List.of("대표 A +1.0bp", "대표 B +2.0bp", "대표 C +3.0bp"),
                List.of(), List.of(), List.of(), List.of());
        var marketResponse = new ArticleAnalysisResponse(List.of(
                new ArticleAnalysisResponse.ArticleAnalysis("article-1", List.of(marketIssue))));
        var repeatedDetail = new ArticleValidationResult.Finding(
                ArticleValidationResult.FindingType.MISSING, "시장 금리",
                ArticleValidationResult.TargetType.MAIN_FACT, null,
                "추가 지표 누락", null, null, "추가 지표 +1.5bp");
        var structuralNumber = new ArticleValidationResult.Finding(
                ArticleValidationResult.FindingType.MISSING, "시장 금리",
                ArticleValidationResult.TargetType.MAIN_FACT, null,
                "적용 기준 누락", null, null, "적용 기준은 +1.5bp 이상");
        var seriesValidation = new ArticleValidationResult(List.of(
                new ArticleValidationResult.ArticleValidation(
                        "article-1", List.of(repeatedDetail, structuralNumber))));
        assertEquals(List.of(structuralNumber), ArticleValidationMerger.merge(
                List.of(article), marketResponse, emptyValidation, seriesValidation)
                .articles().get(0).findings());

        var unchangedType = new ArticleValidationResult.Finding(
                ArticleValidationResult.FindingType.WRONG_TYPE, "가계대출",
                ArticleValidationResult.TargetType.RELATION, "issues[0].relations[0].evidenceType",
                "FACT가 적절하다", nodes.textNode("FACT"), nodes.textNode("FACT"), "원문");
        var wrongTargetAxis = new ArticleValidationResult.Finding(
                ArticleValidationResult.FindingType.WRONG_TYPE, "가계대출",
                ArticleValidationResult.TargetType.RELATION, "issues[0].relations[0].evidenceType",
                "관계 유형 변경", nodes.textNode("FACT"), nodes.textNode("PURPOSE"), "원문");
        var inconsistentValidation = new ArticleValidationResult(List.of(
                new ArticleValidationResult.ArticleValidation(
                        "article-1", List.of(unchangedType, wrongTargetAxis))));
        assertTrue(ArticleValidationMerger.merge(
                List.of(article), response, inconsistentValidation, emptyValidation)
                .articles().get(0).findings().isEmpty());

        var descriptionContradictsSuggestion = new ArticleValidationResult.Finding(
                ArticleValidationResult.FindingType.WRONG_TYPE, "가계대출",
                ArticleValidationResult.TargetType.RELATION, "issues[0].relations[0].evidenceType",
                "기사에서는 '~로 풀이된다'고 하여 INTERPRETATION임을 명시했으나 CLAIM으로 잘못 설정됨.",
                nodes.textNode("INTERPRETATION"), nodes.textNode("CLAIM"), "원문");
        var contradictoryValidation = new ArticleValidationResult(List.of(
                new ArticleValidationResult.ArticleValidation(
                        "article-1", List.of(descriptionContradictsSuggestion))));
        assertTrue(ArticleValidationMerger.merge(
                List.of(article), response, contradictoryValidation, emptyValidation)
                .articles().get(0).findings().isEmpty());
    }
}

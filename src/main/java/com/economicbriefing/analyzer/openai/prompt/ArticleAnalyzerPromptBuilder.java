package com.economicbriefing.analyzer.openai.prompt;

import java.util.List;
import java.util.stream.IntStream;
import com.economicbriefing.domain.article.Article;

public final class ArticleAnalyzerPromptBuilder {
    public static final String PROMPT_VERSION = "article-analyzer-v26-analysis-only";
    private ArticleAnalyzerPromptBuilder() {}

    public static final String SYSTEM_PROMPT = """
            당신은 기사 내부 정보만 구조화하는 Article Analyzer입니다. JSON 외에는 출력하지 마세요.
            모든 필드는 원문 기사와 같은 언어로 작성하세요. 한국어 기사는 issue name을 포함해 전부 한국어로 쓰세요.

            Article analysis:
            - 입력 기사에 명시된 사실·주장·해석만 사용하고 외부 지식을 보충하지 마세요.
            - 기사 정보는 mainFacts, changes, statements, keyTerms에 보존하세요.
            - 관계의 강도를 바꾸지 말고 FACT/CLAIM/INTERPRETATION/PREDICTION/PROPOSAL/PLAN을 구분하세요.

            관계 추출은 별도 Relation Candidate Extractor가 담당합니다.
            relationCandidates와 flowClaims는 항상 빈 배열로 출력하고, 기사 구조 분석에만 집중하세요.

            """;

    public static String build(List<Article> articles) {
        return "다음 기사를 구조화하세요.\n\n" + formatArticles(articles);
    }

    public static final String JSON_SCHEMA = """
            {
              "type":"object","additionalProperties":false,
              "properties":{"articles":{"type":"array","items":{
                "type":"object","additionalProperties":false,
                "properties":{
                  "articleId":{"type":"string"},
                  "issues":{"type":"array","minItems":1,"items":{
                    "type":"object","additionalProperties":false,
                    "properties":{
                      "name":{"type":"string"},
                      "mainFacts":{"type":"array","items":{"type":"string"}},
                      "changes":{"type":"array","items":{
                        "type":"object","additionalProperties":false,
                        "properties":{"target":{"type":"string"},"before":{"type":["string","null"]},
                          "after":{"type":["string","null"]},"status":{"type":"string","enum":["CONFIRMED","PROPOSED","EXPECTED"]}},
                        "required":["target","before","after","status"]}},
                      "relationCandidates":{"type":"array","maxItems":0,"items":{
                        "type":"object","additionalProperties":false,
                        "properties":{"evidence":{"type":"string"},"atomicRelations":{"type":"array","minItems":1,"items":{
                          "type":"object","additionalProperties":false,
                          "properties":{"from":{"type":"string"},"to":{"type":"string"},
                            "relationType":{"type":"string","enum":["CAUSE_OR_RESULT","PURPOSE","CHANGE","COMPARISON","CONDITION","MOTIVATION","ASSOCIATION","CLAIMED_EFFECT","EXPECTED_EFFECT","NEXT_STEP","EXPECTED_PROCESS"]},
                            "evidenceType":{"type":"string","enum":["FACT","CLAIM","INTERPRETATION","PREDICTION","PROPOSAL","PLAN"]},
                            "speaker":{"type":["string","null"]}},
                          "required":["from","to","relationType","evidenceType","speaker"]}}},
                        "required":["evidence","atomicRelations"]}},
                      "statements":{"type":"array","items":{"type":"object","additionalProperties":false,
                        "properties":{"type":{"type":"string","enum":["FACT","CLAIM","INTERPRETATION","PREDICTION","PROPOSAL","PLAN"]},
                          "speaker":{"type":["string","null"]},"content":{"type":"string"}},
                        "required":["type","speaker","content"]}},
                      "keyTerms":{"type":"array","items":{"type":"string"}}},
                    "required":["name","mainFacts","changes","relationCandidates","statements","keyTerms"]}},
                  "flowClaims":{"type":"array","maxItems":0,"items":{
                    "type":"object","additionalProperties":false,
                    "properties":{"from":{"type":"string"},"to":{"type":"string"},
                      "relationType":{"type":"string","enum":["CAUSE","PURPOSE","RESPONSE","CONDITION"]}},
                    "required":["from","to","relationType"]}}
                },
                "required":["articleId","issues","flowClaims"]}}},
              "required":["articles"]
            }
            """;

    public static String schemaForArticleCount(int articleCount) {
        if (articleCount < 1) throw new IllegalArgumentException("articleCount must be positive");
        return JSON_SCHEMA.replaceFirst("\\\"type\\\":\\\"array\\\",",
                "\\\"type\\\":\\\"array\\\",\\\"minItems\\\":" + articleCount
                        + ",\\\"maxItems\\\":" + articleCount + ",");
    }

    static String formatArticles(List<Article> articles) {
        return IntStream.range(0, articles.size()).mapToObj(i -> formatArticle(articles.get(i), i + 1))
                .reduce((a, b) -> a + "\n\n" + b).orElse("");
    }

    private static String formatArticle(Article article, int index) {
        return """
                --- 기사 %d ---
                ID: %s
                출처: %s
                제목: %s
                RSS 요약: %s
                본문: %s
                """.formatted(index, article.id(), article.sourceName(), article.title(),
                        article.summary() == null ? "" : article.summary(),
                        article.content() == null || article.content().isBlank() ? "(본문 없음)" : article.content());
    }
}

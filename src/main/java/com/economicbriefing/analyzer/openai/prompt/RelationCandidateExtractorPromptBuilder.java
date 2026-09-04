package com.economicbriefing.analyzer.openai.prompt;

import java.util.List;
import java.util.stream.IntStream;

import com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse;
import com.economicbriefing.domain.article.Article;

public final class RelationCandidateExtractorPromptBuilder {
    public static final String PROMPT_VERSION = "relation-candidate-extractor-v5";

    private RelationCandidateExtractorPromptBuilder() {}

    public static final String SYSTEM_PROMPT = """
            당신은 Relation Candidate Extractor입니다. JSON 외에는 출력하지 마세요.
            기사 원문을 처음부터 끝까지 직접 읽고, 원문이 직접 설명하는 방향성 있는 경제 관계 후보만 추출하세요.
            기사 요약, 중요도·DB 저장 가치·초보자 설명 필요 여부 판단, 외부 지식 보완은 하지 마세요.

            - 원인, 조건, 목적, 대응, 경제적 영향·충격 전달 관계를 빠짐없이 후보로 보존하세요.
            - 자원·안보·전략적 위치 등 어떤 조건이 정책·투자·외교·시장 행동의 명시된 동기 또는 배경으로 설명되면,
              그 조건→행동을 CONDITION 후보로 보존하세요. 조건과 행동이 단지 같은 문장에 함께 있어도 원문이 연결하지 않으면 만들지 마세요.
            - 행동 대상의 풍부한 자원·안보·전략적 위치가 해당 국가·기관의 위협·병합 시도·투자·외교 행동의 동기로 제시되면,
              그 가치→행동도 CONDITION 후보로 보존하세요. 행동 대상의 수식어일 뿐 동기·배경으로 읽히지 않으면 만들지 마세요.
            - "[가치·조건]을 가진 대상에 대한 [행동]"처럼 한 구 안에 조건과 행동이 함께 있으면 그 구 전체를 from으로 쓰지 마세요.
              원문이 그 대상을 가치·조건 때문에 행동한 것으로 제시할 때는 [가치·조건]→[행동]과 [행동]→[후속 대응]으로 분리하세요.
            - MOTIVATION은 행동 대상의 자원·안보·전략적 가치가 국가·기관의 위협·병합 시도·투자·외교 행동의 이유로 제시된 관계입니다.
              원문이 "[가치가 있는 대상]을 향한 [행동]"처럼 표현하면, 그 가치와 행동을 각각 짧게 분리해 MOTIVATION으로 기록하세요.
              단, 대상의 가치와 행동이 같은 evidence에 함께 있고 실제로 그 대상을 향한 행동일 때만 사용하세요.
            - 같은 문장이나 문단에 함께 등장했다는 이유, 대조·병렬 나열, 경제상식만으로 관계를 만들지 마세요.
            - A→B→C가 원문에 표현되면 A→B와 B→C를 각각 기록하고, 원문이 직접 지지하지 않는 A→C로 압축하지 마세요.
            - 하나의 atomicRelation은 하나의 원인 from과 하나의 결과 to만 표현하세요.
            - evidence는 관계를 직접 지지하는 기사 원문의 완전한 문장 또는 최소 원문 span을 그대로 복사하세요. 요약하거나 바꾸지 마세요.
            - issueName은 입력에 제공된 해당 기사의 issue 이름 중 하나를 글자까지 그대로 사용하세요.
            - 미래형·전망·계획도 원문에 방향성이 있으면 후보로 보존하세요. 후속 Validator가 정확성을 판정합니다.
            """;

    public static String build(List<Article> articles, ArticleAnalysisResponse analysis) {
        return format(articles, analysis);
    }

    public static String buildCoverage(List<Article> articles, ArticleAnalysisResponse analysis) {
        return """
                Relation decomposition pass입니다. evidence는 이미 원문 검증된 source span입니다.
                각 evidence만 독립적으로 읽고 existingRelations가 빠뜨린 atomic relation만 출력하세요.
                evidence에 A→B→C가 직접 표현되면 누락된 A→B 또는 B→C를 각각 출력하세요.
                상위 조건 A와 직접 원인 B가 함께 있으면 B→C를 빠뜨리지 마세요.
                existingRelations를 반복하거나 evidence 밖의 관계를 만들지 마세요.

                """ + IntStream.range(0, articles.size()).mapToObj(i -> {
                    var article = analysis.articles().get(i);
                    var groups = article.issues().stream().flatMap(issue -> issue.relations().stream()
                            .collect(java.util.stream.Collectors.groupingBy(
                                    ArticleAnalysisResponse.Relation::articleExplanation,
                                    java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()))
                            .entrySet().stream().map(entry -> java.util.Map.of(
                                    "issueName", issue.name(), "evidence", entry.getKey(),
                                    "existingRelations", entry.getValue().stream().map(relation -> java.util.Map.of(
                                            "from", relation.from(), "to", relation.to())).toList()))).toList();
                    return "articleId: %s\nissueNames: %s\nevidenceGroups: %s".formatted(article.articleId(),
                            article.issues().stream().map(ArticleAnalysisResponse.Issue::name).toList(), groups);
                }).reduce((a, b) -> a + "\n\n" + b).orElse("");
    }

    private static String format(List<Article> articles, ArticleAnalysisResponse analysis) {
        return IntStream.range(0, articles.size()).mapToObj(i -> {
            var article = articles.get(i);
            var issues = analysis.articles().get(i).issues().stream().map(ArticleAnalysisResponse.Issue::name).toList();
            return """
                    --- 기사 %d ---
                    ID: %s
                    issueNames: %s
                    제목: %s
                    RSS 요약: %s
                    본문: %s
                    """.formatted(i + 1, article.id(), issues, article.title(),
                            article.summary() == null ? "" : article.summary(),
                            article.content() == null ? "" : article.content());
        }).reduce((a, b) -> a + "\n\n" + b).orElse("");
    }

    public static String schemaForArticleCount(int count) {
        if (count < 1) throw new IllegalArgumentException("count must be positive");
        return SCHEMA.replace("ARTICLE_COUNT", Integer.toString(count));
    }

    private static final String SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"articles":{
              "type":"array","minItems":ARTICLE_COUNT,"maxItems":ARTICLE_COUNT,"items":{
                "type":"object","additionalProperties":false,"properties":{
                  "articleId":{"type":"string"},"relationCandidates":{"type":"array","items":{
                    "type":"object","additionalProperties":false,"properties":{
                      "issueName":{"type":"string"},"evidence":{"type":"string"},
                      "atomicRelations":{"type":"array","minItems":1,"items":{
                        "type":"object","additionalProperties":false,"properties":{
                          "from":{"type":"string"},"to":{"type":"string"},
                          "relationType":{"type":"string","enum":["CAUSE_OR_RESULT","PURPOSE","CHANGE","COMPARISON","CONDITION","MOTIVATION","ASSOCIATION","CLAIMED_EFFECT","EXPECTED_EFFECT","NEXT_STEP","EXPECTED_PROCESS"]},
                          "evidenceType":{"type":"string","enum":["FACT","CLAIM","INTERPRETATION","PREDICTION","PROPOSAL","PLAN"]},
                          "speaker":{"type":["string","null"]}},
                        "required":["from","to","relationType","evidenceType","speaker"]}}},
                    "required":["issueName","evidence","atomicRelations"]}}},
                "required":["articleId","relationCandidates"]}}},
            "required":["articles"]}
            """;
}

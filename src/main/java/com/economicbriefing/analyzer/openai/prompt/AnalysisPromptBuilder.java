package com.economicbriefing.analyzer.openai.prompt;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import com.economicbriefing.domain.analysis.AudienceProfile;
import com.economicbriefing.domain.article.Article;

public final class AnalysisPromptBuilder {

    private AnalysisPromptBuilder() {}

    public static String build(
            List<Article> articles,
            LocalDate targetDate,
            int maxSelectedNews,
            AudienceProfile audience,
            String articleAnalysisJson) {
        return build(articles, targetDate, maxSelectedNews, audience, articleAnalysisJson, null);
    }

    public static String build(List<Article> articles, LocalDate targetDate, int maxSelectedNews,
            AudienceProfile audience, String articleAnalysisJson, String economicFlowContextJson) {
        return build(articles, targetDate, maxSelectedNews, audience, articleAnalysisJson,
                economicFlowContextJson, null);
    }

    public static String build(List<Article> articles, LocalDate targetDate, int maxSelectedNews,
            AudienceProfile audience, String articleAnalysisJson, String economicFlowContextJson,
            String economicPrincipleContextJson) {

        String audienceSection = String.join("\n",
                "- 경제 지식 수준: " + formatKnowledgeLevel(audience.economicKnowledgeLevel()),
                "- 관심 분야: " + String.join(", ", audience.interests().stream()
                        .map(c -> c.toValue()).toList()),
                "- 참고 사항: " + String.join(", ", audience.contextNotes())
        );

        String articlesSection = IntStream.range(0, articles.size())
                .mapToObj(i -> formatArticle(articles.get(i), i + 1))
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");

        return """
                ## 분석 요청

                대상 날짜: %s
                분석할 기사 수: %d

                ## 대상 독자 프로필
                %s

                ## 검증된 Economic Flow Context

                %s

                이 Context의 node/edge만 이번 현실 흐름으로 사용하세요. Graph에 없는 현실 원인을 추측하지 마세요.
                principleQuery는 검증된 관계의 일반 메커니즘 조회 요청일 뿐, 현실 원인의 증거가 아닙니다.
                Edge로 연결되지 않은 Node 사이에 인과관계를 만들지 말고, Topic이 같다는 이유로 연결하지 마세요.
                graphExhausted=true이거나 Context가 없으면 확보된 사실까지만 설명하고 빠진 과거 원인을 채우지 마세요.
                principleQuery 자체는 근거가 아니며, 아래 검색 결과가 있을 때만 일반 메커니즘을 설명하세요.

                ## 검증된 Economic Principle Context

                %s

                Principle Context는 Article/Flow에서 이미 확인된 관계의 일반 작동 원리만 설명합니다.
                이를 이번 사건의 실제 원인이나 새로운 Flow Node/Edge로 사용하지 마세요.
                Principle Context에 없는 경제 메커니즘과 파급효과를 모델 지식으로 보충하지 마세요.
                검색 결과가 없으면 beginnerExplanation은 기사에서 확인된 관계까지만 설명하고,
                economicImpact는 기사 또는 Flow에서 확인된 영향이 없다고 명시하세요.

                ## 분석할 기사 목록

                %s

                ## Article Analyzer 결과

                아래 JSON은 기사 내부 정보만 구조화한 결과입니다. 최종 분석의 사실·관계 근거로 우선 사용하세요.

                %s

                ## 요청사항
                1. 위 각 기사에 대해 순서대로 분석하세요.
                2. 각 뉴스에 대해 "오늘 내 돈과 무슨 관련이 있는지"를 경제 초보자가 이해할 수 있도록 해설하세요. 특히 beginnerExplanation에서는 기사 내용을 반복하지 말고, 기사를 이해하기 위해 필요한 경제 원리와 배경지식을 설명하세요.
                3. 근거 없는 영향이나 예상을 만들어내지 마세요. 기사에 나온 사실만 활용하세요.
                4. 각 뉴스의 "쉬운 제목"(easyTitle)은 경제 초보자가 바로 이해할 수 있도록 전문 용어를 풀어서 작성하세요.
                5. 시스템 프롬프트에 정의된 JSON 형식으로 응답하세요. 각 뉴스에 easyTitle, threeLineSummary, whatHappened, whyItHappened, beginnerExplanation, economicImpact, terms 필드를 포함하세요.
                6. **중요**: news 배열의 순서는 위 기사 목록의 순서와 정확히 일치해야 합니다. [1]번 기사 → news[0], [2]번 기사 → news[1] 순서입니다."""
                .formatted(
                        targetDate.toString(),
                        articles.size(),
                        audienceSection,
                        economicFlowContextJson == null ? "(검증된 Economic Flow 없음)" : economicFlowContextJson,
                        economicPrincipleContextJson == null ? "(검증된 Economic Principle 없음)" : economicPrincipleContextJson,
                        articlesSection,
                        articleAnalysisJson
                );
    }

    private static String formatArticle(Article article, int index) {
        // ponytail: one-line format to fit ~600 articles in 128K context window
        String summary = article.summary() != null ? article.summary() : "";
        return "[" + index + "] " + article.id() + " | " + article.sourceName()
                + " | " + article.title() + " — " + summary;
    }

    private static String formatKnowledgeLevel(String level) {
        if ("beginner".equals(level)) {
            return "초보자";
        }
        return level;
    }
}

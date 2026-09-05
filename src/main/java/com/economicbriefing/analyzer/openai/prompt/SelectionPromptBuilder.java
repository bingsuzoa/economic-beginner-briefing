package com.economicbriefing.analyzer.openai.prompt;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import com.economicbriefing.domain.analysis.AudienceProfile;
import com.economicbriefing.domain.article.Article;

public final class SelectionPromptBuilder {

    private SelectionPromptBuilder() {}

    public static final String SYSTEM_PROMPT = """
            당신은 경제 뉴스 선별 전문가입니다.

            주어진 기사 목록에서 대상 독자에게 가장 중요한 기사를 선별하세요.

            선별 기준 (전국적 영향이 있는 것만):
            1. 전국 단위 경제 정책 변화 (금리, 세금, 연금, 보험 등)
            2. 주요 상장기업의 실적 발표, 종목 분석, 산업 뉴스
            3. 부동산 시장 동향 (전국 또는 수도권 단위)
            4. 환율, 물가, 금융시장 변동

            반드시 제외:
            - 특정 시/군/구의 행정 소식 (예: ○○시 LED 교체, ○○도 냉방비 지원, ○○군 교육사업)
            - 지역 단위 정책 발표, 지역 의회 소식
            - 기업 인사, 외교 의전, 정치 스캔들
            - 날씨 정보, 기상 특보
            - 스포츠, 연예, 문화행사
            - 사건사고, 범죄, 재난, 안전사고
            - 인물 동정, 기념행사

            JSON 형식으로 응답하세요:
            {
              "selectedArticleIndexes": [1, 2, ...]
            }
            selectedArticleIndexes에는 기사 목록의 대괄호 안 1-based 순번만 정수로 넣으세요.
            article ID를 복사하거나 문자열로 반환하지 마세요.
            """;

    public static String build(
            List<Article> articles,
            LocalDate targetDate,
            int maxSelectedNews,
            AudienceProfile audience) {

        String audienceSection = String.join("\n",
                "- 경제 지식 수준: " + formatKnowledgeLevel(audience.economicKnowledgeLevel()),
                "- 관심 분야: " + String.join(", ", audience.interests().stream()
                        .map(c -> c.toValue()).toList()),
                "- 참고 사항: " + String.join(", ", audience.contextNotes())
        );

        String articlesSection = IntStream.range(0, articles.size())
                .mapToObj(i -> formatArticle(articles.get(i), i + 1))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        return """
                ## 선별 요청

                대상 날짜: %s
                최대 선별 뉴스 수: %d
                전체 기사 수: %d

                ## 대상 독자 프로필
                %s

                ## 수집된 기사 목록

                %s

                ## 요청사항
                위 기사 중 대상 독자에게 가장 중요한 뉴스를 최대 %d개 선별하여 기사 순번만 반환하세요.
                예: [3]번과 [7]번을 고르면 {"selectedArticleIndexes":[3,7]}를 반환합니다.
                같은 사건에 대한 기사는 하나만 선택하세요.
                """
                .formatted(
                        targetDate.toString(),
                        maxSelectedNews,
                        articles.size(),
                        audienceSection,
                        articlesSection,
                        maxSelectedNews
                );
    }

    private static String formatArticle(Article article, int index) {
        return "[" + index + "] " + article.id() + " | " + article.sourceName()
                + " | " + article.title();
    }

    private static String formatKnowledgeLevel(String level) {
        if ("beginner".equals(level)) {
            return "초보자";
        }
        return level;
    }
}

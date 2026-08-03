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

            선별 기준:
            1. 대상 독자의 경제생활에 직접적인 영향을 주는 뉴스
            2. 주요 경제 정책 변화
            3. 주요 기업의 실적 발표, 종목 분석, 산업 뉴스
            4. 부동산, 금리, 환율 등 자산 가격 변동

            제외 기준:
            - 기업 인사, 외교 의전, 정치 스캔들
            - 날씨 정보, 기상 특보
            - 스포츠, 연예, 문화행사
            - 사건사고, 범죄, 재난, 안전사고
            - 지역 소식, 인물 동정

            JSON 형식으로 응답하세요:
            {
              "selectedArticleIds": ["article-id-1", "article-id-2", ...]
            }
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
                위 기사 중 대상 독자에게 가장 중요한 뉴스를 최대 %d개 선별하여 article ID만 반환하세요.
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

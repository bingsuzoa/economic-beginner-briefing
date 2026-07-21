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
            AudienceProfile audience) {

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
                최대 선별 뉴스 수: %d
                전체 기사 수: %d

                ## 대상 독자 프로필
                %s

                ## 수집된 기사 목록

                %s

                ## 요청사항
                1. 위 기사 중 대상 독자에게 가장 중요한 뉴스를 최대 %d개 선별하세요.
                2. 같은 사건에 대한 기사는 반드시 하나로 그룹화하고, 대표 출처를 선정하세요. 같은 사건을 별도 뉴스로 분리하지 마세요.
                3. 각 뉴스에 대해 "오늘 내 돈과 무슨 관련이 있는지"를 경제 초보자가 이해할 수 있도록 해설하세요.
                4. 개인 재테크나 가계 생활과 직접 관련 없는 기사(기업 인사, 특정 종목 분석, 산업 단신)는 선별하지 마세요.
                5. 다양한 카테고리의 뉴스가 골고루 포함되도록 하세요.
                6. 근거 없는 영향이나 예상을 만들어내지 마세요. 기사에 나온 사실만 활용하세요.
                7. "왜 중요한가"(whyImportant)는 기사 내용 반복이 아닌, 독자에게 왜 중요한지를 설명하세요.
                8. "앞으로 지켜볼 내용", "지금 확인할 것", "누가 꼭 읽어야 하나"(targetAudience), "영향도 평가"(impactAssessment)를 생성하지 마세요. 이 항목들은 제거되었습니다.
                9. 시스템 프롬프트에 정의된 JSON 형식으로 응답하세요."""
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
        StringBuilder sb = new StringBuilder();
        sb.append("--- 기사 ").append(index).append(" ---\n");
        sb.append("ID: ").append(article.id()).append("\n");
        sb.append("제목: ").append(article.title()).append("\n");
        sb.append("요약: ").append(article.summary()).append("\n");
        sb.append("출처: ").append(article.sourceName())
                .append(" (").append(article.sourceType().toValue()).append(")\n");
        sb.append("게시일: ").append(article.publishedAt()).append("\n");
        sb.append("URL: ").append(article.url()).append("\n");
        sb.append("카테고리: ").append(
                article.categories().stream()
                        .map(c -> c.toValue())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("")
        );

        if (article.content() != null && !article.content().isBlank()) {
            sb.append("\n본문:\n").append(article.content());
        }

        return sb.toString();
    }

    private static String formatKnowledgeLevel(String level) {
        if ("beginner".equals(level)) {
            return "초보자";
        }
        return level;
    }
}

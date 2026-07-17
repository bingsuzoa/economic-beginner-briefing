import type { Article } from "../../../domain/article.js";
import type { AudienceProfile } from "../../../domain/analyzedNews.js";

export function buildAnalysisPrompt(options: {
  articles: Article[];
  targetDate: string;
  maxSelectedNews: number;
  audience: AudienceProfile;
}): string {
  const { articles, targetDate, maxSelectedNews, audience } = options;

  const audienceSection = [
    `- 경제 지식 수준: ${audience.economicKnowledgeLevel === "beginner" ? "초보자" : audience.economicKnowledgeLevel}`,
    `- 관심 분야: ${audience.interests.join(", ")}`,
    `- 참고 사항: ${audience.contextNotes.join(", ")}`,
  ].join("\n");

  const articlesSection = articles
    .map((article, index) => {
      const parts = [
        `--- 기사 ${index + 1} ---`,
        `ID: ${article.id}`,
        `제목: ${article.title}`,
        `요약: ${article.summary}`,
        `출처: ${article.sourceName} (${article.sourceType})`,
        `게시일: ${article.publishedAt}`,
        `URL: ${article.url}`,
        `카테고리: ${article.categories.join(", ")}`,
      ];

      if (article.content) {
        parts.push(`본문:\n${article.content}`);
      }

      return parts.join("\n");
    })
    .join("\n\n");

  return `## 분석 요청

대상 날짜: ${targetDate}
최대 선별 뉴스 수: ${maxSelectedNews}
전체 기사 수: ${articles.length}

## 대상 독자 프로필
${audienceSection}

## 수집된 기사 목록

${articlesSection}

## 요청사항
1. 위 기사 중 대상 독자에게 가장 중요한 뉴스를 최대 ${maxSelectedNews}개 선별하세요.
2. 같은 사건에 대한 기사는 하나로 그룹화하고, 대표 출처를 선정하세요.
3. 각 뉴스에 대해 경제 초보자가 이해할 수 있는 해설을 작성하세요.
4. 시스템 프롬프트에 정의된 JSON 형식으로 응답하세요.`;
}

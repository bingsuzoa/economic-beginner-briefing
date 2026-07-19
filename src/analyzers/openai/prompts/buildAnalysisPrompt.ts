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
2. 같은 사건에 대한 기사는 반드시 하나로 그룹화하고, 대표 출처를 선정하세요. 같은 사건을 별도 뉴스로 분리하지 마세요.
3. 각 뉴스에 대해 "오늘 내 돈과 무슨 관련이 있는지"를 경제 초보자가 이해할 수 있도록 해설하세요.
4. 개인 재테크나 가계 생활과 직접 관련 없는 기사(기업 인사, 특정 종목 분석, 산업 단신)는 선별하지 마세요.
5. 다양한 카테고리의 뉴스가 골고루 포함되도록 하세요.
6. 근거 없는 영향이나 예상을 만들어내지 마세요. 기사에 나온 사실만 활용하세요.
7. "누가 꼭 읽어야 하나?"(targetAudience)를 반드시 포함하세요.
8. "왜 중요한가"(whyImportant)는 기사 내용 반복이 아닌, 독자에게 왜 중요한지를 설명하세요.
9. "앞으로 지켜볼 내용"이나 "지금 확인할 것"을 생성하지 마세요. 이 항목들은 제거되었습니다.
10. 시스템 프롬프트에 정의된 JSON 형식으로 응답하세요.`;
}

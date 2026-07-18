import type { Briefing } from "../../../src/domain/briefing.js";

export function createBriefingFixture(overrides: Partial<Briefing> = {}): Briefing {
  const briefing: Briefing = {
    id: "briefing-2026-07-16",
    targetDate: "2026-07-16",
    generatedAt: "2026-07-17T04:30:00+09:00",
    title: "2026-07-16 경제 브리핑",
    overallSummary: [
      "전세 보증금 관리 제도 변화가 검토되고 있습니다.",
      "가계 대출 금리 흐름을 함께 확인할 필요가 있습니다.",
    ],
    news: [
      {
        id: "news-1",
        representativeTitle: "전세보증금 별도 관리 방안 검토",
        category: "jeonse_monthly_rent",
        importance: 5,
        relevanceReason: "전세 세입자의 보증금 안전성과 임대인의 자금 운용에 영향을 줄 수 있습니다.",
        oneLineSummary: "전세보증금을 별도 기관이 관리하는 방안이 검토되고 있습니다.",
        explanation: "정부와 관련 기관이 전세보증금 관리 방식 개선을 논의하고 있습니다. 전세사기와 보증금 미반환 위험을 줄이기 위한 목적인데요, 기존에는 집주인이 전세보증금을 직접 보유하는 경우가 많았습니다.\n\n보증금을 별도 계정이나 기관에서 관리하는 방식이 거론되고 있어, 세입자는 보증금 안전성을 더 확인할 수 있지만 전세 공급 변화도 지켜봐야 합니다.\n\n신혼부부는 전세 계약 전 보증금 보호 장치를 더 꼼꼼히 확인해야 합니다.",
        expectedNextEffects: [
          "전세 제도 개편 논의가 이어질 수 있습니다.",
          "일부 임대인은 월세 전환을 검토할 가능성이 있습니다.",
        ],
        recommendedChecks: [
          "전세보증보험 가입 가능 여부",
          "계약하려는 주택의 근저당과 보증금 순위",
        ],
        evidenceStatus: "proposed",
        uncertaintyNote: "아직 확정된 제도는 아니며 실제 시행 여부는 추가 확인이 필요합니다.",
        economicTerms: [
          {
            term: "전세보증금",
            explanation: "세입자가 집주인에게 맡기는 큰 금액의 보증금입니다.",
            example: "계약 종료 때 돌려받아야 하는 돈입니다.",
          },
        ],
        sources: [
          {
            articleId: "article-1",
            sourceName: "경제신문",
            title: "전세보증금 관리 제도 논의",
            url: "https://example.com/news/1",
            publishedAt: "2026-07-16T09:00:00+09:00",
            isPrimary: true,
          },
        ],
      },
    ],
    glossary: [
      {
        term: "근저당",
        explanation: "집을 담보로 빌린 돈이 있다는 표시입니다.",
      },
    ],
    metadata: {
      collectedArticleCount: 12,
      analyzedArticleCount: 12,
      selectedNewsCount: 1,
      modelName: "test-model",
      promptVersion: "test-v1",
    },
  };

  return {
    ...briefing,
    ...overrides,
  };
}

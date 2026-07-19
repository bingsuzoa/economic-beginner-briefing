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
        whyImportant: "전세보증금 반환 실패 시 세입자가 큰 재산 손실을 입을 수 있고, 제도 변경 시 전세 시장 구조가 달라질 수 있기 때문입니다.",
        targetAudience: {
          mustRead: ["전세 계약 예정자", "신혼부부", "전세 세입자"],
          notRelevant: ["이미 일반 아파트를 계약한 사람", "자가 거주자"],
        },
        oneLineSummary: "전세보증금을 별도 기관이 관리하는 방안이 검토되고 있습니다.",
        explanation: "[무슨 일이 있었나]\n\n정부와 관련 기관이 전세보증금 관리 방식 개선을 논의하고 있습니다. 기존에는 집주인이 전세보증금을 직접 보유하는 경우가 많았는데, 전세사기와 보증금 미반환 위험을 줄이기 위해 별도 계정이나 기관에서 관리하는 방식이 거론되고 있습니다.\n\n[왜 이런 일이 발생했나]\n\n최근 몇 년간 전세사기 피해가 급증하면서 세입자 보호가 사회적 이슈로 부각되었습니다. 기존 보증보험만으로는 피해를 완전히 막기 어려웠고, 보다 근본적인 구조 개선이 필요하다는 논의가 이어졌습니다.\n\n[우리에게 어떤 의미가 있나]\n\n전세 계약을 앞두고 있는 사람은 보증금 보호 장치(보증보험, 확정일자)를 더 꼼꼼히 확인할 필요가 있습니다.\n\n이미 자가에 거주하는 사람은 이번 뉴스의 직접적인 영향은 거의 없습니다.",
        impactAssessment: [
          { target: "전세 세입자", score: 4, reason: "보증금 안전성 변화" },
          { target: "신혼부부", score: 3, reason: "전세 계약 시 고려사항 변화" },
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

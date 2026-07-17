import type { NewsCollector } from "../NewsCollector.js";
import type { CollectNewsRequest, CollectNewsResult, Article } from "../../domain/article.js";

function createMockArticles(targetDate: string): Article[] {
  const publishedAt = `${targetDate}T10:00:00+09:00`;
  const collectedAt = `${targetDate}T23:30:00+09:00`;

  return [
    {
      id: "mock-article-001",
      title: "한국은행, 기준금리 0.25%p 인하 결정",
      summary:
        "한국은행 금융통화위원회가 기준금리를 연 3.25%에서 3.00%로 0.25%포인트 인하했다.",
      sourceName: "한국은행",
      sourceType: "government",
      publishedAt,
      collectedAt,
      url: "https://example.com/articles/base-rate-cut",
      categories: ["interest_rate"],
      language: "ko",
    },
    {
      id: "mock-article-002",
      title: "전세보증금 별도관리 제도 검토 착수",
      summary:
        "금융위원회가 전세보증금을 집주인이 직접 보유하지 않고 별도 기관에서 관리하는 방안을 검토하기로 했다.",
      sourceName: "금융위원회",
      sourceType: "government",
      publishedAt,
      collectedAt,
      url: "https://example.com/articles/jeonse-deposit-management",
      categories: ["jeonse_monthly_rent", "housing"],
      language: "ko",
    },
    {
      id: "mock-article-003",
      title: "주요 은행, 정기예금 금리 일제히 인하",
      summary:
        "기준금리 인하 영향으로 KB국민, 신한, 우리 등 주요 시중은행이 정기예금 금리를 0.1~0.2%p 내렸다.",
      sourceName: "연합뉴스",
      sourceType: "news_media",
      publishedAt,
      collectedAt,
      url: "https://example.com/articles/deposit-rate-drop",
      categories: ["deposit_saving", "interest_rate"],
      language: "ko",
    },
  ];
}

export class MockNewsCollector implements NewsCollector {
  async collect(request: CollectNewsRequest): Promise<CollectNewsResult> {
    const articles = createMockArticles(request.targetDate);

    return {
      targetDate: request.targetDate,
      articles,
      sourceReports: [
        {
          sourceName: "한국은행",
          status: "success",
          collectedCount: 1,
          acceptedCount: 1,
        },
        {
          sourceName: "금융위원회",
          status: "success",
          collectedCount: 1,
          acceptedCount: 1,
        },
        {
          sourceName: "연합뉴스",
          status: "success",
          collectedCount: 1,
          acceptedCount: 1,
        },
      ],
      totalCollected: 3,
      totalAccepted: 3,
      totalRejected: 0,
    };
  }
}

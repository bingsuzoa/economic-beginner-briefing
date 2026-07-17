import type { NewsAnalyzer } from "../NewsAnalyzer.js";
import type { AnalyzeNewsRequest, AnalyzeNewsResult } from "../../domain/analyzedNews.js";
import type { Article } from "../../domain/article.js";
import type { Briefing } from "../../domain/briefing.js";
import { nowISOStringKST } from "../../utils/date.js";

function articleToAnalyzedNews(article: Article, index: number) {
  const analysisMap: Record<
    string,
    {
      whatHappened: string;
      previousSituation: string;
      whatChanged: string;
      whyItChanged: string;
      householdImpact: string;
      newlywedHousingImpact: string;
      expectedNextEffects: string[];
      recommendedChecks: string[];
      evidenceStatus: "confirmed" | "proposed" | "expected";
      economicTerms: { term: string; explanation: string; example?: string }[];
    }
  > = {
    "mock-article-001": {
      whatHappened:
        "한국은행이 기준금리를 연 3.25%에서 3.00%로 0.25%포인트 내렸습니다.",
      previousSituation:
        "기준금리가 연 3.25%로 유지되고 있었습니다. 시중은행의 대출금리와 예금금리는 이 기준금리에 연동되어 움직입니다.",
      whatChanged:
        "기준금리가 0.25%포인트 내려 연 3.00%가 되었습니다. 이에 따라 은행들의 대출금리와 예금금리도 낮아질 가능성이 있습니다.",
      whyItChanged:
        "경기 둔화 우려와 물가 안정세를 고려한 한국은행의 판단입니다.",
      householdImpact:
        "변동금리 대출을 가진 가계는 이자 부담이 줄어들 수 있습니다. 반면 예금이나 적금에 돈을 넣어둔 경우 이자 수입이 줄어들 수 있습니다.",
      newlywedHousingImpact:
        "주택담보대출을 준비하는 신혼부부에게 유리할 수 있습니다. 대출금리가 낮아지면 같은 금액을 빌려도 매달 갚는 이자가 줄어들기 때문입니다.",
      expectedNextEffects: [
        "시중은행 대출금리가 점진적으로 내려갈 가능성이 있습니다.",
        "정기예금 금리도 함께 내려갈 수 있습니다.",
        "부동산 시장에서 매수 심리가 되살아날 가능성이 있습니다.",
      ],
      recommendedChecks: [
        "변동금리 대출이 있다면 다음 금리 변경일을 확인하세요.",
        "예금 만기가 다가오면 금리 비교 후 갱신 여부를 검토하세요.",
      ],
      evidenceStatus: "confirmed",
      economicTerms: [
        {
          term: "기준금리",
          explanation:
            "한국은행이 정하는 금리로, 은행들이 서로 돈을 빌려줄 때 기준이 되는 금리입니다. 시중은행의 예금금리와 대출금리는 이 기준금리에 영향을 받아 움직입니다.",
          example:
            "기준금리가 3.00%이면 은행의 주택담보대출 금리는 보통 이보다 1~2%포인트 높은 4~5% 수준에서 형성됩니다.",
        },
      ],
    },
    "mock-article-002": {
      whatHappened:
        "금융위원회가 전세보증금을 집주인이 아닌 별도 기관에서 관리하는 방안을 검토하기 시작했습니다.",
      previousSituation:
        "기존에는 세입자가 집주인에게 전세보증금을 직접 지급하면, 집주인이 이 돈을 자유롭게 보유하고 활용할 수 있었습니다.",
      whatChanged:
        "새 제도가 도입되면 전세보증금이 별도 기관이나 계좌에서 관리될 가능성이 있습니다. 세입자의 보증금 반환 안전성이 높아질 수 있습니다.",
      whyItChanged:
        "전세 사기와 보증금 미반환 피해가 사회적 문제로 대두되었기 때문입니다.",
      householdImpact:
        "세입자는 보증금을 돌려받지 못할 위험이 줄어들 수 있습니다. 반면 집주인은 보증금을 다른 용도로 활용하기 어려워질 수 있습니다.",
      newlywedHousingImpact:
        "전세를 준비하는 신혼부부에게는 보증금 안전성이 높아지는 긍정적 변화일 수 있습니다. 다만 일부 집주인이 전세 대신 월세를 선택할 가능성도 있습니다.",
      expectedNextEffects: [
        "전세 사기 피해가 줄어들 가능성이 있습니다.",
        "일부 집주인이 전세 대신 월세를 선호할 수 있습니다.",
        "전세 물건의 공급이 줄어들 가능성도 있습니다.",
      ],
      recommendedChecks: [
        "현재 전세계약 중이라면 보증보험 가입 여부를 확인하세요.",
        "정책이 확정되기 전 계약 조건을 단정하지 마세요.",
      ],
      evidenceStatus: "proposed",
      economicTerms: [
        {
          term: "전세보증금",
          explanation:
            "세입자가 집주인에게 맡기는 큰 금액의 보증금입니다. 계약이 끝나면 돌려받아야 하는 돈으로, 그동안 집주인이 이 돈을 보유합니다.",
          example:
            "서울의 전세보증금은 보통 수억 원 수준이며, 계약 기간은 2년이 일반적입니다.",
        },
      ],
    },
    "mock-article-003": {
      whatHappened:
        "KB국민, 신한, 우리 등 주요 시중은행이 정기예금 금리를 0.1~0.2%포인트 인하했습니다.",
      previousSituation:
        "주요 은행의 1년 만기 정기예금 금리는 약 3.5% 수준이었습니다.",
      whatChanged:
        "기준금리 인하 영향으로 정기예금 금리가 3.3~3.4% 수준으로 내려갔습니다.",
      whyItChanged:
        "한국은행이 기준금리를 인하하면서 은행들의 자금 조달 비용이 낮아졌기 때문입니다.",
      householdImpact:
        "예금으로 이자 수입을 얻고 있는 가계는 수익이 줄어들 수 있습니다. 반면 대출금리도 함께 내려갈 수 있어 대출자에게는 유리합니다.",
      newlywedHousingImpact:
        "결혼 자금을 예금에 넣어둔 경우 이자 수입이 다소 줄어들 수 있습니다. 주택 구입용 대출 금리도 내려갈 수 있으니 비교 검토가 필요합니다.",
      expectedNextEffects: [
        "추가 기준금리 인하 시 예금금리가 더 내려갈 수 있습니다.",
        "적금 금리도 조정될 가능성이 있습니다.",
      ],
      recommendedChecks: [
        "예금 만기가 가까운 경우 은행별 금리를 비교해 보세요.",
        "적금 가입을 고려한다면 현재 금리를 확인하세요.",
      ],
      evidenceStatus: "confirmed",
      economicTerms: [
        {
          term: "정기예금",
          explanation:
            "일정 기간 동안 돈을 은행에 맡기고, 만기 때 원금과 이자를 함께 돌려받는 상품입니다. 보통 중도 해지하면 이자가 줄어듭니다.",
        },
      ],
    },
  };

  const detail = analysisMap[article.id];

  return {
    id: `analyzed-${article.id}`,
    representativeTitle: article.title,
    category: article.categories[0] ?? "other",
    importance: (5 - index) as 3 | 4 | 5,
    relevanceReason: "일반 가계에 직접적인 영향이 있는 경제 뉴스입니다.",

    oneLineSummary: article.summary,
    whatHappened: detail?.whatHappened ?? article.summary,
    previousSituation: detail?.previousSituation ?? "확인된 내용이 부족합니다.",
    whatChanged: detail?.whatChanged ?? "확인된 내용이 부족합니다.",
    whyItChanged: detail?.whyItChanged ?? "확인된 내용이 부족합니다.",

    householdImpact: detail?.householdImpact ?? "확인된 내용이 부족합니다.",
    newlywedHousingImpact:
      detail?.newlywedHousingImpact ?? "확인된 내용이 부족합니다.",
    expectedNextEffects: detail?.expectedNextEffects ?? [],
    recommendedChecks: detail?.recommendedChecks ?? [],

    evidenceStatus: detail?.evidenceStatus ?? ("expected" as const),
    economicTerms: detail?.economicTerms ?? [],
    sources: [
      {
        articleId: article.id,
        sourceName: article.sourceName,
        title: article.title,
        url: article.url,
        publishedAt: article.publishedAt,
        isPrimary: true,
      },
    ],
  };
}

export class MockNewsAnalyzer implements NewsAnalyzer {
  async analyze(request: AnalyzeNewsRequest): Promise<AnalyzeNewsResult> {
    const selectedArticles = request.articles.slice(
      0,
      request.maxSelectedNews,
    ) as Article[];

    const news = selectedArticles.map((article, index) =>
      articleToAnalyzedNews(article, index),
    );

    const allTerms = news.flatMap((n) => n.economicTerms);
    const uniqueTerms = allTerms.filter(
      (term, idx) => allTerms.findIndex((t) => t.term === term.term) === idx,
    );

    const briefing: Briefing = {
      id: `briefing-${request.targetDate}`,
      targetDate: request.targetDate,
      generatedAt: nowISOStringKST(),
      title: `${request.targetDate} 경제 브리핑`,
      overallSummary: [
        "한국은행이 기준금리를 인하하면서 대출금리와 예금금리 모두 영향을 받을 전망입니다.",
        "전세보증금 별도관리 제도가 검토되고 있어 전세 시장에 변화가 있을 수 있습니다.",
      ],
      news,
      glossary: uniqueTerms,
      metadata: {
        collectedArticleCount: request.articles.length,
        analyzedArticleCount: request.articles.length,
        selectedNewsCount: news.length,
        modelName: "mock",
        promptVersion: "foundation-v1",
      },
    };

    const selectedIds = new Set(selectedArticles.map((a) => a.id));
    const rejectedArticleIds = request.articles
      .filter((a) => !selectedIds.has(a.id))
      .map((a) => a.id);

    return {
      briefing,
      rejectedArticleIds,
      warnings: [],
    };
  }
}

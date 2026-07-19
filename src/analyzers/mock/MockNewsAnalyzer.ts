import type { NewsAnalyzer } from "../NewsAnalyzer.js";
import type { AnalyzeNewsRequest, AnalyzeNewsResult } from "../../domain/analyzedNews.js";
import type { Article } from "../../domain/article.js";
import type { Briefing } from "../../domain/briefing.js";
import { nowISOStringKST } from "../../utils/date.js";

function articleToAnalyzedNews(article: Article, index: number) {
  const analysisMap: Record<
    string,
    {
      whyImportant: string;
      targetAudience: { mustRead: string[]; notRelevant: string[] };
      explanation: string;
      evidenceStatus: "confirmed" | "proposed" | "expected";
      economicTerms: { term: string; explanation: string; example?: string }[];
    }
  > = {
    "mock-article-001": {
      whyImportant: "변동금리 대출을 보유한 가계의 이자 부담이 직접적으로 줄어들고, 주택담보대출을 준비하는 사람의 자금 계획에 영향을 주기 때문입니다.",
      targetAudience: {
        mustRead: ["변동금리 대출자", "주택담보대출 예정자", "신혼부부"],
        notRelevant: ["고정금리 대출자", "대출이 없는 사람"],
      },
      explanation:
        "[무슨 일이 있었나]\n\n한국은행 금융통화위원회가 기준금리를 연 3.25%에서 3.00%로 0.25%포인트 내렸습니다. 그동안 기준금리가 3.25%로 유지되면서 시중은행의 대출금리와 예금금리도 이에 맞춰 움직이고 있었는데, 이번에 처음으로 인하가 이루어진 겁니다.\n\n[왜 이런 일이 발생했나]\n\n경기 둔화 우려와 물가 안정세를 고려한 판단입니다. 쉽게 말해 경제가 힘들어지고 있으니 돈을 빌리는 비용을 낮춰서 경기를 살려보자는 의도예요.\n\n[우리에게 어떤 의미가 있나]\n\n변동금리 대출을 가지고 계신 분들의 이자 부담이 줄어들 수 있습니다. 주택담보대출을 준비하는 신혼부부에게는 유리한 소식입니다. 대출금리가 낮아지면 같은 금액을 빌려도 매달 갚는 이자가 줄어들기 때문입니다.\n\n반면 예금이나 적금에 돈을 넣어두신 분들은 이자 수입이 줄어들 수 있어요.\n\n고정금리 대출자에게는 직접적인 영향이 거의 없습니다.",
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
      whyImportant: "전세보증금 반환 실패 시 세입자가 큰 재산 손실을 입을 수 있고, 제도 변경 시 전세 시장 구조 자체가 달라질 수 있기 때문입니다.",
      targetAudience: {
        mustRead: ["전세 계약 예정자", "신혼부부", "현재 전세 세입자"],
        notRelevant: ["자가 거주자", "월세 거주자"],
      },
      explanation:
        "[무슨 일이 있었나]\n\n금융위원회가 전세보증금을 집주인이 아닌 별도 기관에서 관리하는 방안을 검토하기 시작했습니다. 기존에는 세입자가 집주인에게 전세보증금을 직접 지급하면, 집주인이 이 돈을 자유롭게 보유하고 활용할 수 있었습니다.\n\n[왜 이런 일이 발생했나]\n\n최근 전세 사기와 보증금 미반환 피해가 사회적 문제로 대두되면서, 세입자의 보증금을 더 안전하게 보호할 방법을 찾고 있는 겁니다.\n\n[우리에게 어떤 의미가 있나]\n\n전세 계약을 앞두고 있는 사람은 보증금 보호 장치(보증보험, 확정일자)를 더 꼼꼼히 확인할 필요가 있습니다. 전세를 준비하는 신혼부부에게는 보증금 안전성이 높아지는 긍정적 변화일 수 있습니다.\n\n자가에 거주하는 사람은 이번 뉴스의 직접적인 영향은 거의 없습니다.\n\n다만 아직 확정된 제도는 아닌 만큼 정책 확정 전까지 계약 조건을 단정하지 않는 것이 좋겠습니다.",
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
      whyImportant: "예금으로 이자 수입을 얻고 있는 가계의 실질 수익이 줄어들고, 만기 후 재예치 조건이 달라지기 때문입니다.",
      targetAudience: {
        mustRead: ["정기예금 보유자", "결혼 자금 저축 중인 사람"],
        notRelevant: ["대출만 보유한 사람"],
      },
      explanation:
        "[무슨 일이 있었나]\n\nKB국민, 신한, 우리 등 주요 시중은행이 정기예금 금리를 0.1~0.2%포인트 인하했습니다. 그동안 주요 은행의 1년 만기 정기예금 금리는 약 3.5% 수준이었는데, 이번에 3.3~3.4% 수준으로 내려갔습니다.\n\n[왜 이런 일이 발생했나]\n\n한국은행이 기준금리를 내리면서 은행들의 자금 조달 비용이 낮아졌고, 이에 따라 예금금리도 함께 내려간 겁니다. 기준금리와 예금금리는 보통 같은 방향으로 움직이기 때문에 예상된 변화라고 할 수 있습니다.\n\n[우리에게 어떤 의미가 있나]\n\n예금으로 이자 수입을 얻고 있는 가계는 수익이 줄어들 수 있습니다. 결혼 자금을 예금에 넣어둔 신혼부부라면 이자 수입이 다소 줄어들 수 있습니다.\n\n반면 대출금리도 함께 내려갈 수 있어서, 대출을 가지고 계신 분들에게는 오히려 유리한 상황이에요.",
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
    whyImportant: detail?.whyImportant ?? "일반 가계에 직접적인 영향이 있는 경제 뉴스입니다.",
    targetAudience: detail?.targetAudience ?? {
      mustRead: ["일반 가계"],
      notRelevant: [],
    },

    impactAssessment: [
      { target: "일반 가계", score: 4, reason: "가계 이자 부담 변화" },
      { target: "신혼부부", score: 3, reason: "주거비용 영향" },
    ],
    oneLineSummary: article.summary,
    explanation: detail?.explanation ?? article.summary,

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
      title: request.briefingTitle ?? `${request.targetDate} 경제 브리핑`,
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

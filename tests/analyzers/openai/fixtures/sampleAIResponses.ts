import type { AIResponse } from "../../../../src/analyzers/openai/prompts/responseSchema.js";

export const validAIResponse: AIResponse = {
  overallSummary: [
    "한국은행이 기준금리를 인하하면서 대출금리와 예금금리 모두 영향을 받을 전망입니다.",
    "서울 아파트 매매가가 3주 연속 상승하고 있으며, 신혼부부 특별공급 요건 완화가 검토 중입니다.",
  ],
  news: [
    {
      id: "news-1",
      representativeTitle: "한국은행 기준금리 0.25%p 인하…연 3.00%로",
      category: "interest_rate",
      importance: 5,
      whyImportant: "변동금리 대출을 보유한 가계의 이자 부담이 직접적으로 줄어들고, 주택담보대출을 준비하는 사람의 자금 계획에 영향을 주기 때문입니다.",
      targetAudience: {
        mustRead: ["변동금리 대출자", "주택담보대출 예정자", "신혼부부"],
        notRelevant: ["고정금리 대출자"],
      },
      oneLineSummary: "한국은행이 기준금리를 연 3.25%에서 3.00%로 인하했습니다.",
      explanation: "[무슨 일이 있었나]\n\n한국은행 금융통화위원회가 기준금리를 연 3.25%에서 3.00%로 0.25%포인트 인하했습니다. 그동안 기준금리가 3.25%로 유지되면서 시중은행의 대출금리와 예금금리도 이에 연동되어 움직이고 있었습니다.\n\n[왜 이런 일이 발생했나]\n\n경기 둔화 우려와 물가 안정세를 고려한 판단입니다. 경제가 힘들어지고 있으니 돈을 빌리는 비용을 낮춰서 경기를 살려보자는 의도예요.\n\n[우리에게 어떤 의미가 있나]\n\n변동금리 대출을 가진 가계는 이자 부담이 줄어들 수 있습니다. 주택담보대출을 준비하는 신혼부부에게는 유리한 소식입니다. 대출금리가 낮아지면 같은 금액을 빌려도 매달 갚는 이자가 줄어들기 때문입니다.\n\n고정금리 대출자에게는 직접적인 영향이 거의 없습니다.",
      evidenceStatus: "confirmed",
      economicTerms: [
        {
          term: "기준금리",
          explanation: "한국은행이 정하는 금리로, 은행들이 서로 돈을 빌려줄 때 기준이 되는 금리입니다.",
          example: "기준금리가 3.00%이면 은행의 주택담보대출 금리는 보통 이보다 1~2%포인트 높은 4~5% 수준입니다.",
        },
      ],
      sources: [
        { articleId: "art-interest-001", isPrimary: true },
      ],
    },
    {
      id: "news-2",
      representativeTitle: "서울 아파트 매매가 3주 연속 상승",
      category: "housing",
      importance: 4,
      whyImportant: "주택 매수를 준비하는 사람의 자금 부담이 커지고, 매입 시기 판단에 직접적인 영향을 주기 때문입니다.",
      targetAudience: {
        mustRead: ["주택 매수 예정자", "신혼부부"],
        notRelevant: ["이미 주택을 보유한 사람", "전세 거주 중 매수 계획 없는 사람"],
      },
      oneLineSummary: "서울 아파트 매매가격이 3주 연속 상승하며 거래량이 증가하고 있습니다.",
      explanation: "[무슨 일이 있었나]\n\n서울 아파트 매매가격이 3주 연속 상승세를 보이며 거래량도 함께 증가하고 있습니다. 그동안 금리 인상 영향으로 부동산 거래가 위축되어 있었는데, 최근 분위기가 바뀌고 있습니다.\n\n[왜 이런 일이 발생했나]\n\n금리 인하 기대와 규제 완화 기조가 맞물리면서 시장 심리가 개선된 영향입니다. 매수세가 살아나며 가격이 상승하고 있습니다.\n\n[우리에게 어떤 의미가 있나]\n\n주택 구입을 계획하는 신혼부부에게는 매입 시기에 대한 고민이 필요한 상황입니다. 가격이 오르는 만큼 자금 부담이 커질 수 있습니다.\n\n이미 주택을 보유한 사람에게는 자산 가치 상승이므로 직접적인 부담은 없습니다.",
      evidenceStatus: "confirmed",
      economicTerms: [],
      sources: [
        { articleId: "art-housing-001", isPrimary: true },
      ],
    },
    {
      id: "news-3",
      representativeTitle: "신혼부부 특별공급 소득 요건 완화 검토",
      category: "government_support",
      importance: 4,
      whyImportant: "기존 소득 기준으로 탈락했던 맞벌이 신혼부부가 특별공급에 다시 지원할 수 있는 기회가 생길 수 있기 때문입니다.",
      targetAudience: {
        mustRead: ["맞벌이 신혼부부", "특별공급 지원 예정자"],
        notRelevant: ["이미 주택을 보유한 사람", "1인 가구"],
      },
      oneLineSummary: "국토교통부가 신혼부부 특별공급의 소득 기준 완화를 검토하고 있습니다.",
      explanation: "[무슨 일이 있었나]\n\n국토교통부가 신혼부부 특별공급의 소득 기준을 완화하는 방안을 검토 중입니다. 기존 소득 기준으로는 맞벌이 신혼부부 중 상당수가 특별공급 대상에서 제외되었습니다.\n\n[왜 이런 일이 발생했나]\n\n저출산 대응과 주거 안정 정책의 일환으로 추진되고 있습니다. 맞벌이가 일반적인 현실을 반영하여 소득 기준을 현실화하려는 움직임입니다.\n\n[우리에게 어떤 의미가 있나]\n\n소득 기준이 완화되면 더 많은 신혼부부가 특별공급에 지원할 수 있게 됩니다. 기존에 소득 기준으로 탈락했다면 재도전이 가능해질 수 있습니다.\n\n이미 주택을 보유한 사람에게는 해당사항이 없습니다.\n\n다만 아직 검토 단계로 확정된 정책이 아닙니다.",
      evidenceStatus: "proposed",
      uncertaintyNote: "아직 검토 단계로 확정된 정책이 아닙니다.",
      economicTerms: [
        {
          term: "특별공급",
          explanation: "일정 자격을 갖춘 무주택자에게 일반 청약과 별도로 아파트를 분양받을 기회를 주는 제도입니다.",
          example: "신혼부부, 다자녀, 생애최초 등 여러 유형이 있습니다.",
        },
      ],
      sources: [
        { articleId: "art-policy-001", isPrimary: true },
      ],
    },
  ],
  glossary: [
    {
      term: "기준금리",
      explanation: "한국은행이 정하는 금리로, 은행들이 서로 돈을 빌려줄 때 기준이 되는 금리입니다.",
      example: "기준금리가 3.00%이면 은행의 주택담보대출 금리는 보통 이보다 1~2%포인트 높은 4~5% 수준입니다.",
    },
    {
      term: "특별공급",
      explanation: "일정 자격을 갖춘 무주택자에게 일반 청약과 별도로 아파트를 분양받을 기회를 주는 제도입니다.",
      example: "신혼부부, 다자녀, 생애최초 등 여러 유형이 있습니다.",
    },
  ],
};

export const invalidJSONResponse = "이것은 JSON이 아닙니다.";

export const missingFieldsResponse = JSON.stringify({
  overallSummary: ["요약"],
  news: [
    {
      id: "news-1",
      representativeTitle: "제목",
    },
  ],
  glossary: [],
});

export const emptyNewsResponse = JSON.stringify({
  overallSummary: ["요약"],
  news: [],
  glossary: [],
});

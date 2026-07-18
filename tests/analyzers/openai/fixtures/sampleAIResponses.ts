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
      relevanceReason: "대출금리와 예금금리에 직접 영향을 주는 핵심 경제 뉴스입니다.",
      oneLineSummary: "한국은행이 기준금리를 연 3.25%에서 3.00%로 인하했습니다.",
      explanation: "한국은행 금융통화위원회가 기준금리를 연 3.25%에서 3.00%로 0.25%포인트 인하했습니다. 경기 둔화 우려와 물가 안정세를 고려한 판단인데요, 그동안 기준금리가 3.25%로 유지되면서 시중은행의 대출금리와 예금금리도 이에 연동되어 움직이고 있었습니다.\n\n이번 인하로 은행들의 대출금리와 예금금리도 점진적으로 조정될 것으로 보입니다. 변동금리 대출을 가진 가계는 이자 부담이 줄어들 수 있고, 반면 예금이나 적금에 돈을 넣어둔 경우 이자 수입이 줄어들 수 있습니다.\n\n주택담보대출을 준비하는 신혼부부에게는 유리할 수 있습니다. 대출금리가 낮아지면 같은 금액을 빌려도 매달 갚는 이자가 줄어들기 때문입니다.",
      expectedNextEffects: [
        "시중은행 대출금리가 점진적으로 내려갈 가능성이 있습니다.",
        "정기예금 금리도 함께 내려갈 수 있습니다.",
      ],
      recommendedChecks: [
        "변동금리 대출이 있다면 다음 금리 변경일을 확인하세요.",
        "예금 만기가 다가오면 금리 비교 후 갱신 여부를 검토하세요.",
      ],
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
      relevanceReason: "주택 구입을 준비하는 가정에 직접적인 영향이 있습니다.",
      oneLineSummary: "서울 아파트 매매가격이 3주 연속 상승하며 거래량이 증가하고 있습니다.",
      explanation: "서울 아파트 매매가격이 3주 연속 상승세를 보이며 거래량도 함께 증가하고 있습니다. 금리 인하 기대와 규제 완화 기조가 맞물리면서 시장 심리가 개선된 영향입니다.\n\n그동안 금리 인상 영향으로 부동산 거래가 위축되어 있었는데, 금리 인하 기대감 등으로 매수세가 살아나며 가격이 상승하고 있습니다.\n\n주택 보유자에게는 자산 가치 상승이지만, 매수 예정자에게는 부담이 커질 수 있습니다. 주택 구입을 계획하는 신혼부부에게는 매입 시기에 대한 고민이 필요한 상황입니다.",
      expectedNextEffects: [
        "기준금리 추가 인하 시 가격 상승세가 지속될 가능성이 있습니다.",
      ],
      recommendedChecks: [
        "관심 지역의 실거래가 추이를 확인해 보세요.",
      ],
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
      relevanceReason: "신혼부부의 주택 마련 기회에 직접적인 영향을 주는 정책입니다.",
      oneLineSummary: "국토교통부가 신혼부부 특별공급의 소득 기준 완화를 검토하고 있습니다.",
      explanation: "국토교통부가 신혼부부 특별공급의 소득 기준을 완화하는 방안을 검토 중입니다. 저출산 대응과 주거 안정 정책의 일환으로 추진되고 있는 건데요, 아직 검토 단계로 확정된 정책은 아닙니다.\n\n기존 소득 기준으로는 맞벌이 신혼부부 중 상당수가 특별공급 대상에서 제외되었습니다. 소득 기준이 완화되면 더 많은 신혼부부가 특별공급에 지원할 수 있게 됩니다.\n\n신혼부부 가정의 주택 마련 기회가 확대될 수 있고, 특히 맞벌이 신혼부부에게 유리한 변화가 될 수 있습니다. 기존에 소득 기준으로 탈락했다면 재도전이 가능해질 수 있습니다.",
      expectedNextEffects: [
        "구체적인 소득 기준 변경안이 발표될 가능성이 있습니다.",
        "특별공급 경쟁률이 높아질 수 있습니다.",
      ],
      recommendedChecks: [
        "현재 신혼부부 특별공급 소득 기준과 자신의 소득을 확인해 보세요.",
      ],
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

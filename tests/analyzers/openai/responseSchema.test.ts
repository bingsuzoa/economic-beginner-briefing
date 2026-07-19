import { describe, it, expect } from "vitest";
import { AIResponseSchema } from "../../../src/analyzers/openai/prompts/responseSchema.js";

describe("AIResponseSchema", () => {
  const validResponse = {
    overallSummary: ["오늘의 주요 경제 뉴스입니다."],
    news: [
      {
        id: "news-1",
        representativeTitle: "기준금리 인하",
        category: "interest_rate",
        importance: 5,
        whyImportant: "변동금리 대출을 보유한 가계의 이자 부담이 직접적으로 줄어들기 때문입니다.",
        targetAudience: {
          mustRead: ["변동금리 대출자", "주택담보대출 예정자", "신혼부부"],
          notRelevant: ["고정금리 대출자"],
        },
        oneLineSummary: "한국은행이 기준금리를 인하했습니다.",
        explanation: "[무슨 일이 있었나]\n\n한국은행이 기준금리를 연 3.25%에서 3.00%로 인하했습니다. 기존에는 3.25%로 유지되고 있었는데, 이번에 3.00%로 낮아졌습니다.\n\n[왜 이런 일이 발생했나]\n\n경기 둔화 우려와 물가 안정세를 고려한 판단입니다.\n\n[우리에게 어떤 의미가 있나]\n\n변동금리 대출을 가진 가계는 이자 부담이 줄어들 수 있습니다. 주택담보대출을 준비하는 신혼부부에게도 유리할 수 있습니다.\n\n고정금리 대출자에게는 직접적인 영향이 거의 없습니다.",
        evidenceStatus: "confirmed",
        economicTerms: [
          { term: "기준금리", explanation: "중앙은행이 정하는 기본 금리" },
        ],
        sources: [{ articleId: "art-1", isPrimary: true }],
      },
    ],
    glossary: [
      { term: "기준금리", explanation: "중앙은행이 정하는 기본 금리" },
    ],
  };

  it("validates a correct AI response", () => {
    const result = AIResponseSchema.safeParse(validResponse);
    expect(result.success).toBe(true);
  });

  it("rejects response with empty news array", () => {
    const result = AIResponseSchema.safeParse({
      ...validResponse,
      news: [],
    });
    expect(result.success).toBe(false);
  });

  it("rejects response with missing required fields", () => {
    const result = AIResponseSchema.safeParse({
      overallSummary: ["test"],
      news: [{ id: "x" }],
      glossary: [],
    });
    expect(result.success).toBe(false);
  });

  it("rejects invalid category", () => {
    const invalidNews = {
      ...validResponse.news[0],
      category: "invalid_category",
    };
    const result = AIResponseSchema.safeParse({
      ...validResponse,
      news: [invalidNews],
    });
    expect(result.success).toBe(false);
  });

  it("rejects invalid importance value", () => {
    const invalidNews = {
      ...validResponse.news[0],
      importance: 6,
    };
    const result = AIResponseSchema.safeParse({
      ...validResponse,
      news: [invalidNews],
    });
    expect(result.success).toBe(false);
  });

  it("rejects invalid evidence status", () => {
    const invalidNews = {
      ...validResponse.news[0],
      evidenceStatus: "unknown",
    };
    const result = AIResponseSchema.safeParse({
      ...validResponse,
      news: [invalidNews],
    });
    expect(result.success).toBe(false);
  });

  it("accepts optional uncertaintyNote", () => {
    const newsWithNote = {
      ...validResponse.news[0],
      uncertaintyNote: "아직 확정되지 않았습니다.",
    };
    const result = AIResponseSchema.safeParse({
      ...validResponse,
      news: [newsWithNote],
    });
    expect(result.success).toBe(true);
  });

  it("accepts economic term with optional example", () => {
    const newsWithExample = {
      ...validResponse.news[0],
      economicTerms: [
        {
          term: "기준금리",
          explanation: "중앙은행이 정하는 기본 금리",
          example: "현재 3.00%",
        },
      ],
    };
    const result = AIResponseSchema.safeParse({
      ...validResponse,
      news: [newsWithExample],
    });
    expect(result.success).toBe(true);
  });

  it("accepts missing targetAudience (optional)", () => {
    const newsWithoutAudience = {
      ...validResponse.news[0],
      targetAudience: undefined,
    };
    const result = AIResponseSchema.safeParse({
      ...validResponse,
      news: [newsWithoutAudience],
    });
    expect(result.success).toBe(true);
  });

  it("accepts targetAudience with empty notRelevant", () => {
    const newsWithEmptyNotRelevant = {
      ...validResponse.news[0],
      targetAudience: {
        mustRead: ["신혼부부"],
        notRelevant: [],
      },
    };
    const result = AIResponseSchema.safeParse({
      ...validResponse,
      news: [newsWithEmptyNotRelevant],
    });
    expect(result.success).toBe(true);
  });
});

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
        relevanceReason: "가계 대출에 직접 영향",
        oneLineSummary: "한국은행이 기준금리를 인하했습니다.",
        explanation: "한국은행이 기준금리를 연 3.25%에서 3.00%로 인하했습니다. 경기 둔화 우려와 물가 안정세를 고려한 판단입니다.\n\n기존에는 3.25%로 유지되고 있었는데, 이번에 3.00%로 낮아졌습니다. 변동금리 대출을 가진 가계는 이자 부담이 줄어들 수 있고, 주택담보대출을 준비하는 신혼부부에게도 유리할 수 있습니다.",
        expectedNextEffects: ["추가 인하 가능성"],
        recommendedChecks: ["대출 금리 확인"],
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
});

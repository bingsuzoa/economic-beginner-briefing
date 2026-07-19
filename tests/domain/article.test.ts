import { describe, it, expect } from "vitest";
import { ArticleSchema } from "../../src/domain/article.js";
import { AnalyzedNewsSchema } from "../../src/domain/analyzedNews.js";

function validArticle() {
  return {
    id: "test-001",
    title: "테스트 기사 제목",
    summary: "테스트 요약",
    sourceName: "테스트 언론사",
    sourceType: "news_media" as const,
    publishedAt: "2026-07-16T10:00:00+09:00",
    collectedAt: "2026-07-16T23:00:00+09:00",
    url: "https://example.com/article/1",
    categories: ["interest_rate" as const],
    language: "ko" as const,
  };
}

describe("ArticleSchema", () => {
  it("should validate a correct article", () => {
    const result = ArticleSchema.safeParse(validArticle());
    expect(result.success).toBe(true);
  });

  it("should reject empty title", () => {
    const article = { ...validArticle(), title: "" };
    const result = ArticleSchema.safeParse(article);
    expect(result.success).toBe(false);
  });

  it("should reject invalid URL", () => {
    const article = { ...validArticle(), url: "not-a-url" };
    const result = ArticleSchema.safeParse(article);
    expect(result.success).toBe(false);
  });

  it("should reject invalid date format", () => {
    const article = { ...validArticle(), publishedAt: "2026/07/16" };
    const result = ArticleSchema.safeParse(article);
    expect(result.success).toBe(false);
  });

  it("should accept article with optional content", () => {
    const article = { ...validArticle(), content: "Full article text..." };
    const result = ArticleSchema.safeParse(article);
    expect(result.success).toBe(true);
  });

  it("should accept article without content", () => {
    const article = validArticle();
    const result = ArticleSchema.safeParse(article);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.content).toBeUndefined();
    }
  });
});

describe("AnalyzedNewsSchema", () => {
  function validAnalyzedNews() {
    return {
      id: "analyzed-001",
      representativeTitle: "기준금리 인하",
      category: "interest_rate" as const,
      importance: 5 as const,
      whyImportant: "변동금리 대출자의 이자 부담이 직접적으로 줄어들기 때문입니다.",
      targetAudience: {
        mustRead: ["변동금리 대출자", "신혼부부"],
        notRelevant: ["고정금리 대출자"],
      },
      oneLineSummary: "기준금리 인하 결정",
      explanation: "한국은행이 기준금리를 연 3.25%에서 3.00%로 내렸습니다. 경기 둔화 우려 때문인데요, 대출 이자가 줄어들 수 있고 주택담보대출 금리도 낮아질 수 있습니다.",
      evidenceStatus: "confirmed" as const,
      economicTerms: [
        { term: "기준금리", explanation: "한국은행이 정하는 기준이 되는 금리" },
      ],
      sources: [
        {
          articleId: "test-001",
          sourceName: "한국은행",
          title: "기준금리 결정",
          url: "https://example.com/bok",
          publishedAt: "2026-07-16T10:00:00+09:00",
          isPrimary: true,
        },
      ],
    };
  }

  it("should validate importance in range 1-5", () => {
    for (const importance of [1, 2, 3, 4, 5] as const) {
      const news = { ...validAnalyzedNews(), importance };
      const result = AnalyzedNewsSchema.safeParse(news);
      expect(result.success).toBe(true);
    }
  });

  it("should reject importance outside range 1-5", () => {
    const news = { ...validAnalyzedNews(), importance: 0 };
    const result = AnalyzedNewsSchema.safeParse(news);
    expect(result.success).toBe(false);
  });

  it("should reject importance of 6", () => {
    const news = { ...validAnalyzedNews(), importance: 6 };
    const result = AnalyzedNewsSchema.safeParse(news);
    expect(result.success).toBe(false);
  });
});

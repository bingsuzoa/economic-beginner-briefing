import { describe, it, expect } from "vitest";
import { buildAnalysisPrompt } from "../../../src/analyzers/openai/prompts/buildAnalysisPrompt.js";
import type { Article } from "../../../src/domain/article.js";
import type { AudienceProfile } from "../../../src/domain/analyzedNews.js";
import { DEFAULT_AUDIENCE } from "../../../src/config/constants.js";

describe("buildAnalysisPrompt", () => {
  const sampleArticle: Article = {
    id: "art-1",
    title: "기준금리 인하",
    summary: "한국은행이 기준금리를 인하했습니다.",
    sourceName: "한국경제",
    sourceType: "news_media",
    publishedAt: "2026-07-16T10:00:00+09:00",
    collectedAt: "2026-07-16T11:00:00+09:00",
    url: "https://example.com/article/1",
    categories: ["interest_rate"],
    language: "ko",
  };

  const audience: AudienceProfile = DEFAULT_AUDIENCE;

  it("includes target date in prompt", () => {
    const result = buildAnalysisPrompt({
      articles: [sampleArticle],
      targetDate: "2026-07-16",
      maxSelectedNews: 10,
      audience,
    });
    expect(result).toContain("2026-07-16");
  });

  it("includes article details", () => {
    const result = buildAnalysisPrompt({
      articles: [sampleArticle],
      targetDate: "2026-07-16",
      maxSelectedNews: 10,
      audience,
    });
    expect(result).toContain("art-1");
    expect(result).toContain("기준금리 인하");
    expect(result).toContain("한국경제");
    expect(result).toContain("https://example.com/article/1");
  });

  it("numbers articles sequentially", () => {
    const articles: Article[] = [
      sampleArticle,
      { ...sampleArticle, id: "art-2", title: "환율 변동" },
    ];
    const result = buildAnalysisPrompt({
      articles,
      targetDate: "2026-07-16",
      maxSelectedNews: 10,
      audience,
    });
    expect(result).toContain("기사 1");
    expect(result).toContain("기사 2");
  });

  it("includes audience profile", () => {
    const result = buildAnalysisPrompt({
      articles: [sampleArticle],
      targetDate: "2026-07-16",
      maxSelectedNews: 10,
      audience,
    });
    expect(result).toContain("초보자");
    expect(result).toContain("신혼부부");
  });

  it("includes maxSelectedNews in instructions", () => {
    const result = buildAnalysisPrompt({
      articles: [sampleArticle],
      targetDate: "2026-07-16",
      maxSelectedNews: 5,
      audience,
    });
    expect(result).toContain("최대 5개");
  });

  it("includes article content when present", () => {
    const articleWithContent: Article = {
      ...sampleArticle,
      content: "한국은행이 기준금리를 0.25%포인트 인하했다.",
    };
    const result = buildAnalysisPrompt({
      articles: [articleWithContent],
      targetDate: "2026-07-16",
      maxSelectedNews: 10,
      audience,
    });
    expect(result).toContain("본문:");
    expect(result).toContain("0.25%포인트 인하");
  });

  it("shows total article count", () => {
    const articles: Article[] = Array.from({ length: 3 }, (_, i) => ({
      ...sampleArticle,
      id: `art-${i + 1}`,
    }));
    const result = buildAnalysisPrompt({
      articles,
      targetDate: "2026-07-16",
      maxSelectedNews: 10,
      audience,
    });
    expect(result).toContain("전체 기사 수: 3");
  });
});

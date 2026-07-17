import { describe, it, expect } from "vitest";
import { buildBriefingFromAIResponse } from "../../../src/analyzers/openai/utils/buildBriefingFromAIResponse.js";
import { BriefingSchema } from "../../../src/domain/briefing.js";
import { sampleArticles } from "./fixtures/sampleArticles.js";
import { validAIResponse } from "./fixtures/sampleAIResponses.js";

describe("buildBriefingFromAIResponse", () => {
  it("creates a valid Briefing from AI response", () => {
    const briefing = buildBriefingFromAIResponse({
      aiResponse: validAIResponse,
      targetDate: "2026-07-16",
      articles: sampleArticles,
      modelName: "gpt-4o",
      promptVersion: "v1",
    });

    const result = BriefingSchema.safeParse(briefing);
    expect(result.success).toBe(true);
  });

  it("sets correct briefing ID and title", () => {
    const briefing = buildBriefingFromAIResponse({
      aiResponse: validAIResponse,
      targetDate: "2026-07-16",
      articles: sampleArticles,
      modelName: "gpt-4o",
      promptVersion: "v1",
    });

    expect(briefing.id).toBe("briefing-2026-07-16");
    expect(briefing.title).toBe("2026-07-16 경제 브리핑");
    expect(briefing.targetDate).toBe("2026-07-16");
  });

  it("maps overallSummary from AI response", () => {
    const briefing = buildBriefingFromAIResponse({
      aiResponse: validAIResponse,
      targetDate: "2026-07-16",
      articles: sampleArticles,
      modelName: "gpt-4o",
      promptVersion: "v1",
    });

    expect(briefing.overallSummary).toEqual(validAIResponse.overallSummary);
  });

  it("enriches source references with article data", () => {
    const briefing = buildBriefingFromAIResponse({
      aiResponse: validAIResponse,
      targetDate: "2026-07-16",
      articles: sampleArticles,
      modelName: "gpt-4o",
      promptVersion: "v1",
    });

    const firstNews = briefing.news[0]!;
    const primarySource = firstNews.sources[0]!;
    expect(primarySource.sourceName).toBe("한국경제");
    expect(primarySource.title).toBe("한국은행 기준금리 0.25%p 인하…연 3.00%로");
    expect(primarySource.url).toBe("https://example.com/interest-rate-cut");
    expect(primarySource.isPrimary).toBe(true);
  });

  it("handles unknown articleId in sources gracefully", () => {
    const responseWithUnknownSource = {
      ...validAIResponse,
      news: [
        {
          ...validAIResponse.news[0]!,
          sources: [{ articleId: "unknown-id", isPrimary: true }],
        },
      ],
    };

    const briefing = buildBriefingFromAIResponse({
      aiResponse: responseWithUnknownSource,
      targetDate: "2026-07-16",
      articles: sampleArticles,
      modelName: "gpt-4o",
      promptVersion: "v1",
    });

    const source = briefing.news[0]!.sources[0]!;
    expect(source.sourceName).toBe("Unknown");
  });

  it("sets correct metadata", () => {
    const briefing = buildBriefingFromAIResponse({
      aiResponse: validAIResponse,
      targetDate: "2026-07-16",
      articles: sampleArticles,
      modelName: "gpt-4o",
      promptVersion: "v1",
    });

    expect(briefing.metadata.collectedArticleCount).toBe(sampleArticles.length);
    expect(briefing.metadata.selectedNewsCount).toBe(validAIResponse.news.length);
    expect(briefing.metadata.modelName).toBe("gpt-4o");
    expect(briefing.metadata.promptVersion).toBe("v1");
  });

  it("maps glossary from AI response", () => {
    const briefing = buildBriefingFromAIResponse({
      aiResponse: validAIResponse,
      targetDate: "2026-07-16",
      articles: sampleArticles,
      modelName: "gpt-4o",
      promptVersion: "v1",
    });

    expect(briefing.glossary).toEqual(validAIResponse.glossary);
  });
});

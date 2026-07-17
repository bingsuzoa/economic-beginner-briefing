import { describe, it, expect } from "vitest";
import { MockNewsAnalyzer } from "../../src/analyzers/mock/MockNewsAnalyzer.js";
import { MockNewsCollector } from "../../src/collectors/mock/MockNewsCollector.js";
import { BriefingSchema } from "../../src/domain/briefing.js";
import { DEFAULT_AUDIENCE } from "../../src/config/constants.js";

describe("MockNewsAnalyzer", () => {
  it("should produce a valid briefing from collected articles", async () => {
    const collector = new MockNewsCollector();
    const collectResult = await collector.collect({
      targetDate: "2026-07-16",
      timezone: "Asia/Seoul",
    });

    const analyzer = new MockNewsAnalyzer();
    const analyzeResult = await analyzer.analyze({
      targetDate: "2026-07-16",
      articles: collectResult.articles,
      maxSelectedNews: 10,
      audience: DEFAULT_AUDIENCE,
    });

    expect(analyzeResult.briefing).toBeDefined();
    expect(analyzeResult.briefing.news.length).toBeGreaterThan(0);
    expect(analyzeResult.warnings).toEqual([]);
  });

  it("should return a briefing conforming to BriefingSchema", async () => {
    const collector = new MockNewsCollector();
    const collectResult = await collector.collect({
      targetDate: "2026-07-16",
      timezone: "Asia/Seoul",
    });

    const analyzer = new MockNewsAnalyzer();
    const analyzeResult = await analyzer.analyze({
      targetDate: "2026-07-16",
      articles: collectResult.articles,
      maxSelectedNews: 10,
      audience: DEFAULT_AUDIENCE,
    });

    const validation = BriefingSchema.safeParse(analyzeResult.briefing);
    expect(validation.success).toBe(true);
  });

  it("should include economic terms in analyzed news", async () => {
    const collector = new MockNewsCollector();
    const collectResult = await collector.collect({
      targetDate: "2026-07-16",
      timezone: "Asia/Seoul",
    });

    const analyzer = new MockNewsAnalyzer();
    const analyzeResult = await analyzer.analyze({
      targetDate: "2026-07-16",
      articles: collectResult.articles,
      maxSelectedNews: 10,
      audience: DEFAULT_AUDIENCE,
    });

    const allTerms = analyzeResult.briefing.news.flatMap(
      (n) => n.economicTerms,
    );
    expect(allTerms.length).toBeGreaterThan(0);
  });

  it("should respect maxSelectedNews limit", async () => {
    const collector = new MockNewsCollector();
    const collectResult = await collector.collect({
      targetDate: "2026-07-16",
      timezone: "Asia/Seoul",
    });

    const analyzer = new MockNewsAnalyzer();
    const analyzeResult = await analyzer.analyze({
      targetDate: "2026-07-16",
      articles: collectResult.articles,
      maxSelectedNews: 2,
      audience: DEFAULT_AUDIENCE,
    });

    expect(analyzeResult.briefing.news.length).toBeLessThanOrEqual(2);
  });
});

import { describe, it, expect } from "vitest";
import { MockNewsCollector } from "../../src/collectors/mock/MockNewsCollector.js";
import { ArticleSchema, CollectNewsResultSchema } from "../../src/domain/article.js";

describe("MockNewsCollector", () => {
  it("should return articles for the given target date", async () => {
    const collector = new MockNewsCollector();
    const result = await collector.collect({
      targetDate: "2026-07-16",
      timezone: "Asia/Seoul",
    });

    expect(result.targetDate).toBe("2026-07-16");
    expect(result.articles.length).toBeGreaterThanOrEqual(3);
    expect(result.totalAccepted).toBe(result.articles.length);
  });

  it("should return valid articles conforming to ArticleSchema", async () => {
    const collector = new MockNewsCollector();
    const result = await collector.collect({
      targetDate: "2026-07-16",
      timezone: "Asia/Seoul",
    });

    for (const article of result.articles) {
      const validation = ArticleSchema.safeParse(article);
      expect(validation.success).toBe(true);
    }
  });

  it("should return valid CollectNewsResult", async () => {
    const collector = new MockNewsCollector();
    const result = await collector.collect({
      targetDate: "2026-07-16",
      timezone: "Asia/Seoul",
    });

    const validation = CollectNewsResultSchema.safeParse(result);
    expect(validation.success).toBe(true);
  });

  it("should include source reports for each source", async () => {
    const collector = new MockNewsCollector();
    const result = await collector.collect({
      targetDate: "2026-07-16",
      timezone: "Asia/Seoul",
    });

    expect(result.sourceReports.length).toBeGreaterThan(0);
    for (const report of result.sourceReports) {
      expect(report.status).toBe("success");
    }
  });
});

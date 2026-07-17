import { describe, it, expect } from "vitest";
import { MockBriefingPublisher } from "../../src/publishers/mock/MockBriefingPublisher.js";
import { MockNewsCollector } from "../../src/collectors/mock/MockNewsCollector.js";
import { MockNewsAnalyzer } from "../../src/analyzers/mock/MockNewsAnalyzer.js";
import { DEFAULT_AUDIENCE } from "../../src/config/constants.js";

async function createTestBriefing() {
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
  return analyzeResult.briefing;
}

describe("MockBriefingPublisher", () => {
  it("should publish successfully when dryRun is false", async () => {
    const publisher = new MockBriefingPublisher();
    const briefing = await createTestBriefing();

    const result = await publisher.publish({ briefing, dryRun: false });

    expect(result.briefingId).toBe(briefing.id);
    expect(result.results[0]?.status).toBe("success");
    expect(result.results[0]?.channel).toBe("mock");
  });

  it("should skip when dryRun is true", async () => {
    const publisher = new MockBriefingPublisher();
    const briefing = await createTestBriefing();

    const result = await publisher.publish({ briefing, dryRun: true });

    expect(result.results[0]?.status).toBe("skipped");
  });

  it("should store published briefings", async () => {
    const publisher = new MockBriefingPublisher();
    const briefing = await createTestBriefing();

    await publisher.publish({ briefing, dryRun: false });

    expect(publisher.getPublishedBriefings()).toHaveLength(1);
    expect(publisher.getPublishedBriefings()[0]?.id).toBe(briefing.id);
  });

  it("should handle partial failure in results representation", async () => {
    // The mock publisher always succeeds, but we verify the result structure
    // supports the concept of partial failure via PublishChannelResult
    const publisher = new MockBriefingPublisher();
    const briefing = await createTestBriefing();

    const result = await publisher.publish({ briefing, dryRun: false });

    // Verify the result structure can represent channel-level status
    for (const channelResult of result.results) {
      expect(["success", "skipped", "failed"]).toContain(channelResult.status);
      expect(["email", "notion", "mock"]).toContain(channelResult.channel);
    }
  });
});

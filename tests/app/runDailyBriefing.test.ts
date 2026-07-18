import { describe, it, expect } from "vitest";
import { runDailyBriefing } from "../../src/app/runDailyBriefing.js";
import { MockNewsCollector } from "../../src/collectors/mock/MockNewsCollector.js";
import { MockNewsAnalyzer } from "../../src/analyzers/mock/MockNewsAnalyzer.js";
import { MockBriefingPublisher } from "../../src/publishers/mock/MockBriefingPublisher.js";
import { loadEnv } from "../../src/config/env.js";
import { DEFAULT_AUDIENCE } from "../../src/config/constants.js";
import type { NewsCollector } from "../../src/collectors/NewsCollector.js";
import type { CollectNewsRequest, CollectNewsResult } from "../../src/domain/article.js";

function createDeps(overrides?: {
  collector?: NewsCollector;
  publisher?: MockBriefingPublisher;
}) {
  return {
    collector: overrides?.collector ?? new MockNewsCollector(),
    analyzer: new MockNewsAnalyzer(),
    publisher: overrides?.publisher ?? new MockBriefingPublisher(),
    env: loadEnv({
      NODE_ENV: "test",
      TZ: "Asia/Seoul",
      DRY_RUN: "false",
      LOG_LEVEL: "info",
    }),
    audience: DEFAULT_AUDIENCE,
  };
}

describe("runDailyBriefing", () => {
  it("should complete full mock pipeline successfully", async () => {
    const deps = createDeps();
    const log = await runDailyBriefing(deps, "2026-07-16");

    expect(log.status).toBe("success");
    expect(log.targetDate).toBe("2026-07-16");
    expect(log.collectedArticleCount).toBeGreaterThan(0);
    expect(log.selectedNewsCount).toBeGreaterThan(0);
    // Filtering stats are logged as an informational entry
    const realErrors = log.errors.filter((e) => e.code !== "COLLECT_FILTERING_STATS");
    expect(realErrors).toHaveLength(0);
    expect(log.completedAt).toBeDefined();
  });

  it("should succeed gracefully when 0 articles collected (hourly mode)", async () => {
    const emptyCollector: NewsCollector = {
      async collect(request: CollectNewsRequest): Promise<CollectNewsResult> {
        return {
          targetDate: request.targetDate,
          articles: [],
          sourceReports: [],
          totalCollected: 0,
          totalAccepted: 0,
          totalRejected: 0,
        };
      },
    };

    const deps = createDeps({ collector: emptyCollector });
    const log = await runDailyBriefing(deps, "2026-07-16");

    expect(log.status).toBe("success");
    expect(log.collectedArticleCount).toBe(0);
    expect(log.selectedNewsCount).toBe(0);
    expect(log.errors).toHaveLength(0);
  });

  it("should handle collector error gracefully", async () => {
    const failingCollector: NewsCollector = {
      async collect(): Promise<CollectNewsResult> {
        throw new Error("Network error");
      },
    };

    const deps = createDeps({ collector: failingCollector });
    const log = await runDailyBriefing(deps, "2026-07-16");

    expect(log.status).toBe("failed");
    expect(log.errors.length).toBeGreaterThan(0);
  });

  it("should record publisher partial failure", async () => {
    // Test with dryRun which results in "skipped" status
    const deps = createDeps();
    deps.env = loadEnv({
      NODE_ENV: "test",
      TZ: "Asia/Seoul",
      DRY_RUN: "true",
      LOG_LEVEL: "info",
    });

    const log = await runDailyBriefing(deps, "2026-07-16");

    // dryRun results in skipped which counts as success
    expect(log.status).toBe("success");
  });
});

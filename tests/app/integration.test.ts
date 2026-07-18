import { describe, it, expect } from "vitest";
import { runDailyBriefing } from "../../src/app/runDailyBriefing.js";
import { MockNewsCollector } from "../../src/collectors/mock/MockNewsCollector.js";
import { MockNewsAnalyzer } from "../../src/analyzers/mock/MockNewsAnalyzer.js";
import { MockBriefingPublisher } from "../../src/publishers/mock/MockBriefingPublisher.js";
import { MockExecutionTracker } from "../../src/app/ExecutionTracker.js";
import { loadEnv } from "../../src/config/env.js";
import { DEFAULT_AUDIENCE } from "../../src/config/constants.js";
import { ExecutionLogSchema } from "../../src/domain/execution.js";
import { BriefingSchema } from "../../src/domain/briefing.js";
import type { NewsCollector } from "../../src/collectors/NewsCollector.js";
import type { NewsAnalyzer } from "../../src/analyzers/NewsAnalyzer.js";
import type { BriefingPublisher } from "../../src/publishers/BriefingPublisher.js";
import type { CollectNewsRequest, CollectNewsResult } from "../../src/domain/article.js";
import type { AnalyzeNewsRequest, AnalyzeNewsResult } from "../../src/domain/analyzedNews.js";
import type {
  PublishBriefingRequest,
  PublishBriefingResult,
} from "../../src/domain/briefing.js";
import type { ApplicationDeps } from "../../src/app/createApplication.js";
import { nowISOStringKST } from "../../src/utils/date.js";

const TARGET_DATE = "2026-07-16";

function createTestEnv() {
  return loadEnv({
    NODE_ENV: "test",
    TZ: "Asia/Seoul",
    DRY_RUN: "false",
    LOG_LEVEL: "info",
  });
}

function createDeps(overrides?: Partial<ApplicationDeps>): ApplicationDeps {
  return {
    collector: overrides?.collector ?? new MockNewsCollector(),
    analyzer: overrides?.analyzer ?? new MockNewsAnalyzer(),
    publisher: overrides?.publisher ?? new MockBriefingPublisher(),
    env: overrides?.env ?? createTestEnv(),
    audience: overrides?.audience ?? DEFAULT_AUDIENCE,
    executionTracker: overrides?.executionTracker,
  };
}

describe("Integration: Full Pipeline", () => {
  it("should complete full pipeline successfully with mocks", async () => {
    const tracker = new MockExecutionTracker();
    const deps = createDeps({ executionTracker: tracker });
    const log = await runDailyBriefing(deps, TARGET_DATE);

    expect(log.status).toBe("success");
    expect(log.targetDate).toBe(TARGET_DATE);
    expect(log.collectedArticleCount).toBeGreaterThan(0);
    expect(log.selectedNewsCount).toBeGreaterThan(0);
    expect(log.errors).toHaveLength(0);
    expect(log.completedAt).toBeDefined();
    expect(log.executionId).toBeDefined();
    expect(log.startedAt).toBeDefined();

    // Execution should be recorded
    const recorded = await tracker.getLastExecution(TARGET_DATE);
    expect(recorded).not.toBeNull();
    expect(recorded?.status).toBe("success");
  });

  it("should pass collector output to analyzer input correctly", async () => {
    let capturedArticles: unknown[] = [];
    const spyAnalyzer: NewsAnalyzer = {
      async analyze(request: AnalyzeNewsRequest): Promise<AnalyzeNewsResult> {
        capturedArticles = request.articles;
        const realAnalyzer = new MockNewsAnalyzer();
        return realAnalyzer.analyze(request);
      },
    };

    const collector = new MockNewsCollector();
    const deps = createDeps({ analyzer: spyAnalyzer, collector });
    await runDailyBriefing(deps, TARGET_DATE);

    // Collector produces 3 articles, all should reach analyzer
    expect(capturedArticles).toHaveLength(3);
  });

  it("should pass analyzer briefing to publisher correctly", async () => {
    let capturedBriefing: unknown = null;
    const spyPublisher: BriefingPublisher = {
      async publish(request: PublishBriefingRequest): Promise<PublishBriefingResult> {
        capturedBriefing = request.briefing;
        return {
          briefingId: request.briefing.id,
          results: [{ channel: "mock", status: "success" }],
          completedAt: nowISOStringKST(),
        };
      },
    };

    const deps = createDeps({ publisher: spyPublisher });
    await runDailyBriefing(deps, TARGET_DATE);

    expect(capturedBriefing).not.toBeNull();
    const parsed = BriefingSchema.safeParse(capturedBriefing);
    expect(parsed.success).toBe(true);
  });
});

describe("Integration: Collection Failure", () => {
  it("should not run analyzer when collector throws", async () => {
    let analyzerCalled = false;
    const failingCollector: NewsCollector = {
      async collect(): Promise<CollectNewsResult> {
        throw new Error("Network error");
      },
    };
    const spyAnalyzer: NewsAnalyzer = {
      async analyze(request: AnalyzeNewsRequest): Promise<AnalyzeNewsResult> {
        analyzerCalled = true;
        return new MockNewsAnalyzer().analyze(request);
      },
    };

    const deps = createDeps({ collector: failingCollector, analyzer: spyAnalyzer });
    const log = await runDailyBriefing(deps, TARGET_DATE);

    expect(log.status).toBe("failed");
    expect(analyzerCalled).toBe(false);
    expect(log.errors.length).toBeGreaterThan(0);
  });

  it("should succeed gracefully when collector returns 0 articles", async () => {
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
    const log = await runDailyBriefing(deps, TARGET_DATE);

    expect(log.status).toBe("success");
    expect(log.collectedArticleCount).toBe(0);
    expect(log.selectedNewsCount).toBe(0);
    expect(log.errors).toHaveLength(0);
  });
});

describe("Integration: Analysis Failure", () => {
  it("should not run publisher when analyzer throws", async () => {
    let publisherCalled = false;
    const failingAnalyzer: NewsAnalyzer = {
      async analyze(): Promise<AnalyzeNewsResult> {
        throw new Error("AI API error");
      },
    };
    const spyPublisher: BriefingPublisher = {
      async publish(request: PublishBriefingRequest): Promise<PublishBriefingResult> {
        publisherCalled = true;
        return {
          briefingId: request.briefing.id,
          results: [{ channel: "mock", status: "success" }],
          completedAt: nowISOStringKST(),
        };
      },
    };

    const tracker = new MockExecutionTracker();
    const deps = createDeps({
      analyzer: failingAnalyzer,
      publisher: spyPublisher,
      executionTracker: tracker,
    });
    const log = await runDailyBriefing(deps, TARGET_DATE);

    expect(log.status).toBe("failed");
    expect(publisherCalled).toBe(false);
    expect(log.errors.length).toBeGreaterThan(0);

    // Should record failed execution
    const recorded = await tracker.getLastExecution(TARGET_DATE);
    expect(recorded?.status).toBe("failed");
  });

  it("should fail when briefing has 0 news items", async () => {
    const emptyAnalyzer: NewsAnalyzer = {
      async analyze(request: AnalyzeNewsRequest): Promise<AnalyzeNewsResult> {
        return {
          briefing: {
            id: "briefing-empty",
            targetDate: request.targetDate,
            generatedAt: nowISOStringKST(),
            title: "Empty Briefing",
            overallSummary: [],
            news: [],
            glossary: [],
            metadata: {
              collectedArticleCount: request.articles.length,
              analyzedArticleCount: 0,
              selectedNewsCount: 0,
            },
          },
          rejectedArticleIds: request.articles.map((a) => a.id),
          warnings: [],
        };
      },
    };

    const deps = createDeps({ analyzer: emptyAnalyzer });
    const log = await runDailyBriefing(deps, TARGET_DATE);

    expect(log.status).toBe("failed");
    expect(log.selectedNewsCount).toBe(0);
    expect(log.errors.some((e) => e.code === "ANALYZE_EMPTY_INPUT")).toBe(true);
  });
});

describe("Integration: Publish Failure", () => {
  it("should return partial_success when some channels fail", async () => {
    const partialPublisher: BriefingPublisher = {
      async publish(request: PublishBriefingRequest): Promise<PublishBriefingResult> {
        return {
          briefingId: request.briefing.id,
          results: [
            { channel: "notion", status: "success", externalId: "notion-123" },
            {
              channel: "email",
              status: "failed",
              errorCode: "PUBLISH_CHANNEL_ERROR",
              errorMessage: "SMTP error",
            },
          ],
          completedAt: nowISOStringKST(),
        };
      },
    };

    const deps = createDeps({ publisher: partialPublisher });
    const log = await runDailyBriefing(deps, TARGET_DATE);

    expect(log.status).toBe("partial_success");
  });

  it("should return failed when all channels fail", async () => {
    const failPublisher: BriefingPublisher = {
      async publish(request: PublishBriefingRequest): Promise<PublishBriefingResult> {
        return {
          briefingId: request.briefing.id,
          results: [
            {
              channel: "notion",
              status: "failed",
              errorCode: "PUBLISH_CHANNEL_ERROR",
              errorMessage: "Notion API error",
            },
            {
              channel: "email",
              status: "failed",
              errorCode: "PUBLISH_CHANNEL_ERROR",
              errorMessage: "SMTP error",
            },
          ],
          completedAt: nowISOStringKST(),
        };
      },
    };

    const deps = createDeps({ publisher: failPublisher });
    const log = await runDailyBriefing(deps, TARGET_DATE);

    expect(log.status).toBe("failed");
  });
});

describe("Integration: Duplicate Execution", () => {
  it("should skip when already published for same date", async () => {
    const tracker = new MockExecutionTracker();
    const deps = createDeps({ executionTracker: tracker });

    // First run
    const firstLog = await runDailyBriefing(deps, TARGET_DATE);
    expect(firstLog.status).toBe("success");

    // Second run - should skip
    let analyzerCalled = false;
    const spyAnalyzer: NewsAnalyzer = {
      async analyze(request: AnalyzeNewsRequest): Promise<AnalyzeNewsResult> {
        analyzerCalled = true;
        return new MockNewsAnalyzer().analyze(request);
      },
    };

    const deps2 = createDeps({ executionTracker: tracker, analyzer: spyAnalyzer });
    const secondLog = await runDailyBriefing(deps2, TARGET_DATE);

    expect(secondLog.status).toBe("success");
    expect(secondLog.collectedArticleCount).toBe(0);
    expect(secondLog.selectedNewsCount).toBe(0);
    expect(secondLog.errors).toHaveLength(0);
    expect(analyzerCalled).toBe(false);
  });

  it("should retry when previous execution failed", async () => {
    const tracker = new MockExecutionTracker();

    // Record a failed execution
    await tracker.recordExecution({
      executionId: "exec-failed",
      targetDate: TARGET_DATE,
      startedAt: nowISOStringKST(),
      completedAt: nowISOStringKST(),
      status: "failed",
      collectedArticleCount: 0,
      selectedNewsCount: 0,
      errors: [{ stage: "collect", code: "COLLECT_NO_ARTICLES", message: "No articles", retryable: false }],
    });

    const deps = createDeps({ executionTracker: tracker });
    const log = await runDailyBriefing(deps, TARGET_DATE);

    // Should proceed normally (retry_previous_failure allows re-execution)
    expect(log.status).toBe("success");
    expect(log.collectedArticleCount).toBeGreaterThan(0);
  });
});

describe("Integration: ExecutionLog Structure", () => {
  it("should produce valid ExecutionLog per Zod schema", async () => {
    const deps = createDeps();
    const log = await runDailyBriefing(deps, TARGET_DATE);

    const parsed = ExecutionLogSchema.safeParse(log);
    expect(parsed.success).toBe(true);
  });

  it("should produce valid ExecutionLog on failure", async () => {
    const failingCollector: NewsCollector = {
      async collect(): Promise<CollectNewsResult> {
        throw new Error("Network error");
      },
    };

    const deps = createDeps({ collector: failingCollector });
    const log = await runDailyBriefing(deps, TARGET_DATE);

    const parsed = ExecutionLogSchema.safeParse(log);
    expect(parsed.success).toBe(true);
    expect(log.status).toBe("failed");
  });

  it("should include all required fields in ExecutionLog", async () => {
    const deps = createDeps();
    const log = await runDailyBriefing(deps, TARGET_DATE);

    expect(log).toHaveProperty("executionId");
    expect(log).toHaveProperty("targetDate");
    expect(log).toHaveProperty("startedAt");
    expect(log).toHaveProperty("completedAt");
    expect(log).toHaveProperty("status");
    expect(log).toHaveProperty("collectedArticleCount");
    expect(log).toHaveProperty("selectedNewsCount");
    expect(log).toHaveProperty("errors");
    expect(Array.isArray(log.errors)).toBe(true);
  });
});

describe("Integration: Foundation Contract Compliance", () => {
  it("should produce briefing conforming to BriefingSchema", async () => {
    let capturedBriefing: unknown = null;
    const spyPublisher: BriefingPublisher = {
      async publish(request: PublishBriefingRequest): Promise<PublishBriefingResult> {
        capturedBriefing = request.briefing;
        return {
          briefingId: request.briefing.id,
          results: [{ channel: "mock", status: "success" }],
          completedAt: nowISOStringKST(),
        };
      },
    };

    const deps = createDeps({ publisher: spyPublisher });
    await runDailyBriefing(deps, TARGET_DATE);

    expect(capturedBriefing).not.toBeNull();
    const parsed = BriefingSchema.safeParse(capturedBriefing);
    expect(parsed.success).toBe(true);
  });

  it("should include metadata fields in briefing", async () => {
    let capturedBriefing: Record<string, unknown> | null = null;
    const spyPublisher: BriefingPublisher = {
      async publish(request: PublishBriefingRequest): Promise<PublishBriefingResult> {
        capturedBriefing = request.briefing as unknown as Record<string, unknown>;
        return {
          briefingId: request.briefing.id,
          results: [{ channel: "mock", status: "success" }],
          completedAt: nowISOStringKST(),
        };
      },
    };

    const deps = createDeps({ publisher: spyPublisher });
    await runDailyBriefing(deps, TARGET_DATE);

    expect(capturedBriefing).not.toBeNull();
    const metadata = capturedBriefing?.metadata as Record<string, unknown>;
    expect(metadata).toBeDefined();
    expect(metadata).toHaveProperty("collectedArticleCount");
    expect(metadata).toHaveProperty("analyzedArticleCount");
    expect(metadata).toHaveProperty("selectedNewsCount");
  });
});

import { describe, it, expect, beforeEach } from "vitest";
import { runPipeline } from "../../src/pipeline/runPipeline.js";
import { DbPipelineLock } from "../../src/pipeline/DbPipelineLock.js";
import { DbPipelineRecorder } from "../../src/pipeline/PipelineRecorder.js";
import { MockPipelineRunRepository } from "../../src/db/mock/MockPipelineRunRepository.js";
import { MockPipelineLogRepository } from "../../src/db/mock/MockPipelineLogRepository.js";
import { MockPipelineItemRepository } from "../../src/db/mock/MockPipelineItemRepository.js";
import { MockNewsCollector } from "../../src/collectors/mock/MockNewsCollector.js";
import { MockNewsAnalyzer } from "../../src/analyzers/mock/MockNewsAnalyzer.js";
import { MockBriefingPublisher } from "../../src/publishers/mock/MockBriefingPublisher.js";
import { loadEnv } from "../../src/config/env.js";
import { DEFAULT_AUDIENCE } from "../../src/config/constants.js";
import type { ApplicationDeps } from "../../src/app/createApplication.js";

describe("runPipeline", () => {
  let runRepo: MockPipelineRunRepository;
  let logRepo: MockPipelineLogRepository;
  let itemRepo: MockPipelineItemRepository;
  let lock: DbPipelineLock;
  let recorder: DbPipelineRecorder;
  let appDeps: ApplicationDeps;

  beforeEach(() => {
    runRepo = new MockPipelineRunRepository();
    logRepo = new MockPipelineLogRepository();
    itemRepo = new MockPipelineItemRepository();
    lock = new DbPipelineLock(runRepo);
    recorder = new DbPipelineRecorder(runRepo, logRepo, itemRepo);
    appDeps = {
      collector: new MockNewsCollector(),
      analyzer: new MockNewsAnalyzer(),
      publisher: new MockBriefingPublisher(),
      env: loadEnv({ NODE_ENV: "test", TZ: "Asia/Seoul", DRY_RUN: "false", LOG_LEVEL: "info" }),
      audience: DEFAULT_AUDIENCE,
    };
  });

  it("should run pipeline and record success", async () => {
    const result = await runPipeline({
      deps: appDeps,
      lock,
      recorder,
      triggerType: "MANUAL",
      targetDate: "2026-07-16",
    });

    expect(result.success).toBe(true);
    expect(result.executionLog).toBeDefined();
    expect(result.executionLog!.status).toBe("success");

    // Should have a completed run in the repo
    const run = await runRepo.findById(result.runId);
    expect(run).toBeDefined();
    expect(run!.status).toBe("SUCCESS");
    expect(run!.currentStep).toBe("COMPLETE");
    expect(run!.finishedAt).toBeDefined();
    expect(run!.durationMs).toBeGreaterThanOrEqual(0);

    // Should have logs
    expect(logRepo.logs.length).toBeGreaterThan(0);
  });

  it("should reject duplicate execution", async () => {
    // First run - acquire lock but keep it running
    await lock.acquire("existing-run");

    const result = await runPipeline({
      deps: appDeps,
      lock,
      recorder,
      triggerType: "MANUAL",
      targetDate: "2026-07-16",
    });

    expect(result.success).toBe(false);
    expect(result.error).toContain("already running");
  });

  it("should record failure when pipeline throws", async () => {
    const failingDeps: ApplicationDeps = {
      ...appDeps,
      collector: {
        async collect() {
          throw new Error("Network error");
        },
      },
    };

    const result = await runPipeline({
      deps: failingDeps,
      lock,
      recorder,
      triggerType: "MANUAL",
      targetDate: "2026-07-16",
    });

    expect(result.success).toBe(true);
    expect(result.executionLog!.status).toBe("failed");

    const run = await runRepo.findById(result.runId);
    expect(run!.status).toBe("FAILED");
  });

  it("should record correct trigger type", async () => {
    const result = await runPipeline({
      deps: appDeps,
      lock,
      recorder,
      triggerType: "GITHUB_ACTIONS",
      targetDate: "2026-07-16",
    });

    expect(result.success).toBe(true);
    // Check logs mention the trigger type
    const initLog = logRepo.logs.find((l) => l.eventCode === "PIPELINE_STARTED");
    expect(initLog).toBeDefined();
    expect(initLog!.message).toContain("GITHUB_ACTIONS");
  });
});

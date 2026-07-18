import { describe, it, expect, beforeEach } from "vitest";
import request from "supertest";
import { createAdminApp } from "../../src/admin/server.js";
import { MockPipelineRunRepository } from "../../src/db/mock/MockPipelineRunRepository.js";
import { MockPipelineLogRepository } from "../../src/db/mock/MockPipelineLogRepository.js";
import { MockPipelineItemRepository } from "../../src/db/mock/MockPipelineItemRepository.js";
import { DbPipelineLock } from "../../src/pipeline/DbPipelineLock.js";
import { DbPipelineRecorder } from "../../src/pipeline/PipelineRecorder.js";
import { MockNewsCollector } from "../../src/collectors/mock/MockNewsCollector.js";
import { MockNewsAnalyzer } from "../../src/analyzers/mock/MockNewsAnalyzer.js";
import { MockBriefingPublisher } from "../../src/publishers/mock/MockBriefingPublisher.js";
import { loadEnv } from "../../src/config/env.js";
import { DEFAULT_AUDIENCE } from "../../src/config/constants.js";
import type express from "express";

let app: express.Application;
let runRepo: MockPipelineRunRepository;

function setupApp() {
  runRepo = new MockPipelineRunRepository();
  const logRepo = new MockPipelineLogRepository();
  const itemRepo = new MockPipelineItemRepository();
  const lock = new DbPipelineLock(runRepo);
  const recorder = new DbPipelineRecorder(runRepo, logRepo, itemRepo);
  const env = loadEnv({ NODE_ENV: "test", TZ: "Asia/Seoul", DRY_RUN: "false", LOG_LEVEL: "info" });

  app = createAdminApp({
    runRepo,
    logRepo,
    itemRepo,
    lock,
    recorder,
    appDeps: {
      collector: new MockNewsCollector(),
      analyzer: new MockNewsAnalyzer(),
      publisher: new MockBriefingPublisher(),
      env,
      audience: DEFAULT_AUDIENCE,
    },
    adminToken: "",
  });
}

describe("GET /api/admin/status", () => {
  beforeEach(setupApp);

  it("should return service status with no runs", async () => {
    const res = await request(app).get("/api/admin/status");
    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.data.service).toBe("running");
    expect(res.body.data.pipelineRunning).toBe(false);
    expect(res.body.data.currentRunId).toBeNull();
    expect(res.body.data.lastRun).toBeNull();
    expect(res.body.data.dbConnected).toBe(true);
  });

  it("should show last run info", async () => {
    await runRepo.create({
      id: "run-1",
      status: "SUCCESS",
      triggerType: "MANUAL",
      startedAt: new Date(),
      currentStep: "COMPLETE",
    });
    await runRepo.update("run-1", { status: "SUCCESS" });

    const res = await request(app).get("/api/admin/status");
    expect(res.body.data.lastRun).toBeDefined();
    expect(res.body.data.lastRun.id).toBe("run-1");
  });

  it("should detect running pipeline", async () => {
    await runRepo.create({
      id: "run-active",
      status: "RUNNING",
      triggerType: "MANUAL",
      startedAt: new Date(),
      currentStep: "COLLECT",
    });

    const res = await request(app).get("/api/admin/status");
    expect(res.body.data.pipelineRunning).toBe(true);
    expect(res.body.data.currentRunId).toBe("run-active");
  });
});

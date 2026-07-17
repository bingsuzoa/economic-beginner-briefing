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
let logRepo: MockPipelineLogRepository;
let itemRepo: MockPipelineItemRepository;

function setupApp() {
  runRepo = new MockPipelineRunRepository();
  logRepo = new MockPipelineLogRepository();
  itemRepo = new MockPipelineItemRepository();
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

describe("GET /api/admin/runs", () => {
  beforeEach(setupApp);

  it("should return empty list when no runs exist", async () => {
    const res = await request(app).get("/api/admin/runs");
    expect(res.status).toBe(200);
    expect(res.body.data.runs).toHaveLength(0);
    expect(res.body.data.total).toBe(0);
  });

  it("should return runs with pagination", async () => {
    for (let i = 0; i < 5; i++) {
      await runRepo.create({
        id: `run-${i}`,
        status: "SUCCESS",
        triggerType: "MANUAL",
        startedAt: new Date(Date.now() - i * 60000),
        currentStep: "COMPLETE",
      });
    }

    const res = await request(app).get("/api/admin/runs?page=1&size=2");
    expect(res.status).toBe(200);
    expect(res.body.data.runs).toHaveLength(2);
    expect(res.body.data.total).toBe(5);
    expect(res.body.data.page).toBe(1);
    expect(res.body.data.size).toBe(2);
  });

  it("should filter by status", async () => {
    await runRepo.create({ id: "run-s", status: "SUCCESS", triggerType: "MANUAL", startedAt: new Date(), currentStep: "COMPLETE" });
    await runRepo.create({ id: "run-f", status: "FAILED", triggerType: "MANUAL", startedAt: new Date(), currentStep: "COMPLETE" });

    const res = await request(app).get("/api/admin/runs?status=FAILED");
    expect(res.body.data.runs).toHaveLength(1);
    expect(res.body.data.runs[0].id).toBe("run-f");
  });

  it("should filter by triggerType", async () => {
    await runRepo.create({ id: "run-m", status: "SUCCESS", triggerType: "MANUAL", startedAt: new Date(), currentStep: "COMPLETE" });
    await runRepo.create({ id: "run-g", status: "SUCCESS", triggerType: "GITHUB_ACTIONS", startedAt: new Date(), currentStep: "COMPLETE" });

    const res = await request(app).get("/api/admin/runs?triggerType=GITHUB_ACTIONS");
    expect(res.body.data.runs).toHaveLength(1);
    expect(res.body.data.runs[0].id).toBe("run-g");
  });
});

describe("GET /api/admin/runs/:runId", () => {
  beforeEach(setupApp);

  it("should return run detail", async () => {
    await runRepo.create({ id: "run-1", status: "SUCCESS", triggerType: "MANUAL", startedAt: new Date(), currentStep: "COMPLETE" });

    const res = await request(app).get("/api/admin/runs/run-1");
    expect(res.status).toBe(200);
    expect(res.body.data.id).toBe("run-1");
  });

  it("should return 404 for missing run", async () => {
    const res = await request(app).get("/api/admin/runs/non-existent");
    expect(res.status).toBe(404);
    expect(res.body.code).toBe("RUN_NOT_FOUND");
  });
});

describe("GET /api/admin/runs/:runId/logs", () => {
  beforeEach(setupApp);

  it("should return logs for a run", async () => {
    await runRepo.create({ id: "run-1", status: "SUCCESS", triggerType: "MANUAL", startedAt: new Date(), currentStep: "COMPLETE" });
    await logRepo.create({ runId: "run-1", level: "INFO", step: "INIT", eventCode: "START", message: "Started", metadataJson: null });
    await logRepo.create({ runId: "run-1", level: "ERROR", step: "COLLECT", eventCode: "ERR", message: "Failed", metadataJson: null });

    const res = await request(app).get("/api/admin/runs/run-1/logs");
    expect(res.status).toBe(200);
    expect(res.body.data).toHaveLength(2);
  });

  it("should filter logs by level", async () => {
    await runRepo.create({ id: "run-1", status: "SUCCESS", triggerType: "MANUAL", startedAt: new Date(), currentStep: "COMPLETE" });
    await logRepo.create({ runId: "run-1", level: "INFO", step: "INIT", eventCode: null, message: "Info", metadataJson: null });
    await logRepo.create({ runId: "run-1", level: "ERROR", step: "COLLECT", eventCode: null, message: "Error", metadataJson: null });

    const res = await request(app).get("/api/admin/runs/run-1/logs?level=ERROR");
    expect(res.body.data).toHaveLength(1);
    expect(res.body.data[0].level).toBe("ERROR");
  });

  it("should return 404 for missing run", async () => {
    const res = await request(app).get("/api/admin/runs/non-existent/logs");
    expect(res.status).toBe(404);
  });
});

describe("GET /api/admin/runs/:runId/items", () => {
  beforeEach(setupApp);

  it("should return items for a run", async () => {
    await runRepo.create({ id: "run-1", status: "SUCCESS", triggerType: "MANUAL", startedAt: new Date(), currentStep: "COMPLETE" });
    await itemRepo.create({
      runId: "run-1",
      articleUrl: "https://example.com/1",
      normalizedUrl: "example.com/1",
      source: "Test",
      originalTitle: "Title",
      originalSummary: "Summary",
      publishedAt: new Date(),
      category: "housing",
      duplicateStatus: "UNIQUE",
      analysisStatus: "SUCCESS",
      analysisResultJson: null,
      analysisErrorMessage: null,
      publishStatus: "SUCCESS",
      notionPageId: null,
      notionPageUrl: null,
      publishErrorMessage: null,
    });

    const res = await request(app).get("/api/admin/runs/run-1/items");
    expect(res.status).toBe(200);
    expect(res.body.data).toHaveLength(1);
  });
});

describe("POST /api/admin/runs", () => {
  beforeEach(setupApp);

  it("should start a manual run", async () => {
    const res = await request(app).post("/api/admin/runs");
    expect(res.status).toBe(202);
    expect(res.body.success).toBe(true);
  });

  it("should reject when pipeline is already running", async () => {
    await runRepo.create({ id: "run-active", status: "RUNNING", triggerType: "MANUAL", startedAt: new Date(), currentStep: "COLLECT" });

    const res = await request(app).post("/api/admin/runs");
    expect(res.status).toBe(409);
    expect(res.body.code).toBe("PIPELINE_ALREADY_RUNNING");
  });
});

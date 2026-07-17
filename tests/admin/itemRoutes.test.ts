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
let itemRepo: MockPipelineItemRepository;

function setupApp() {
  const runRepo = new MockPipelineRunRepository();
  const logRepo = new MockPipelineLogRepository();
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

describe("GET /api/admin/items/:itemId", () => {
  beforeEach(setupApp);

  it("should return item detail", async () => {
    const item = await itemRepo.create({
      runId: "run-1",
      articleUrl: "https://example.com/1",
      normalizedUrl: "example.com/1",
      source: "Test",
      originalTitle: "Test Title",
      originalSummary: "Summary",
      publishedAt: new Date(),
      category: "housing",
      duplicateStatus: "UNIQUE",
      analysisStatus: "SUCCESS",
      analysisResultJson: { representativeTitle: "AI Title" },
      analysisErrorMessage: null,
      publishStatus: "SUCCESS",
      notionPageId: "notion-123",
      notionPageUrl: "https://notion.so/page",
      publishErrorMessage: null,
    });

    const res = await request(app).get(`/api/admin/items/${item.id}`);
    expect(res.status).toBe(200);
    expect(res.body.data.originalTitle).toBe("Test Title");
    expect(res.body.data.analysisResultJson.representativeTitle).toBe("AI Title");
    expect(res.body.data.notionPageUrl).toBe("https://notion.so/page");
  });

  it("should return 404 for missing item", async () => {
    const res = await request(app).get("/api/admin/items/99999");
    expect(res.status).toBe(404);
    expect(res.body.code).toBe("ITEM_NOT_FOUND");
  });

  it("should return 400 for invalid item id", async () => {
    const res = await request(app).get("/api/admin/items/not-a-number");
    expect(res.status).toBe(400);
  });
});

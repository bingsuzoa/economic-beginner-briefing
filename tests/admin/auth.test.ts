import { describe, it, expect } from "vitest";
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

function createTestApp(adminToken: string): express.Application {
  const runRepo = new MockPipelineRunRepository();
  const logRepo = new MockPipelineLogRepository();
  const itemRepo = new MockPipelineItemRepository();
  const lock = new DbPipelineLock(runRepo);
  const recorder = new DbPipelineRecorder(runRepo, logRepo, itemRepo);
  const env = loadEnv({ NODE_ENV: "test", TZ: "Asia/Seoul", DRY_RUN: "false", LOG_LEVEL: "info" });

  return createAdminApp({
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
    adminToken,
  });
}

describe("Auth middleware", () => {
  it("should reject requests without token when ADMIN_TOKEN is set", async () => {
    const app = createTestApp("secret-token");
    const res = await request(app).get("/api/admin/status");
    expect(res.status).toBe(401);
    expect(res.body.code).toBe("UNAUTHORIZED");
  });

  it("should reject requests with wrong token", async () => {
    const app = createTestApp("secret-token");
    const res = await request(app)
      .get("/api/admin/status")
      .set("Authorization", "Bearer wrong-token");
    expect(res.status).toBe(401);
  });

  it("should accept requests with correct token", async () => {
    const app = createTestApp("secret-token");
    const res = await request(app)
      .get("/api/admin/status")
      .set("Authorization", "Bearer secret-token");
    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
  });

  it("should allow requests when ADMIN_TOKEN is empty", async () => {
    const app = createTestApp("");
    const res = await request(app).get("/api/admin/status");
    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
  });
});

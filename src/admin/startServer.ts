import { loadEnv } from "../config/env.js";
import { DEFAULT_AUDIENCE } from "../config/constants.js";
import { createPool } from "../db/pool.js";
import { PgPipelineRunRepository } from "../db/repositories/PipelineRunRepository.js";
import { PgPipelineLogRepository } from "../db/repositories/PipelineLogRepository.js";
import { PgPipelineItemRepository } from "../db/repositories/PipelineItemRepository.js";
import { DbPipelineLock } from "../pipeline/DbPipelineLock.js";
import { DbPipelineRecorder } from "../pipeline/PipelineRecorder.js";
import { MockNewsCollector } from "../collectors/mock/MockNewsCollector.js";
import { MockNewsAnalyzer } from "../analyzers/mock/MockNewsAnalyzer.js";
import { MockBriefingPublisher } from "../publishers/mock/MockBriefingPublisher.js";
import { createAdminApp } from "./server.js";

const env = loadEnv();
const pool = createPool(env);

const runRepo = new PgPipelineRunRepository(pool);
const logRepo = new PgPipelineLogRepository(pool);
const itemRepo = new PgPipelineItemRepository(pool);

const lock = new DbPipelineLock(runRepo);
const recorder = new DbPipelineRecorder(runRepo, logRepo, itemRepo);

const appDeps = {
  collector: new MockNewsCollector(),
  analyzer: new MockNewsAnalyzer(),
  publisher: new MockBriefingPublisher(),
  env,
  audience: DEFAULT_AUDIENCE,
};

const app = createAdminApp({
  runRepo,
  logRepo,
  itemRepo,
  lock,
  recorder,
  appDeps,
  adminToken: env.ADMIN_TOKEN,
});

const port = env.ADMIN_PORT;
app.listen(port, () => {
  console.log(`Admin server running on http://localhost:${port}/admin`);
});

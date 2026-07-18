import express from "express";
import path from "node:path";
import { fileURLToPath } from "node:url";
import type { PipelineRunRepository } from "../db/repositories/PipelineRunRepository.js";
import type { PipelineLogRepository } from "../db/repositories/PipelineLogRepository.js";
import type { PipelineItemRepository } from "../db/repositories/PipelineItemRepository.js";
import type { PipelineLock } from "../pipeline/PipelineLock.js";
import type { PipelineRecorder } from "../pipeline/PipelineRecorder.js";
import type { ApplicationDeps } from "../app/createApplication.js";
import { createAuthMiddleware } from "./middleware/auth.js";
import { errorHandler } from "./middleware/errorHandler.js";
import { createStatusRoutes } from "./routes/statusRoutes.js";
import { createRunRoutes } from "./routes/runRoutes.js";
import { createItemRoutes } from "./routes/itemRoutes.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export interface AdminServerDeps {
  runRepo: PipelineRunRepository;
  logRepo: PipelineLogRepository;
  itemRepo: PipelineItemRepository;
  lock: PipelineLock;
  recorder: PipelineRecorder;
  appDeps: ApplicationDeps;
  adminToken: string;
}

export function createAdminApp(deps: AdminServerDeps): express.Application {
  const app = express();

  app.use(express.json());

  // Serve static files for admin dashboard
  const publicDir = path.join(__dirname, "public");
  app.use("/admin", express.static(publicDir));

  // API routes (authenticated)
  const auth = createAuthMiddleware(deps.adminToken);

  const statusRoutes = createStatusRoutes(deps.runRepo);
  const runRoutes = createRunRoutes({
    runRepo: deps.runRepo,
    logRepo: deps.logRepo,
    itemRepo: deps.itemRepo,
    lock: deps.lock,
    recorder: deps.recorder,
    appDeps: deps.appDeps,
  });
  const itemRoutes = createItemRoutes(deps.itemRepo);

  app.use("/api/admin", auth, statusRoutes);
  app.use("/api/admin", auth, runRoutes);
  app.use("/api/admin", auth, itemRoutes);

  // Error handler
  app.use(errorHandler);

  return app;
}

import { Router } from "express";
import type { PipelineRunRepository } from "../../db/repositories/PipelineRunRepository.js";

export function createStatusRoutes(runRepo: PipelineRunRepository): Router {
  const router = Router();

  router.get("/status", async (_req, res, next) => {
    try {
      const running = await runRepo.findRunning();
      const lastRuns = await runRepo.list({ page: 1, size: 1 });
      const lastRun = lastRuns.runs[0] ?? null;

      res.json({
        success: true,
        data: {
          service: "running",
          pipelineRunning: !!running,
          currentRunId: running?.id ?? null,
          currentStep: running?.currentStep ?? null,
          lastRun: lastRun
            ? {
                id: lastRun.id,
                status: lastRun.status,
                startedAt: lastRun.startedAt,
                finishedAt: lastRun.finishedAt,
                triggerType: lastRun.triggerType,
              }
            : null,
          dbConnected: true,
        },
      });
    } catch (err) {
      next(err);
    }
  });

  return router;
}

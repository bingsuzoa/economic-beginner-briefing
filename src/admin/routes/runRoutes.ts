import { Router } from "express";
import type { PipelineRunRepository, RunStatus, TriggerType } from "../../db/repositories/PipelineRunRepository.js";
import type { PipelineLogRepository, LogLevel, LogStep } from "../../db/repositories/PipelineLogRepository.js";
import type { PipelineItemRepository, ItemProcessStatus, DuplicateStatus } from "../../db/repositories/PipelineItemRepository.js";
import type { PipelineLock } from "../../pipeline/PipelineLock.js";
import type { PipelineRecorder } from "../../pipeline/PipelineRecorder.js";
import type { ApplicationDeps } from "../../app/createApplication.js";
import { runPipeline } from "../../pipeline/runPipeline.js";
import { ADMIN_DEFAULT_PAGE_SIZE } from "../../config/constants.js";
import { validateISODate } from "../../utils/date.js";

export interface RunRouteDeps {
  runRepo: PipelineRunRepository;
  logRepo: PipelineLogRepository;
  itemRepo: PipelineItemRepository;
  lock: PipelineLock;
  recorder: PipelineRecorder;
  appDeps: ApplicationDeps;
}

export function createRunRoutes(deps: RunRouteDeps): Router {
  const router = Router();

  // List runs
  router.get("/runs", async (req, res, next) => {
    try {
      const page = parseInt(req.query.page as string, 10) || 1;
      const size = parseInt(req.query.size as string, 10) || ADMIN_DEFAULT_PAGE_SIZE;
      const status = req.query.status as RunStatus | undefined;
      const triggerType = req.query.triggerType as TriggerType | undefined;
      const from = req.query.from as string | undefined;
      const to = req.query.to as string | undefined;

      const result = await deps.runRepo.list({ page, size, status, triggerType, from, to });
      res.json({ success: true, data: result });
    } catch (err) {
      next(err);
    }
  });

  // Get run detail
  router.get("/runs/:runId", async (req, res, next) => {
    try {
      const run = await deps.runRepo.findById(req.params.runId!);
      if (!run) {
        res.status(404).json({
          success: false,
          code: "RUN_NOT_FOUND",
          message: "실행 기록을 찾을 수 없습니다.",
        });
        return;
      }
      res.json({ success: true, data: run });
    } catch (err) {
      next(err);
    }
  });

  // Get run logs
  router.get("/runs/:runId/logs", async (req, res, next) => {
    try {
      const run = await deps.runRepo.findById(req.params.runId!);
      if (!run) {
        res.status(404).json({
          success: false,
          code: "RUN_NOT_FOUND",
          message: "실행 기록을 찾을 수 없습니다.",
        });
        return;
      }

      const level = req.query.level as LogLevel | undefined;
      const step = req.query.step as LogStep | undefined;
      const logs = await deps.logRepo.listByRunId({ runId: req.params.runId!, level, step });
      res.json({ success: true, data: logs });
    } catch (err) {
      next(err);
    }
  });

  // Get run items
  router.get("/runs/:runId/items", async (req, res, next) => {
    try {
      const run = await deps.runRepo.findById(req.params.runId!);
      if (!run) {
        res.status(404).json({
          success: false,
          code: "RUN_NOT_FOUND",
          message: "실행 기록을 찾을 수 없습니다.",
        });
        return;
      }

      const analysisStatus = req.query.analysisStatus as ItemProcessStatus | undefined;
      const publishStatus = req.query.publishStatus as ItemProcessStatus | undefined;
      const duplicateStatus = req.query.duplicateStatus as DuplicateStatus | undefined;
      const items = await deps.itemRepo.listByRunId({
        runId: req.params.runId!,
        analysisStatus,
        publishStatus,
        duplicateStatus,
      });
      res.json({ success: true, data: items });
    } catch (err) {
      next(err);
    }
  });

  // Manual run (async)
  router.post("/runs", async (req, res, next) => {
    try {
      const body = (req.body ?? {}) as { targetDate?: string };
      const targetDate = body.targetDate;

      // Validate targetDate if provided
      if (targetDate !== undefined) {
        try {
          validateISODate(targetDate);
        } catch {
          res.status(400).json({
            success: false,
            code: "INVALID_TARGET_DATE",
            message: `유효하지 않은 날짜 형식입니다: ${targetDate}`,
          });
          return;
        }
      }

      const isLocked = await deps.lock.isLocked();
      if (isLocked) {
        const currentRunId = await deps.lock.getCurrentRunId();
        res.status(409).json({
          success: false,
          code: "PIPELINE_ALREADY_RUNNING",
          message: "파이프라인이 이미 실행 중입니다.",
          currentRunId,
        });
        return;
      }

      const runId = `run-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

      // Start async execution
      runPipeline({
        deps: deps.appDeps,
        lock: deps.lock,
        recorder: deps.recorder,
        triggerType: "MANUAL",
        targetDate,
      }).catch((err) => {
        console.error("Async pipeline execution failed:", err);
      });

      res.status(202).json({
        success: true,
        runId,
        message: "파이프라인 실행이 시작되었습니다.",
      });
    } catch (err) {
      next(err);
    }
  });

  return router;
}

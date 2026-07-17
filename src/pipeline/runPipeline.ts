import type { ApplicationDeps } from "../app/createApplication.js";
import type { PipelineLock } from "./PipelineLock.js";
import type { PipelineRecorder } from "./PipelineRecorder.js";
import type { TriggerType } from "../db/repositories/PipelineRunRepository.js";
import type { ExecutionLog } from "../domain/execution.js";
import { runDailyBriefing } from "../app/runDailyBriefing.js";

export interface RunPipelineOptions {
  deps: ApplicationDeps;
  lock: PipelineLock;
  recorder: PipelineRecorder;
  triggerType: TriggerType;
  targetDate?: string;
}

export interface RunPipelineResult {
  success: boolean;
  runId: string;
  executionLog?: ExecutionLog;
  error?: string;
}

export async function runPipeline(options: RunPipelineOptions): Promise<RunPipelineResult> {
  const { deps, lock, recorder, triggerType, targetDate } = options;
  const runId = `run-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

  const acquired = await lock.acquire(runId);
  if (!acquired) {
    return {
      success: false,
      runId,
      error: "Pipeline is already running",
    };
  }

  try {
    await recorder.startRun(runId, triggerType);
    await recorder.updateStep(runId, "COLLECT");
    await recorder.log(runId, "INFO", "COLLECT", "Starting collection");

    const executionLog = await runDailyBriefing(deps, targetDate);

    const collectedCount = executionLog.collectedArticleCount;
    const selectedCount = executionLog.selectedNewsCount;

    await recorder.updateStep(runId, "COMPLETE");

    const statusMap: Record<string, "SUCCESS" | "PARTIAL_SUCCESS" | "FAILED"> = {
      success: "SUCCESS",
      partial_success: "PARTIAL_SUCCESS",
      failed: "FAILED",
    };

    const runStatus = statusMap[executionLog.status] ?? "FAILED";
    const errorInfo = executionLog.errors.length > 0
      ? { code: executionLog.errors[0]!.code, message: executionLog.errors[0]!.message }
      : undefined;

    await recorder.finishRun(runId, runStatus, {
      collectedCount,
      duplicateCount: 0,
      analysisSuccessCount: selectedCount,
      analysisFailureCount: 0,
      publishSuccessCount: runStatus === "SUCCESS" ? selectedCount : 0,
      publishFailureCount: runStatus === "FAILED" ? selectedCount : 0,
    }, errorInfo);

    return {
      success: true,
      runId,
      executionLog,
    };
  } catch (err) {
    const message = err instanceof Error ? err.message : "Unknown error";
    try {
      await recorder.finishRun(runId, "FAILED", {
        collectedCount: 0,
        duplicateCount: 0,
        analysisSuccessCount: 0,
        analysisFailureCount: 0,
        publishSuccessCount: 0,
        publishFailureCount: 0,
      }, { code: "SYSTEM_UNEXPECTED", message });
    } catch {
      console.error("Failed to record pipeline failure:", message);
    }

    return {
      success: false,
      runId,
      error: message,
    };
  }
}

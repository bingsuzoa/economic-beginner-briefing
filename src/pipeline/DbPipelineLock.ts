import type { PipelineLock } from "./PipelineLock.js";
import type { PipelineRunRepository, TriggerType } from "../db/repositories/PipelineRunRepository.js";
import { PIPELINE_LOCK_MAX_AGE_MS } from "../config/constants.js";

export class DbPipelineLock implements PipelineLock {
  constructor(private readonly runRepo: PipelineRunRepository) {}

  async acquire(runId: string, triggerType: TriggerType = "MANUAL"): Promise<boolean> {
    const running = await this.runRepo.findRunning();
    if (running) {
      const age = Date.now() - running.startedAt.getTime();
      if (age > PIPELINE_LOCK_MAX_AGE_MS) {
        await this.runRepo.update(running.id, {
          status: "FAILED",
          finishedAt: new Date(),
          durationMs: age,
          errorCode: "PIPELINE_TIMEOUT",
          errorMessage: "Pipeline execution exceeded maximum age and was forcibly terminated",
        });
      } else {
        return false;
      }
    }

    await this.runRepo.create({
      id: runId,
      status: "RUNNING",
      triggerType,
      startedAt: new Date(),
      currentStep: "INIT",
    });
    return true;
  }

  async release(runId: string): Promise<void> {
    const run = await this.runRepo.findById(runId);
    if (run && run.status === "RUNNING") {
      await this.runRepo.update(runId, {
        status: "FAILED",
        finishedAt: new Date(),
      });
    }
  }

  async isLocked(): Promise<boolean> {
    const running = await this.runRepo.findRunning();
    if (!running) return false;
    const age = Date.now() - running.startedAt.getTime();
    return age <= PIPELINE_LOCK_MAX_AGE_MS;
  }

  async getCurrentRunId(): Promise<string | null> {
    const running = await this.runRepo.findRunning();
    return running?.id ?? null;
  }
}

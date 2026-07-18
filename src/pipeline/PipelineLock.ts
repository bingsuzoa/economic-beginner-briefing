import type { TriggerType } from "../db/repositories/PipelineRunRepository.js";

export interface PipelineLock {
  acquire(runId: string, triggerType?: TriggerType): Promise<boolean>;
  release(runId: string): Promise<void>;
  isLocked(): Promise<boolean>;
  getCurrentRunId(): Promise<string | null>;
}

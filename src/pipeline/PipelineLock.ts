export interface PipelineLock {
  acquire(runId: string): Promise<boolean>;
  release(runId: string): Promise<void>;
  isLocked(): Promise<boolean>;
  getCurrentRunId(): Promise<string | null>;
}

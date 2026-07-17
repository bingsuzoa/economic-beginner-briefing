import type { ExecutionLog, PublicationDecision } from "../domain/execution.js";

export interface ExecutionTracker {
  checkDuplicate(targetDate: string): Promise<PublicationDecision>;
  recordExecution(log: ExecutionLog): Promise<void>;
  getLastExecution(targetDate: string): Promise<ExecutionLog | null>;
}

export class MockExecutionTracker implements ExecutionTracker {
  private executions = new Map<string, ExecutionLog>();

  async checkDuplicate(targetDate: string): Promise<PublicationDecision> {
    const last = this.executions.get(targetDate);
    if (!last) {
      return "publish";
    }
    if (last.status === "success") {
      return "skip_already_published";
    }
    return "retry_previous_failure";
  }

  async recordExecution(log: ExecutionLog): Promise<void> {
    this.executions.set(log.targetDate, log);
  }

  async getLastExecution(targetDate: string): Promise<ExecutionLog | null> {
    return this.executions.get(targetDate) ?? null;
  }

  clear(): void {
    this.executions.clear();
  }
}

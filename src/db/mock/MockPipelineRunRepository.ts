import type {
  PipelineRun,
  PipelineRunRepository,
  ListRunsFilter,
  ListRunsResult,
} from "../repositories/PipelineRunRepository.js";

export class MockPipelineRunRepository implements PipelineRunRepository {
  readonly runs: Map<string, PipelineRun> = new Map();

  async create(run: Pick<PipelineRun, "id" | "status" | "triggerType" | "startedAt" | "currentStep">): Promise<PipelineRun> {
    const now = new Date();
    const full: PipelineRun = {
      id: run.id,
      status: run.status,
      triggerType: run.triggerType,
      startedAt: run.startedAt,
      finishedAt: null,
      durationMs: null,
      currentStep: run.currentStep,
      collectedCount: 0,
      duplicateCount: 0,
      analysisSuccessCount: 0,
      analysisFailureCount: 0,
      publishSuccessCount: 0,
      publishFailureCount: 0,
      totalFailureCount: 0,
      errorCode: null,
      errorMessage: null,
      createdAt: now,
      updatedAt: now,
    };
    this.runs.set(full.id, full);
    return full;
  }

  async findById(id: string): Promise<PipelineRun | null> {
    return this.runs.get(id) ?? null;
  }

  async findRunning(): Promise<PipelineRun | null> {
    for (const run of this.runs.values()) {
      if (run.status === "RUNNING") return run;
    }
    return null;
  }

  async list(filter: ListRunsFilter): Promise<ListRunsResult> {
    const page = filter.page ?? 1;
    const size = filter.size ?? 20;
    let all = Array.from(this.runs.values());

    if (filter.status) {
      all = all.filter((r) => r.status === filter.status);
    }
    if (filter.triggerType) {
      all = all.filter((r) => r.triggerType === filter.triggerType);
    }
    if (filter.from) {
      const from = new Date(filter.from);
      all = all.filter((r) => r.startedAt >= from);
    }
    if (filter.to) {
      const to = new Date(filter.to);
      all = all.filter((r) => r.startedAt <= to);
    }

    all.sort((a, b) => b.startedAt.getTime() - a.startedAt.getTime());
    const total = all.length;
    const offset = (page - 1) * size;
    const runs = all.slice(offset, offset + size);

    return { runs, total, page, size };
  }

  async update(id: string, fields: Partial<Omit<PipelineRun, "id" | "createdAt">>): Promise<PipelineRun | null> {
    const existing = this.runs.get(id);
    if (!existing) return null;
    const updated = { ...existing, ...fields, updatedAt: new Date() };
    this.runs.set(id, updated);
    return updated;
  }
}

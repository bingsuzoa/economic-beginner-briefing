import type {
  PipelineLog,
  PipelineLogRepository,
  ListLogsFilter,
} from "../repositories/PipelineLogRepository.js";

export class MockPipelineLogRepository implements PipelineLogRepository {
  readonly logs: PipelineLog[] = [];
  private nextId = 1;

  async create(log: Omit<PipelineLog, "id" | "createdAt">): Promise<PipelineLog> {
    const full: PipelineLog = {
      ...log,
      id: this.nextId++,
      createdAt: new Date(),
    };
    this.logs.push(full);
    return full;
  }

  async listByRunId(filter: ListLogsFilter): Promise<PipelineLog[]> {
    return this.logs
      .filter((l) => l.runId === filter.runId)
      .filter((l) => !filter.level || l.level === filter.level)
      .filter((l) => !filter.step || l.step === filter.step)
      .sort((a, b) => a.createdAt.getTime() - b.createdAt.getTime());
  }
}

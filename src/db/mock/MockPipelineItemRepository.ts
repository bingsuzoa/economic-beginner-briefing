import type {
  PipelineItem,
  PipelineItemRepository,
  ListItemsFilter,
} from "../repositories/PipelineItemRepository.js";

export class MockPipelineItemRepository implements PipelineItemRepository {
  readonly items: Map<number, PipelineItem> = new Map();
  private nextId = 1;

  async create(item: Omit<PipelineItem, "id" | "createdAt" | "updatedAt">): Promise<PipelineItem> {
    const now = new Date();
    const full: PipelineItem = {
      ...item,
      id: this.nextId++,
      createdAt: now,
      updatedAt: now,
    };
    this.items.set(full.id, full);
    return full;
  }

  async findById(id: number): Promise<PipelineItem | null> {
    return this.items.get(id) ?? null;
  }

  async listByRunId(filter: ListItemsFilter): Promise<PipelineItem[]> {
    return Array.from(this.items.values())
      .filter((i) => i.runId === filter.runId)
      .filter((i) => !filter.analysisStatus || i.analysisStatus === filter.analysisStatus)
      .filter((i) => !filter.publishStatus || i.publishStatus === filter.publishStatus)
      .filter((i) => !filter.duplicateStatus || i.duplicateStatus === filter.duplicateStatus)
      .sort((a, b) => a.id - b.id);
  }

  async update(id: number, fields: Partial<Omit<PipelineItem, "id" | "runId" | "createdAt">>): Promise<PipelineItem | null> {
    const existing = this.items.get(id);
    if (!existing) return null;
    const updated = { ...existing, ...fields, updatedAt: new Date() };
    this.items.set(id, updated);
    return updated;
  }
}

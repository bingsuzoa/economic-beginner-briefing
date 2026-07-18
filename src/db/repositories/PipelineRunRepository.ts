import type { DbPool } from "../pool.js";

export type RunStatus = "RUNNING" | "SUCCESS" | "PARTIAL_SUCCESS" | "FAILED";
export type TriggerType = "MANUAL" | "SCHEDULER" | "GITHUB_ACTIONS" | "LOCAL";
export type PipelineStep = "INIT" | "COLLECT" | "DEDUPLICATE" | "ANALYZE" | "PUBLISH" | "COMPLETE";

export interface PipelineRun {
  id: string;
  status: RunStatus;
  triggerType: TriggerType;
  startedAt: Date;
  finishedAt: Date | null;
  durationMs: number | null;
  currentStep: PipelineStep;
  collectedCount: number;
  duplicateCount: number;
  analysisSuccessCount: number;
  analysisFailureCount: number;
  publishSuccessCount: number;
  publishFailureCount: number;
  totalFailureCount: number;
  errorCode: string | null;
  errorMessage: string | null;
  createdAt: Date;
  updatedAt: Date;
}

export interface ListRunsFilter {
  page?: number;
  size?: number;
  status?: RunStatus;
  triggerType?: TriggerType;
  from?: string;
  to?: string;
}

export interface ListRunsResult {
  runs: PipelineRun[];
  total: number;
  page: number;
  size: number;
}

export interface PipelineRunRepository {
  create(run: Pick<PipelineRun, "id" | "status" | "triggerType" | "startedAt" | "currentStep">): Promise<PipelineRun>;
  findById(id: string): Promise<PipelineRun | null>;
  findRunning(): Promise<PipelineRun | null>;
  list(filter: ListRunsFilter): Promise<ListRunsResult>;
  update(id: string, fields: Partial<Omit<PipelineRun, "id" | "createdAt">>): Promise<PipelineRun | null>;
}

export class PgPipelineRunRepository implements PipelineRunRepository {
  constructor(private readonly pool: DbPool) {}

  async create(run: Pick<PipelineRun, "id" | "status" | "triggerType" | "startedAt" | "currentStep">): Promise<PipelineRun> {
    const result = await this.pool.query<PipelineRunRow>(
      `INSERT INTO pipeline_runs (id, status, trigger_type, started_at, current_step)
       VALUES ($1, $2, $3, $4, $5)
       RETURNING *`,
      [run.id, run.status, run.triggerType, run.startedAt, run.currentStep],
    );
    return mapRow(result.rows[0]!);
  }

  async findById(id: string): Promise<PipelineRun | null> {
    const result = await this.pool.query<PipelineRunRow>(
      "SELECT * FROM pipeline_runs WHERE id = $1",
      [id],
    );
    return result.rows[0] ? mapRow(result.rows[0]) : null;
  }

  async findRunning(): Promise<PipelineRun | null> {
    const result = await this.pool.query<PipelineRunRow>(
      "SELECT * FROM pipeline_runs WHERE status = 'RUNNING' ORDER BY started_at DESC LIMIT 1",
    );
    return result.rows[0] ? mapRow(result.rows[0]) : null;
  }

  async list(filter: ListRunsFilter): Promise<ListRunsResult> {
    const page = filter.page ?? 1;
    const size = filter.size ?? 20;
    const conditions: string[] = [];
    const params: unknown[] = [];
    let paramIdx = 1;

    if (filter.status) {
      conditions.push(`status = $${paramIdx++}`);
      params.push(filter.status);
    }
    if (filter.triggerType) {
      conditions.push(`trigger_type = $${paramIdx++}`);
      params.push(filter.triggerType);
    }
    if (filter.from) {
      conditions.push(`started_at >= $${paramIdx++}`);
      params.push(filter.from);
    }
    if (filter.to) {
      conditions.push(`started_at <= $${paramIdx++}`);
      params.push(filter.to);
    }

    const where = conditions.length > 0 ? `WHERE ${conditions.join(" AND ")}` : "";

    const countResult = await this.pool.query<{ count: string }>(
      `SELECT COUNT(*) as count FROM pipeline_runs ${where}`,
      params,
    );
    const total = parseInt(countResult.rows[0]?.count ?? "0", 10);

    const offset = (page - 1) * size;
    const dataResult = await this.pool.query<PipelineRunRow>(
      `SELECT * FROM pipeline_runs ${where} ORDER BY started_at DESC LIMIT $${paramIdx++} OFFSET $${paramIdx++}`,
      [...params, size, offset],
    );

    return {
      runs: dataResult.rows.map(mapRow),
      total,
      page,
      size,
    };
  }

  async update(id: string, fields: Partial<Omit<PipelineRun, "id" | "createdAt">>): Promise<PipelineRun | null> {
    const setClauses: string[] = [];
    const params: unknown[] = [];
    let paramIdx = 1;

    const fieldMap: Record<string, string> = {
      status: "status",
      triggerType: "trigger_type",
      startedAt: "started_at",
      finishedAt: "finished_at",
      durationMs: "duration_ms",
      currentStep: "current_step",
      collectedCount: "collected_count",
      duplicateCount: "duplicate_count",
      analysisSuccessCount: "analysis_success_count",
      analysisFailureCount: "analysis_failure_count",
      publishSuccessCount: "publish_success_count",
      publishFailureCount: "publish_failure_count",
      totalFailureCount: "total_failure_count",
      errorCode: "error_code",
      errorMessage: "error_message",
    };

    for (const [key, col] of Object.entries(fieldMap)) {
      if (key in fields) {
        setClauses.push(`${col} = $${paramIdx++}`);
        params.push((fields as Record<string, unknown>)[key]);
      }
    }

    if (setClauses.length === 0) return this.findById(id);

    setClauses.push(`updated_at = $${paramIdx++}`);
    params.push(new Date());

    params.push(id);
    const result = await this.pool.query<PipelineRunRow>(
      `UPDATE pipeline_runs SET ${setClauses.join(", ")} WHERE id = $${paramIdx} RETURNING *`,
      params,
    );
    return result.rows[0] ? mapRow(result.rows[0]) : null;
  }
}

interface PipelineRunRow {
  id: string;
  status: string;
  trigger_type: string;
  started_at: Date;
  finished_at: Date | null;
  duration_ms: number | null;
  current_step: string;
  collected_count: number;
  duplicate_count: number;
  analysis_success_count: number;
  analysis_failure_count: number;
  publish_success_count: number;
  publish_failure_count: number;
  total_failure_count: number;
  error_code: string | null;
  error_message: string | null;
  created_at: Date;
  updated_at: Date;
}

function mapRow(row: PipelineRunRow): PipelineRun {
  return {
    id: row.id,
    status: row.status as RunStatus,
    triggerType: row.trigger_type as TriggerType,
    startedAt: row.started_at,
    finishedAt: row.finished_at,
    durationMs: row.duration_ms,
    currentStep: row.current_step as PipelineStep,
    collectedCount: row.collected_count,
    duplicateCount: row.duplicate_count,
    analysisSuccessCount: row.analysis_success_count,
    analysisFailureCount: row.analysis_failure_count,
    publishSuccessCount: row.publish_success_count,
    publishFailureCount: row.publish_failure_count,
    totalFailureCount: row.total_failure_count,
    errorCode: row.error_code,
    errorMessage: row.error_message,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

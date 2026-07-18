import type { DbPool } from "../pool.js";

export type LogLevel = "INFO" | "WARN" | "ERROR";
export type LogStep = "INIT" | "COLLECT" | "DEDUPLICATE" | "ANALYZE" | "PUBLISH" | "COMPLETE";

export interface PipelineLog {
  id: number;
  runId: string;
  level: LogLevel;
  step: LogStep;
  eventCode: string | null;
  message: string;
  metadataJson: Record<string, unknown> | null;
  createdAt: Date;
}

export interface ListLogsFilter {
  runId: string;
  level?: LogLevel;
  step?: LogStep;
}

export interface PipelineLogRepository {
  create(log: Omit<PipelineLog, "id" | "createdAt">): Promise<PipelineLog>;
  listByRunId(filter: ListLogsFilter): Promise<PipelineLog[]>;
}

export class PgPipelineLogRepository implements PipelineLogRepository {
  constructor(private readonly pool: DbPool) {}

  async create(log: Omit<PipelineLog, "id" | "createdAt">): Promise<PipelineLog> {
    const result = await this.pool.query<PipelineLogRow>(
      `INSERT INTO pipeline_logs (run_id, level, step, event_code, message, metadata_json)
       VALUES ($1, $2, $3, $4, $5, $6)
       RETURNING *`,
      [log.runId, log.level, log.step, log.eventCode, log.message, log.metadataJson ? JSON.stringify(log.metadataJson) : null],
    );
    return mapLogRow(result.rows[0]!);
  }

  async listByRunId(filter: ListLogsFilter): Promise<PipelineLog[]> {
    const conditions: string[] = ["run_id = $1"];
    const params: unknown[] = [filter.runId];
    let paramIdx = 2;

    if (filter.level) {
      conditions.push(`level = $${paramIdx++}`);
      params.push(filter.level);
    }
    if (filter.step) {
      conditions.push(`step = $${paramIdx++}`);
      params.push(filter.step);
    }

    const result = await this.pool.query<PipelineLogRow>(
      `SELECT * FROM pipeline_logs WHERE ${conditions.join(" AND ")} ORDER BY created_at ASC`,
      params,
    );
    return result.rows.map(mapLogRow);
  }
}

interface PipelineLogRow {
  id: number;
  run_id: string;
  level: string;
  step: string;
  event_code: string | null;
  message: string;
  metadata_json: Record<string, unknown> | null;
  created_at: Date;
}

function mapLogRow(row: PipelineLogRow): PipelineLog {
  return {
    id: row.id,
    runId: row.run_id,
    level: row.level as LogLevel,
    step: row.step as LogStep,
    eventCode: row.event_code,
    message: row.message,
    metadataJson: row.metadata_json,
    createdAt: row.created_at,
  };
}

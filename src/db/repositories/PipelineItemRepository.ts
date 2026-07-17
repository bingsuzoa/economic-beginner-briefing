import type { DbPool } from "../pool.js";

export type DuplicateStatus = "UNIQUE" | "DUPLICATE";
export type ItemProcessStatus = "PENDING" | "SUCCESS" | "FAILED" | "SKIPPED";

export interface PipelineItem {
  id: number;
  runId: string;
  articleUrl: string | null;
  normalizedUrl: string | null;
  source: string | null;
  originalTitle: string | null;
  originalSummary: string | null;
  publishedAt: Date | null;
  category: string | null;
  duplicateStatus: DuplicateStatus;
  analysisStatus: ItemProcessStatus;
  analysisResultJson: Record<string, unknown> | null;
  analysisErrorMessage: string | null;
  publishStatus: ItemProcessStatus;
  notionPageId: string | null;
  notionPageUrl: string | null;
  publishErrorMessage: string | null;
  createdAt: Date;
  updatedAt: Date;
}

export interface ListItemsFilter {
  runId: string;
  analysisStatus?: ItemProcessStatus;
  publishStatus?: ItemProcessStatus;
  duplicateStatus?: DuplicateStatus;
}

export interface PipelineItemRepository {
  create(item: Omit<PipelineItem, "id" | "createdAt" | "updatedAt">): Promise<PipelineItem>;
  findById(id: number): Promise<PipelineItem | null>;
  listByRunId(filter: ListItemsFilter): Promise<PipelineItem[]>;
  update(id: number, fields: Partial<Omit<PipelineItem, "id" | "runId" | "createdAt">>): Promise<PipelineItem | null>;
}

export class PgPipelineItemRepository implements PipelineItemRepository {
  constructor(private readonly pool: DbPool) {}

  async create(item: Omit<PipelineItem, "id" | "createdAt" | "updatedAt">): Promise<PipelineItem> {
    const result = await this.pool.query<PipelineItemRow>(
      `INSERT INTO pipeline_items (
        run_id, article_url, normalized_url, source, original_title, original_summary,
        published_at, category, duplicate_status, analysis_status, analysis_result_json,
        analysis_error_message, publish_status, notion_page_id, notion_page_url, publish_error_message
      ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16) RETURNING *`,
      [
        item.runId, item.articleUrl, item.normalizedUrl, item.source,
        item.originalTitle, item.originalSummary, item.publishedAt,
        item.category, item.duplicateStatus, item.analysisStatus,
        item.analysisResultJson ? JSON.stringify(item.analysisResultJson) : null,
        item.analysisErrorMessage, item.publishStatus, item.notionPageId,
        item.notionPageUrl, item.publishErrorMessage,
      ],
    );
    return mapItemRow(result.rows[0]!);
  }

  async findById(id: number): Promise<PipelineItem | null> {
    const result = await this.pool.query<PipelineItemRow>(
      "SELECT * FROM pipeline_items WHERE id = $1",
      [id],
    );
    return result.rows[0] ? mapItemRow(result.rows[0]) : null;
  }

  async listByRunId(filter: ListItemsFilter): Promise<PipelineItem[]> {
    const conditions: string[] = ["run_id = $1"];
    const params: unknown[] = [filter.runId];
    let paramIdx = 2;

    if (filter.analysisStatus) {
      conditions.push(`analysis_status = $${paramIdx++}`);
      params.push(filter.analysisStatus);
    }
    if (filter.publishStatus) {
      conditions.push(`publish_status = $${paramIdx++}`);
      params.push(filter.publishStatus);
    }
    if (filter.duplicateStatus) {
      conditions.push(`duplicate_status = $${paramIdx++}`);
      params.push(filter.duplicateStatus);
    }

    const result = await this.pool.query<PipelineItemRow>(
      `SELECT * FROM pipeline_items WHERE ${conditions.join(" AND ")} ORDER BY id ASC`,
      params,
    );
    return result.rows.map(mapItemRow);
  }

  async update(id: number, fields: Partial<Omit<PipelineItem, "id" | "runId" | "createdAt">>): Promise<PipelineItem | null> {
    const setClauses: string[] = [];
    const params: unknown[] = [];
    let paramIdx = 1;

    const fieldMap: Record<string, string> = {
      articleUrl: "article_url",
      normalizedUrl: "normalized_url",
      source: "source",
      originalTitle: "original_title",
      originalSummary: "original_summary",
      publishedAt: "published_at",
      category: "category",
      duplicateStatus: "duplicate_status",
      analysisStatus: "analysis_status",
      analysisResultJson: "analysis_result_json",
      analysisErrorMessage: "analysis_error_message",
      publishStatus: "publish_status",
      notionPageId: "notion_page_id",
      notionPageUrl: "notion_page_url",
      publishErrorMessage: "publish_error_message",
    };

    for (const [key, col] of Object.entries(fieldMap)) {
      if (key in fields) {
        setClauses.push(`${col} = $${paramIdx++}`);
        const val = (fields as Record<string, unknown>)[key];
        if (key === "analysisResultJson" && val != null) {
          params.push(JSON.stringify(val));
        } else {
          params.push(val);
        }
      }
    }

    if (setClauses.length === 0) return this.findById(id);

    setClauses.push(`updated_at = $${paramIdx++}`);
    params.push(new Date());

    params.push(id);
    const result = await this.pool.query<PipelineItemRow>(
      `UPDATE pipeline_items SET ${setClauses.join(", ")} WHERE id = $${paramIdx} RETURNING *`,
      params,
    );
    return result.rows[0] ? mapItemRow(result.rows[0]) : null;
  }
}

interface PipelineItemRow {
  id: number;
  run_id: string;
  article_url: string | null;
  normalized_url: string | null;
  source: string | null;
  original_title: string | null;
  original_summary: string | null;
  published_at: Date | null;
  category: string | null;
  duplicate_status: string;
  analysis_status: string;
  analysis_result_json: Record<string, unknown> | null;
  analysis_error_message: string | null;
  publish_status: string;
  notion_page_id: string | null;
  notion_page_url: string | null;
  publish_error_message: string | null;
  created_at: Date;
  updated_at: Date;
}

function mapItemRow(row: PipelineItemRow): PipelineItem {
  return {
    id: row.id,
    runId: row.run_id,
    articleUrl: row.article_url,
    normalizedUrl: row.normalized_url,
    source: row.source,
    originalTitle: row.original_title,
    originalSummary: row.original_summary,
    publishedAt: row.published_at,
    category: row.category,
    duplicateStatus: row.duplicate_status as DuplicateStatus,
    analysisStatus: row.analysis_status as ItemProcessStatus,
    analysisResultJson: row.analysis_result_json,
    analysisErrorMessage: row.analysis_error_message,
    publishStatus: row.publish_status as ItemProcessStatus,
    notionPageId: row.notion_page_id,
    notionPageUrl: row.notion_page_url,
    publishErrorMessage: row.publish_error_message,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

import type { PipelineRunRepository, TriggerType, PipelineStep } from "../db/repositories/PipelineRunRepository.js";
import type { PipelineLogRepository, LogLevel, LogStep } from "../db/repositories/PipelineLogRepository.js";
import type { PipelineItemRepository } from "../db/repositories/PipelineItemRepository.js";
import type { Article } from "../domain/article.js";

export interface PipelineRecorder {
  startRun(runId: string, triggerType: TriggerType): Promise<void>;
  updateStep(runId: string, step: PipelineStep): Promise<void>;
  log(runId: string, level: LogLevel, step: LogStep, message: string, eventCode?: string, metadata?: Record<string, unknown>): Promise<void>;
  recordItems(runId: string, articles: Article[]): Promise<number[]>;
  updateItemAnalysis(itemId: number, status: "SUCCESS" | "FAILED", result?: Record<string, unknown>, error?: string): Promise<void>;
  updateItemPublish(itemId: number, status: "SUCCESS" | "FAILED" | "SKIPPED", notionPageId?: string, notionPageUrl?: string, error?: string): Promise<void>;
  finishRun(runId: string, status: "SUCCESS" | "PARTIAL_SUCCESS" | "FAILED", counts: RunCounts, error?: { code: string; message: string }): Promise<void>;
}

export interface RunCounts {
  collectedCount: number;
  duplicateCount: number;
  analysisSuccessCount: number;
  analysisFailureCount: number;
  publishSuccessCount: number;
  publishFailureCount: number;
}

export class DbPipelineRecorder implements PipelineRecorder {
  constructor(
    private readonly runRepo: PipelineRunRepository,
    private readonly logRepo: PipelineLogRepository,
    private readonly itemRepo: PipelineItemRepository,
  ) {}

  async startRun(runId: string, triggerType: TriggerType): Promise<void> {
    const existing = await this.runRepo.findById(runId);
    if (!existing) {
      await this.runRepo.create({
        id: runId,
        status: "RUNNING",
        triggerType,
        startedAt: new Date(),
        currentStep: "INIT",
      });
    }
    await this.logRepo.create({
      runId,
      level: "INFO",
      step: "INIT",
      eventCode: "PIPELINE_STARTED",
      message: `Pipeline started with trigger: ${triggerType}`,
      metadataJson: null,
    });
  }

  async updateStep(runId: string, step: PipelineStep): Promise<void> {
    await this.runRepo.update(runId, { currentStep: step });
  }

  async log(
    runId: string,
    level: LogLevel,
    step: LogStep,
    message: string,
    eventCode?: string,
    metadata?: Record<string, unknown>,
  ): Promise<void> {
    await this.logRepo.create({
      runId,
      level,
      step,
      eventCode: eventCode ?? null,
      message,
      metadataJson: metadata ?? null,
    });
  }

  async recordItems(runId: string, articles: Article[]): Promise<number[]> {
    const ids: number[] = [];
    for (const article of articles) {
      const item = await this.itemRepo.create({
        runId,
        articleUrl: article.url,
        normalizedUrl: article.url.replace(/^https?:\/\//, "").replace(/\/$/, ""),
        source: article.sourceName,
        originalTitle: article.title,
        originalSummary: article.summary,
        publishedAt: new Date(article.publishedAt),
        category: article.categories[0] ?? null,
        duplicateStatus: "UNIQUE",
        analysisStatus: "PENDING",
        analysisResultJson: null,
        analysisErrorMessage: null,
        publishStatus: "PENDING",
        notionPageId: null,
        notionPageUrl: null,
        publishErrorMessage: null,
      });
      ids.push(item.id);
    }
    return ids;
  }

  async updateItemAnalysis(
    itemId: number,
    status: "SUCCESS" | "FAILED",
    result?: Record<string, unknown>,
    error?: string,
  ): Promise<void> {
    await this.itemRepo.update(itemId, {
      analysisStatus: status,
      analysisResultJson: result ?? null,
      analysisErrorMessage: error ?? null,
    });
  }

  async updateItemPublish(
    itemId: number,
    status: "SUCCESS" | "FAILED" | "SKIPPED",
    notionPageId?: string,
    notionPageUrl?: string,
    error?: string,
  ): Promise<void> {
    await this.itemRepo.update(itemId, {
      publishStatus: status,
      notionPageId: notionPageId ?? null,
      notionPageUrl: notionPageUrl ?? null,
      publishErrorMessage: error ?? null,
    });
  }

  async finishRun(
    runId: string,
    status: "SUCCESS" | "PARTIAL_SUCCESS" | "FAILED",
    counts: RunCounts,
    error?: { code: string; message: string },
  ): Promise<void> {
    const run = await this.runRepo.findById(runId);
    const startedAt = run?.startedAt ?? new Date();
    const finishedAt = new Date();
    const durationMs = finishedAt.getTime() - startedAt.getTime();

    await this.runRepo.update(runId, {
      status,
      finishedAt,
      durationMs,
      currentStep: "COMPLETE",
      collectedCount: counts.collectedCount,
      duplicateCount: counts.duplicateCount,
      analysisSuccessCount: counts.analysisSuccessCount,
      analysisFailureCount: counts.analysisFailureCount,
      publishSuccessCount: counts.publishSuccessCount,
      publishFailureCount: counts.publishFailureCount,
      totalFailureCount: counts.analysisFailureCount + counts.publishFailureCount,
      errorCode: error?.code ?? null,
      errorMessage: error?.message ?? null,
    });

    await this.logRepo.create({
      runId,
      level: status === "FAILED" ? "ERROR" : "INFO",
      step: "COMPLETE",
      eventCode: "PIPELINE_FINISHED",
      message: `Pipeline finished with status: ${status}`,
      metadataJson: { ...counts },
    });
  }
}

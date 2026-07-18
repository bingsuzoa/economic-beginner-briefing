import type { CollectNewsRequest, CollectNewsResult, SourceCollectionReport } from "../domain/article.js";
import type { NewsCollector } from "./NewsCollector.js";
import type { SourceAdapter } from "./sources/SourceAdapter.js";
import type { Article } from "../domain/article.js";
import { getTargetDateStartKST, getTargetDateEndKST } from "../utils/date.js";
import { filterByDate } from "./filters/dateFilter.js";
import { removeDuplicates } from "./filters/duplicateRemover.js";
import { validateQuality } from "./filters/qualityValidator.js";
import { AppError } from "../errors/AppError.js";
import { ErrorCodes } from "../errors/errorCodes.js";

import { YonhapSourceAdapter } from "./sources/YonhapSourceAdapter.js";
import { HankyungSourceAdapter } from "./sources/HankyungSourceAdapter.js";
import { MKSourceAdapter } from "./sources/MKSourceAdapter.js";
import { SBSBizSourceAdapter } from "./sources/SBSBizSourceAdapter.js";
import { SedailySourceAdapter } from "./sources/SedailySourceAdapter.js";

/**
 * Creates the default set of source adapters.
 */
export function createDefaultAdapters(): SourceAdapter[] {
  return [
    new YonhapSourceAdapter(),
    new HankyungSourceAdapter(),
    new MKSourceAdapter(),
    new SBSBizSourceAdapter(),
    new SedailySourceAdapter(),
  ];
}

/**
 * Real news collector that fetches articles from multiple RSS sources,
 * applies date filtering, deduplication, and quality validation.
 */
export class RealNewsCollector implements NewsCollector {
  private readonly adapters: SourceAdapter[];

  constructor(adapters?: SourceAdapter[]) {
    this.adapters = adapters ?? createDefaultAdapters();
  }

  async collect(request: CollectNewsRequest): Promise<CollectNewsResult> {
    const startTime = request.startTime ?? getTargetDateStartKST(request.targetDate);
    const endTime = request.endTime ?? getTargetDateEndKST(request.targetDate);

    const sourceReports: SourceCollectionReport[] = [];
    let allArticles: Article[] = [];
    let totalCollected = 0;

    // Collect from all sources in parallel, tolerating individual failures
    const results = await Promise.allSettled(
      this.adapters.map(async (adapter) => {
        const startMs = Date.now();
        const result = await adapter.collect(startTime, endTime);
        const durationMs = Date.now() - startMs;
        return { adapter, result, durationMs };
      }),
    );

    for (const settled of results) {
      if (settled.status === "fulfilled") {
        const { adapter, result, durationMs } = settled.value;
        totalCollected += result.collectedCount;
        allArticles.push(...result.articles);

        sourceReports.push({
          sourceName: adapter.sourceName,
          status: "success",
          collectedCount: result.collectedCount,
          acceptedCount: result.acceptedCount,
          rawCount: result.collectedCount,
          durationMs,
        });
      } else {
        const adapter = this.adapters[results.indexOf(settled)];
        const error = settled.reason instanceof AppError
          ? settled.reason
          : settled.reason instanceof Error
            ? new AppError({
                code: ErrorCodes.COLLECT_SOURCE_UNAVAILABLE,
                stage: "collect",
                retryable: true,
                safeMessage: settled.reason.message,
                cause: settled.reason,
              })
            : new AppError({
                code: ErrorCodes.COLLECT_SOURCE_UNAVAILABLE,
                stage: "collect",
                retryable: true,
                safeMessage: "Unknown error",
              });

        sourceReports.push({
          sourceName: adapter?.sourceName ?? "unknown",
          status: "failed",
          collectedCount: 0,
          acceptedCount: 0,
          errorCode: error.code,
          errorMessage: error.safeMessage,
        });
      }
    }

    // Apply date filter (secondary check - adapters already filter by date)
    const dateResult = filterByDate(allArticles, startTime, endTime);
    allArticles = dateResult.accepted;
    const dateRejected = dateResult.rejected.length;

    // Apply quality validation
    const qualityResult = validateQuality(allArticles);
    allArticles = qualityResult.valid;
    const qualityRejected = qualityResult.invalid.length;

    // Remove duplicates
    const dedupeResult = removeDuplicates(allArticles);
    allArticles = dedupeResult.unique;
    const duplicateRejected = dedupeResult.duplicates.length;

    // Apply maxArticles limit if specified
    if (request.maxArticles && allArticles.length > request.maxArticles) {
      allArticles = allArticles.slice(0, request.maxArticles);
    }

    const totalRejected = dateRejected + qualityRejected + duplicateRejected;

    return {
      targetDate: request.targetDate,
      articles: allArticles,
      sourceReports,
      totalCollected,
      totalAccepted: allArticles.length,
      totalRejected,
    };
  }
}

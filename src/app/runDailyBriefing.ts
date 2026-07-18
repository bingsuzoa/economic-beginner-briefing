import type { ApplicationDeps } from "./createApplication.js";
import type { ExecutionLog } from "../domain/execution.js";
import type { ExecutionError } from "../domain/execution.js";
import { getHourlyTimeRange, nowISOStringKST, formatHourlyBriefingTitle } from "../utils/date.js";
import type { HourlyTimeRange } from "../utils/date.js";
import { DEFAULT_MAX_SELECTED_NEWS } from "../config/constants.js";
import { AppError } from "../errors/AppError.js";
import {
  validateCollectResult,
  validateAnalyzeResult,
} from "./validatePipelineData.js";
import type { ValidationWarning } from "./validatePipelineData.js";

function warningsToErrors(warnings: ValidationWarning[]): ExecutionError[] {
  return warnings.map((w) => ({
    stage: w.stage,
    code: "ANALYZE_VALIDATION_ERROR",
    message: w.message,
    retryable: false,
  }));
}

export interface RunBriefingOptions {
  /** Override target date (YYYY-MM-DD). Used in daily/manual mode. */
  targetDate?: string;
  /** Explicit hourly time range. If provided, uses hourly mode. */
  timeRange?: HourlyTimeRange;
}

export async function runDailyBriefing(
  deps: ApplicationDeps,
  targetDateOrOptions?: string | RunBriefingOptions,
): Promise<ExecutionLog> {
  const options: RunBriefingOptions =
    typeof targetDateOrOptions === "string"
      ? { targetDate: targetDateOrOptions }
      : targetDateOrOptions ?? {};

  // When targetDate is explicitly set (manual mode), use full-day range (no timeRange).
  // When timeRange is set, use hourly mode.
  // Default (no options): compute hourly time range from current time.
  const timeRange = options.targetDate ? undefined : (options.timeRange ?? getHourlyTimeRange());
  const date = options.targetDate ?? (timeRange?.targetDate ?? getHourlyTimeRange().targetDate);
  const executionId = `exec-${Date.now()}`;
  const startedAt = nowISOStringKST();
  const errors: ExecutionError[] = [];

  // 0. Check duplicate execution
  const dedupeKey = timeRange
    ? `${timeRange.targetDate}T${String(timeRange.hour).padStart(2, "0")}`
    : date;

  if (deps.executionTracker) {
    const decision = await deps.executionTracker.checkDuplicate(dedupeKey);
    if (decision === "skip_already_published") {
      return {
        executionId,
        targetDate: date,
        startedAt,
        completedAt: nowISOStringKST(),
        status: "success",
        collectedArticleCount: 0,
        selectedNewsCount: 0,
        errors: [],
      };
    }
  }

  // 1. Collect
  let collectedArticleCount = 0;
  const collectResult = await (async () => {
    try {
      return await deps.collector.collect({
        targetDate: date,
        timezone: "Asia/Seoul",
        startTime: timeRange?.startTime,
        endTime: timeRange?.endTime,
      });
    } catch (e) {
      const appErr =
        e instanceof AppError
          ? e
          : new AppError({
              code: "SYSTEM_UNEXPECTED",
              stage: "collect",
              retryable: false,
              safeMessage: "Unexpected error during collection",
              cause: e instanceof Error ? e : undefined,
            });
      errors.push(appErr.toExecutionError());
      return null;
    }
  })();

  // When no articles found in the hourly window, succeed gracefully
  if (!collectResult || collectResult.articles.length === 0) {
    if (collectResult && collectResult.articles.length === 0) {
      // No articles in this time window - this is normal for hourly mode
      return {
        executionId,
        targetDate: date,
        startedAt,
        completedAt: nowISOStringKST(),
        status: "success",
        collectedArticleCount: 0,
        selectedNewsCount: 0,
        errors: [],
      };
    }
    // Collection failed (exception was thrown)
    return {
      executionId,
      targetDate: date,
      startedAt,
      completedAt: nowISOStringKST(),
      status: "failed",
      collectedArticleCount: 0,
      selectedNewsCount: 0,
      errors,
    };
  }

  // 1.5 Validate collect result
  const collectValidation = validateCollectResult(collectResult, date);
  errors.push(...warningsToErrors(collectValidation.warnings));

  const validArticles = collectValidation.validArticles;
  if (validArticles.length === 0) {
    return {
      executionId,
      targetDate: date,
      startedAt,
      completedAt: nowISOStringKST(),
      status: "failed",
      collectedArticleCount: 0,
      selectedNewsCount: 0,
      errors: [
        ...errors,
        {
          stage: "collect" as const,
          code: "COLLECT_NO_ARTICLES",
          message: "No valid articles after validation",
          retryable: false,
        },
      ],
    };
  }

  collectedArticleCount = validArticles.length;

  // 2. Analyze
  const briefingTitle = timeRange
    ? formatHourlyBriefingTitle(timeRange.targetDate, timeRange.hour)
    : undefined;

  const analyzeResult = await (async () => {
    try {
      return await deps.analyzer.analyze({
        targetDate: date,
        articles: validArticles,
        maxSelectedNews: DEFAULT_MAX_SELECTED_NEWS,
        audience: deps.audience,
        briefingTitle,
      });
    } catch (e) {
      const appErr =
        e instanceof AppError
          ? e
          : new AppError({
              code: "SYSTEM_UNEXPECTED",
              stage: "analyze",
              retryable: false,
              safeMessage: "Unexpected error during analysis",
              cause: e instanceof Error ? e : undefined,
            });
      errors.push(appErr.toExecutionError());
      return null;
    }
  })();

  if (!analyzeResult) {
    const log: ExecutionLog = {
      executionId,
      targetDate: date,
      startedAt,
      completedAt: nowISOStringKST(),
      status: "failed",
      collectedArticleCount,
      selectedNewsCount: 0,
      errors,
    };
    if (deps.executionTracker) {
      await deps.executionTracker.recordExecution(log);
    }
    return log;
  }

  // 2.5 Validate analyze result
  const analyzeValidation = validateAnalyzeResult(analyzeResult, date);
  errors.push(...warningsToErrors(analyzeValidation.warnings));

  if (!analyzeValidation.valid) {
    const log: ExecutionLog = {
      executionId,
      targetDate: date,
      startedAt,
      completedAt: nowISOStringKST(),
      status: "failed",
      collectedArticleCount,
      selectedNewsCount: 0,
      errors: [
        ...errors,
        {
          stage: "analyze" as const,
          code: "ANALYZE_EMPTY_INPUT",
          message: "No valid news in briefing after validation",
          retryable: false,
        },
      ],
    };
    if (deps.executionTracker) {
      await deps.executionTracker.recordExecution(log);
    }
    return log;
  }

  // 3. Publish
  const publishResult = await (async () => {
    try {
      return await deps.publisher.publish({
        briefing: analyzeResult.briefing,
        dryRun: deps.env.DRY_RUN,
      });
    } catch (e) {
      const appErr =
        e instanceof AppError
          ? e
          : new AppError({
              code: "SYSTEM_UNEXPECTED",
              stage: "publish",
              retryable: false,
              safeMessage: "Unexpected error during publishing",
              cause: e instanceof Error ? e : undefined,
            });
      errors.push(appErr.toExecutionError());
      return null;
    }
  })();

  const selectedNewsCount = analyzeResult.briefing.news.length;

  if (!publishResult) {
    const log: ExecutionLog = {
      executionId,
      targetDate: date,
      startedAt,
      completedAt: nowISOStringKST(),
      status: "failed",
      collectedArticleCount,
      selectedNewsCount,
      errors,
    };
    if (deps.executionTracker) {
      await deps.executionTracker.recordExecution(log);
    }
    return log;
  }

  // Record publish channel results as errors for visibility
  for (const r of publishResult.results) {
    if (r.status === "failed") {
      errors.push({
        stage: "publish",
        code: r.errorCode ?? "PUBLISH_CHANNEL_ERROR",
        message: r.errorMessage ?? `${r.channel} publish failed`,
        retryable: false,
      });
    } else if (r.status === "skipped" && r.errorCode) {
      errors.push({
        stage: "publish",
        code: r.errorCode,
        message: r.errorMessage ?? `${r.channel} publish skipped`,
        retryable: false,
      });
    }
  }

  // Determine final status
  const allSucceeded = publishResult.results.every(
    (r) => r.status === "success" || r.status === "skipped",
  );
  const allFailed = publishResult.results.every(
    (r) => r.status === "failed",
  );

  let status: "success" | "partial_success" | "failed";
  if (allFailed) {
    status = "failed";
  } else if (allSucceeded) {
    status = "success";
  } else {
    status = "partial_success";
  }

  const log: ExecutionLog = {
    executionId,
    targetDate: date,
    startedAt,
    completedAt: nowISOStringKST(),
    status,
    collectedArticleCount,
    selectedNewsCount,
    errors,
  };

  if (deps.executionTracker) {
    await deps.executionTracker.recordExecution(log);
  }

  return log;
}

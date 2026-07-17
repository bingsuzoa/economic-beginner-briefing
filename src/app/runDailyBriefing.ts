import type { ApplicationDeps } from "./createApplication.js";
import type { ExecutionLog } from "../domain/execution.js";
import type { ExecutionError } from "../domain/execution.js";
import { getYesterdayDateInKST, nowISOStringKST } from "../utils/date.js";
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

export async function runDailyBriefing(
  deps: ApplicationDeps,
  targetDate?: string,
): Promise<ExecutionLog> {
  const executionId = `exec-${Date.now()}`;
  const date = targetDate ?? getYesterdayDateInKST();
  const startedAt = nowISOStringKST();
  const errors: ExecutionError[] = [];

  // 0. Check duplicate execution
  if (deps.executionTracker) {
    const decision = await deps.executionTracker.checkDuplicate(date);
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

  if (!collectResult || collectResult.articles.length === 0) {
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
        ...(collectResult && collectResult.articles.length === 0
          ? [
              {
                stage: "collect" as const,
                code: "COLLECT_NO_ARTICLES",
                message: "No articles collected",
                retryable: false,
              },
            ]
          : []),
      ],
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
  const analyzeResult = await (async () => {
    try {
      return await deps.analyzer.analyze({
        targetDate: date,
        articles: validArticles,
        maxSelectedNews: DEFAULT_MAX_SELECTED_NEWS,
        audience: deps.audience,
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

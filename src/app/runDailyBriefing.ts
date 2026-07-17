import type { ApplicationDeps } from "./createApplication.js";
import type { ExecutionLog } from "../domain/execution.js";
import type { ExecutionError } from "../domain/execution.js";
import { getYesterdayDateInKST, nowISOStringKST } from "../utils/date.js";
import { DEFAULT_MAX_SELECTED_NEWS } from "../config/constants.js";
import { AppError } from "../errors/AppError.js";

export async function runDailyBriefing(
  deps: ApplicationDeps,
  targetDate?: string,
): Promise<ExecutionLog> {
  const executionId = `exec-${Date.now()}`;
  const date = targetDate ?? getYesterdayDateInKST();
  const startedAt = nowISOStringKST();
  const errors: ExecutionError[] = [];

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

  collectedArticleCount = collectResult.articles.length;

  // 2. Analyze
  const analyzeResult = await (async () => {
    try {
      return await deps.analyzer.analyze({
        targetDate: date,
        articles: collectResult.articles,
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
    return {
      executionId,
      targetDate: date,
      startedAt,
      completedAt: nowISOStringKST(),
      status: "failed",
      collectedArticleCount,
      selectedNewsCount: 0,
      errors,
    };
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
    return {
      executionId,
      targetDate: date,
      startedAt,
      completedAt: nowISOStringKST(),
      status: "failed",
      collectedArticleCount,
      selectedNewsCount,
      errors,
    };
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

  return {
    executionId,
    targetDate: date,
    startedAt,
    completedAt: nowISOStringKST(),
    status,
    collectedArticleCount,
    selectedNewsCount,
    errors,
  };
}

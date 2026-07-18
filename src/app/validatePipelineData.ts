import type { CollectNewsResult, Article } from "../domain/article.js";
import type { AnalyzeNewsResult } from "../domain/analyzedNews.js";

export interface ValidationWarning {
  stage: "collect" | "analyze";
  message: string;
}

export interface CollectValidationResult {
  validArticles: Article[];
  warnings: ValidationWarning[];
}

export interface AnalyzeValidationResult {
  valid: boolean;
  warnings: ValidationWarning[];
}

export function validateCollectResult(
  result: CollectNewsResult,
  targetDate: string,
): CollectValidationResult {
  const warnings: ValidationWarning[] = [];

  if (result.targetDate !== targetDate) {
    warnings.push({
      stage: "collect",
      message: `targetDate mismatch: expected ${targetDate}, got ${result.targetDate}`,
    });
  }

  const validArticles = result.articles.filter((article) => {
    if (!article.id || !article.title || !article.url) {
      warnings.push({
        stage: "collect",
        message: `Article missing required field: id=${article.id}, title=${article.title ? "present" : "missing"}, url=${article.url ? "present" : "missing"}`,
      });
      return false;
    }
    return true;
  });

  return { validArticles, warnings };
}

export function validateAnalyzeResult(
  result: AnalyzeNewsResult,
  targetDate: string,
): AnalyzeValidationResult {
  const warnings: ValidationWarning[] = [];

  if (result.briefing.targetDate !== targetDate) {
    warnings.push({
      stage: "analyze",
      message: `targetDate mismatch: expected ${targetDate}, got ${result.briefing.targetDate}`,
    });
  }

  if (result.briefing.news.length === 0) {
    return { valid: false, warnings };
  }

  const validNews = result.briefing.news.filter((news) => {
    if (!news.sources || news.sources.length === 0) {
      warnings.push({
        stage: "analyze",
        message: `News "${news.representativeTitle}" has no source references, excluded`,
      });
      return false;
    }
    const hasUrl = news.sources.some((s) => s.url);
    if (!hasUrl) {
      warnings.push({
        stage: "analyze",
        message: `News "${news.representativeTitle}" has no source URLs, excluded`,
      });
      return false;
    }
    return true;
  });

  if (validNews.length !== result.briefing.news.length) {
    result.briefing.news = validNews;
  }

  if (validNews.length === 0) {
    return { valid: false, warnings };
  }

  return { valid: true, warnings };
}

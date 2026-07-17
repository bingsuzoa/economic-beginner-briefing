import type { Article, ArticleSourceType, NewsCategory } from "../../domain/article.js";
import { nowISOStringKST } from "../../utils/date.js";
import { createHash } from "crypto";

export interface RawArticleData {
  title: string;
  url: string;
  publishedAt: string;
  summary: string;
  sourceName: string;
  sourceType: ArticleSourceType;
  categories: NewsCategory[];
  content?: string;
  guid?: string;
}

/**
 * Normalizes raw article data to the Article domain type.
 * Generates a stable ID based on URL and GUID.
 */
export function normalizeToArticle(raw: RawArticleData): Article {
  return {
    id: generateArticleId(raw.url, raw.guid),
    title: raw.title.trim(),
    summary: raw.summary.trim(),
    sourceName: raw.sourceName,
    sourceType: raw.sourceType,
    publishedAt: raw.publishedAt,
    collectedAt: nowISOStringKST(),
    url: raw.url,
    categories: raw.categories,
    language: "ko",
    content: raw.content?.trim(),
  };
}

/**
 * Generates a stable article ID from URL and optional GUID.
 * Uses SHA-256 hash to create a unique identifier.
 */
function generateArticleId(url: string, guid?: string): string {
  const source = guid || url;
  const hash = createHash("sha256").update(source).digest("hex");
  return hash.substring(0, 16);
}

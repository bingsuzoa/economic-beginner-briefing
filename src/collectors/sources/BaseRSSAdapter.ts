import type { ArticleSourceType, NewsCategory } from "../../domain/article.js";
import { fetchRSSFeed, type RSSItem } from "../parsers/rssParser.js";
import { normalizeToArticle, type RawArticleData } from "../parsers/articleNormalizer.js";
import { classifyCategories } from "../filters/categoryClassifier.js";
import type { SourceAdapter, SourceCollectionResult } from "./SourceAdapter.js";
import type { Article } from "../../domain/article.js";

export interface RSSSourceConfig {
  sourceName: string;
  sourceType: ArticleSourceType;
  feedUrls: string[];
  defaultCategories?: NewsCategory[];
}

/**
 * Base adapter for RSS-based news sources.
 * Handles common RSS parsing, date filtering, and article normalization.
 */
export abstract class BaseRSSAdapter implements SourceAdapter {
  readonly sourceName: string;
  protected readonly config: RSSSourceConfig;

  constructor(config: RSSSourceConfig) {
    this.sourceName = config.sourceName;
    this.config = config;
  }

  async collect(startTime: string, endTime: string): Promise<SourceCollectionResult> {
    const allArticles: Article[] = [];
    let totalCollected = 0;

    for (const feedUrl of this.config.feedUrls) {
      const feed = await fetchRSSFeed(feedUrl);
      totalCollected += feed.items.length;

      for (const item of feed.items) {
        const article = this.parseItem(item, startTime, endTime);
        if (article) {
          allArticles.push(article);
        }
      }
    }

    return {
      articles: allArticles,
      collectedCount: totalCollected,
      acceptedCount: allArticles.length,
    };
  }

  protected parseItem(item: RSSItem, startTime: string, endTime: string): Article | null {
    const publishedAt = item.isoDate ?? item.pubDate;
    if (!publishedAt) return null;

    const publishedMs = new Date(publishedAt).getTime();
    if (Number.isNaN(publishedMs)) return null;

    const startMs = new Date(startTime).getTime();
    const endMs = new Date(endTime).getTime();
    if (publishedMs < startMs || publishedMs > endMs) return null;

    const title = item.title?.trim();
    if (!title) return null;

    const url = item.link?.trim();
    if (!url) return null;

    const summary = item.contentSnippet?.trim() ?? "";
    const content = item.content?.trim();

    const categories = this.determineCategories(title, summary);
    if (categories.length === 0) return null;

    const isoPublishedAt = new Date(publishedAt).toISOString().replace("Z", "+09:00");

    const raw: RawArticleData = {
      title,
      url,
      publishedAt: isoPublishedAt,
      summary,
      sourceName: this.config.sourceName,
      sourceType: this.config.sourceType,
      categories,
      content,
      guid: item.guid,
    };

    return normalizeToArticle(raw);
  }

  protected determineCategories(title: string, summary: string): NewsCategory[] {
    const classified = classifyCategories(title, summary);
    if (classified.length > 0) return classified;

    if (this.config.defaultCategories && this.config.defaultCategories.length > 0) {
      return this.config.defaultCategories;
    }

    return [];
  }
}

import type { Article } from "../../domain/article.js";

export interface SourceCollectionResult {
  articles: Article[];
  collectedCount: number;
  acceptedCount: number;
}

/**
 * Interface that all news source adapters must implement.
 * Each adapter knows how to fetch and parse articles from a specific source.
 */
export interface SourceAdapter {
  /**
   * The name of the news source (e.g., "한국경제", "금융위원회")
   */
  readonly sourceName: string;

  /**
   * Collects articles from this source for the specified date range.
   * @param startTime - Start of date range in KST (inclusive)
   * @param endTime - End of date range in KST (inclusive)
   * @returns Collection result with articles and counts
   */
  collect(startTime: string, endTime: string): Promise<SourceCollectionResult>;
}

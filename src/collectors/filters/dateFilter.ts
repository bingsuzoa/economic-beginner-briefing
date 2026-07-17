import type { Article } from "../../domain/article.js";

export interface DateFilterResult {
  accepted: Article[];
  rejected: Article[];
}

/**
 * Filters articles to only include those published on the target date in KST.
 * @param articles - Articles to filter
 * @param startTime - Start of target date in KST ISO format (e.g., "2026-07-16T00:00:00+09:00")
 * @param endTime - End of target date in KST ISO format (e.g., "2026-07-16T23:59:59+09:00")
 */
export function filterByDate(
  articles: Article[],
  startTime: string,
  endTime: string,
): DateFilterResult {
  const startMs = new Date(startTime).getTime();
  const endMs = new Date(endTime).getTime();

  const accepted: Article[] = [];
  const rejected: Article[] = [];

  for (const article of articles) {
    const publishedMs = new Date(article.publishedAt).getTime();

    if (Number.isNaN(publishedMs)) {
      rejected.push(article);
      continue;
    }

    if (publishedMs >= startMs && publishedMs <= endMs) {
      accepted.push(article);
    } else {
      rejected.push(article);
    }
  }

  return { accepted, rejected };
}

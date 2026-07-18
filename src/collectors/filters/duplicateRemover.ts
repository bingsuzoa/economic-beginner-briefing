import type { Article } from "../../domain/article.js";

export interface DeduplicationResult {
  unique: Article[];
  duplicates: Article[];
}

export interface NewsEventGroup {
  representative: Article;
  relatedArticles: Article[];
  sourceNames: string[];
}

export interface EnhancedDeduplicationResult extends DeduplicationResult {
  eventGroups: NewsEventGroup[];
}

/**
 * Removes duplicate articles based on:
 * 1. Normalized URL matching (strips tracking parameters)
 * 2. Article ID matching
 * 3. Same-source title similarity
 *
 * Additionally groups cross-source articles covering the same event,
 * selecting a representative article based on content quality.
 */
export function removeDuplicates(articles: Article[]): EnhancedDeduplicationResult {
  const duplicates: Article[] = [];
  const seenUrls = new Set<string>();
  const seenIds = new Set<string>();
  const seenTitlesBySource = new Map<string, string[]>();

  // Phase 1: Remove exact duplicates (same URL, same ID, same-source similar title)
  const afterExactDedup: Article[] = [];

  for (const article of articles) {
    const normalizedUrl = normalizeUrl(article.url);

    if (seenIds.has(article.id)) {
      duplicates.push(article);
      continue;
    }

    if (seenUrls.has(normalizedUrl)) {
      duplicates.push(article);
      continue;
    }

    const sourceTitles = seenTitlesBySource.get(article.sourceName) ?? [];
    if (isTitleDuplicate(article.title, sourceTitles)) {
      duplicates.push(article);
      continue;
    }

    seenIds.add(article.id);
    seenUrls.add(normalizedUrl);
    sourceTitles.push(article.title);
    seenTitlesBySource.set(article.sourceName, sourceTitles);
    afterExactDedup.push(article);
  }

  // Phase 2: Group cross-source articles covering the same event
  const eventGroups = groupByEvent(afterExactDedup);

  // Select representative from each group and collect unique articles
  const unique: Article[] = [];
  for (const group of eventGroups) {
    unique.push(group.representative);
    duplicates.push(...group.relatedArticles);
  }

  return { unique, duplicates, eventGroups };
}

/**
 * Groups articles from different sources that cover the same event.
 * Uses title similarity to determine if articles from different sources
 * are about the same event.
 */
function groupByEvent(articles: Article[]): NewsEventGroup[] {
  const groups: NewsEventGroup[] = [];
  const assigned = new Set<number>();

  for (let i = 0; i < articles.length; i++) {
    if (assigned.has(i)) continue;

    const articleI = articles[i]!;
    const group: Article[] = [articleI];
    assigned.add(i);

    for (let j = i + 1; j < articles.length; j++) {
      if (assigned.has(j)) continue;

      const articleJ = articles[j]!;

      // Only group articles from different sources
      if (articleI.sourceName === articleJ.sourceName) continue;

      const titleA = cleanTitleText(articleI.title);
      const titleB = cleanTitleText(articleJ.title);

      if (calculateSimilarity(titleA, titleB) > 0.8) {
        group.push(articleJ);
        assigned.add(j);
      }
    }

    if (group.length === 1) {
      // Single article, no grouping needed
      groups.push({
        representative: articleI,
        relatedArticles: [],
        sourceNames: [articleI.sourceName],
      });
    } else {
      // Multiple articles about the same event: pick best representative
      const sorted = [...group].sort((a, b) => scoreArticleQuality(b) - scoreArticleQuality(a));
      const representative = sorted[0]!;
      const related = sorted.slice(1);

      groups.push({
        representative,
        relatedArticles: related,
        sourceNames: group.map((a) => a.sourceName),
      });
    }
  }

  return groups;
}

/**
 * Scores an article's quality for representative selection.
 * Higher score = better representative.
 */
function scoreArticleQuality(article: Article): number {
  let score = 0;

  // Prefer articles with longer summary (more informative)
  const summaryLength = article.summary?.length ?? 0;
  score += Math.min(summaryLength / 50, 5); // up to 5 points for summary length

  // Prefer articles with content
  if (article.content) {
    score += Math.min(article.content.length / 200, 3); // up to 3 points
  }

  // Prefer titles with specific numbers (more concrete)
  if (/\d/.test(article.title)) {
    score += 2;
  }

  // Penalize clickbait-style titles
  if (/[!?]{2,}|충격|경악|헐|대박/.test(article.title)) {
    score -= 3;
  }

  // Penalize very short titles
  if (article.title.length < 15) {
    score -= 1;
  }

  return score;
}

/**
 * Normalizes a URL by removing tracking parameters and fragments.
 */
export function normalizeUrl(url: string): string {
  try {
    const parsed = new URL(url);
    const trackingParams = [
      "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
      "ref", "fbclid", "gclid", "mc_cid", "mc_eid",
    ];

    for (const param of trackingParams) {
      parsed.searchParams.delete(param);
    }

    parsed.hash = "";

    // Normalize mobile/PC URL variants
    let normalized = parsed.toString();
    normalized = normalized.replace(/\/\/m\./, "//www.");
    normalized = normalized.replace(/\/\/mobile\./, "//www.");

    return normalized;
  } catch {
    return url;
  }
}

/**
 * Checks if a title is similar to any existing title from the same source.
 * Uses character overlap ratio for Korean text comparison.
 */
function isTitleDuplicate(title: string, existingTitles: string[]): boolean {
  const cleanTitle = cleanTitleText(title);

  for (const existing of existingTitles) {
    const cleanExisting = cleanTitleText(existing);
    if (calculateSimilarity(cleanTitle, cleanExisting) > 0.8) {
      return true;
    }
  }

  return false;
}

/**
 * Removes common noise from titles for comparison.
 */
function cleanTitleText(title: string): string {
  return title
    .replace(/\[.*?\]/g, "")   // Remove bracketed tags like [속보], [단독]
    .replace(/\(.*?\)/g, "")   // Remove parenthetical content
    .replace(/\s+/g, "")       // Remove whitespace
    .trim();
}

/**
 * Calculates character-level similarity between two strings.
 * Returns a value between 0 and 1.
 */
export function calculateSimilarity(a: string, b: string): number {
  if (a === b) return 1;
  if (a.length === 0 || b.length === 0) return 0;

  const longer = a.length >= b.length ? a : b;
  const shorter = a.length >= b.length ? b : a;

  let matchCount = 0;
  const longerChars = [...longer];
  const shorterChars = [...shorter];
  const used = new Array(longerChars.length).fill(false);

  for (const ch of shorterChars) {
    const idx = longerChars.findIndex((c, i) => !used[i] && c === ch);
    if (idx !== -1) {
      matchCount++;
      used[idx] = true;
    }
  }

  return (matchCount * 2) / (longerChars.length + shorterChars.length);
}

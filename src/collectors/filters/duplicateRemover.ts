import type { Article } from "../../domain/article.js";

export interface DeduplicationResult {
  unique: Article[];
  duplicates: Article[];
}

/**
 * Removes duplicate articles based on:
 * 1. Normalized URL matching (strips tracking parameters)
 * 2. Article ID matching
 * 3. Same-source title similarity
 */
export function removeDuplicates(articles: Article[]): DeduplicationResult {
  const unique: Article[] = [];
  const duplicates: Article[] = [];
  const seenUrls = new Set<string>();
  const seenIds = new Set<string>();
  const seenTitlesBySource = new Map<string, string[]>();

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
    unique.push(article);
  }

  return { unique, duplicates };
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
function calculateSimilarity(a: string, b: string): number {
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

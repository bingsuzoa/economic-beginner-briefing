import type { Article, NewsCategory } from "../../domain/article.js";
import type { RelevanceScore } from "./relevanceScorer.js";
import { DIVERSITY } from "../../config/constants.js";

export interface DiversitySelectionOptions {
  maxArticlesPerSource?: number;
  maxArticlesPerCategory?: number;
  minRelevanceScore?: number;
  maxTotal?: number;
}

export interface DiversitySelectionResult {
  selected: Article[];
  excluded: Article[];
  stats: {
    bySource: Record<string, number>;
    byCategory: Record<string, number>;
    excludedBySourceLimit: number;
    excludedByCategoryLimit: number;
    excludedByRelevance: number;
  };
}

/**
 * Selects articles considering source and category diversity.
 *
 * Selection priority:
 * 1. Relevance score (higher = better)
 * 2. Source diversity (cap per source)
 * 3. Category diversity (cap per category)
 *
 * Quality and relevance are prioritized; diversity acts as a tiebreaker
 * and prevents monopolization by a single source or category.
 */
export function selectWithDiversity(
  articles: Article[],
  scores: RelevanceScore[],
  options: DiversitySelectionOptions = {},
): DiversitySelectionResult {
  const maxPerSource = options.maxArticlesPerSource ?? DIVERSITY.MAX_ARTICLES_PER_SOURCE;
  const maxPerCategory = options.maxArticlesPerCategory ?? DIVERSITY.MAX_ARTICLES_PER_CATEGORY;
  const minRelevance = options.minRelevanceScore ?? DIVERSITY.MIN_PERSONAL_FINANCE_RELEVANCE;
  const maxTotal = options.maxTotal ?? Infinity;

  const scoreMap = new Map<string, number>();
  for (const s of scores) {
    scoreMap.set(s.articleId, s.score);
  }

  // Sort by relevance score (descending), then by published time (descending for freshness)
  const sorted = [...articles].sort((a, b) => {
    const scoreA = scoreMap.get(a.id) ?? 0;
    const scoreB = scoreMap.get(b.id) ?? 0;
    if (scoreB !== scoreA) return scoreB - scoreA;
    return new Date(b.publishedAt).getTime() - new Date(a.publishedAt).getTime();
  });

  const selected: Article[] = [];
  const excluded: Article[] = [];
  const sourceCounts: Record<string, number> = {};
  const categoryCounts: Record<string, number> = {};

  let excludedBySourceLimit = 0;
  let excludedByCategoryLimit = 0;
  let excludedByRelevance = 0;

  for (const article of sorted) {
    if (selected.length >= maxTotal) {
      excluded.push(article);
      continue;
    }

    const relevance = scoreMap.get(article.id) ?? 0;

    // Filter by minimum relevance
    if (relevance < minRelevance) {
      excluded.push(article);
      excludedByRelevance++;
      continue;
    }

    // Check source limit
    const sourceCount = sourceCounts[article.sourceName] ?? 0;
    if (sourceCount >= maxPerSource) {
      excluded.push(article);
      excludedBySourceLimit++;
      continue;
    }

    // Check category limit - article passes if at least one category has room
    const articleCategories = article.categories.length > 0 ? article.categories : ["other" as NewsCategory];
    const allCategoriesFull = articleCategories.every(
      (cat) => (categoryCounts[cat] ?? 0) >= maxPerCategory,
    );

    if (allCategoriesFull) {
      excluded.push(article);
      excludedByCategoryLimit++;
      continue;
    }

    // Accept article
    selected.push(article);
    sourceCounts[article.sourceName] = sourceCount + 1;
    for (const cat of articleCategories) {
      categoryCounts[cat] = (categoryCounts[cat] ?? 0) + 1;
    }
  }

  return {
    selected,
    excluded,
    stats: {
      bySource: { ...sourceCounts },
      byCategory: { ...categoryCounts },
      excludedBySourceLimit,
      excludedByCategoryLimit,
      excludedByRelevance,
    },
  };
}

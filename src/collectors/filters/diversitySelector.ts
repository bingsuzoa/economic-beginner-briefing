import type { Article, NewsCategory } from "../../domain/article.js";
import type { RelevanceScore } from "./relevanceScorer.js";
import { DIVERSITY } from "../../config/constants.js";

export interface DiversitySelectionOptions {
  maxArticlesPerSource?: number;
  maxArticlesPerCategory?: number;
  minRelevanceScore?: number;
  maxTotal?: number;
  /** Score threshold at or above which articles can exceed category soft cap */
  softMaxOverrideScore?: number;
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
 * 3. Category diversity (soft cap per category, overridable for high-score articles)
 *
 * Quality and relevance are prioritized; diversity acts as a tiebreaker
 * and prevents monopolization by a single source or category.
 *
 * Soft cap: The category limit is a soft cap. Articles with a relevance score
 * at or above `softMaxOverrideScore` can exceed the category limit by 1.
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
  const softMaxOverride = options.softMaxOverrideScore ?? DIVERSITY.SOFT_MAX_OVERRIDE_SCORE;

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

    // Check source limit (hard cap)
    const sourceCount = sourceCounts[article.sourceName] ?? 0;
    if (sourceCount >= maxPerSource) {
      excluded.push(article);
      excludedBySourceLimit++;
      continue;
    }

    // Check category limit - soft cap with override for high-score articles
    const articleCategories = article.categories.length > 0 ? article.categories : ["other" as NewsCategory];

    const allCategoriesFull = articleCategories.every((cat) => {
      const count = categoryCounts[cat] ?? 0;
      // Soft cap: allow +1 overflow for high-score articles
      const effectiveMax = relevance >= softMaxOverride ? maxPerCategory + 1 : maxPerCategory;
      return count >= effectiveMax;
    });

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

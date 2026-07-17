import type { Article } from "../../domain/article.js";

export interface QualityValidationResult {
  valid: Article[];
  invalid: Article[];
}

const AD_KEYWORDS = [
  "[AD]", "[광고]", "[제휴]", "[이벤트]", "[홍보]",
  "광고", "제휴", "이벤트 안내", "협찬",
];

const MIN_TITLE_LENGTH = 5;

/**
 * Validates article quality and filters out low-quality or ad content.
 *
 * Rejection criteria:
 * - Title is empty or shorter than MIN_TITLE_LENGTH
 * - URL is empty or invalid
 * - publishedAt is empty or unparseable
 * - Both summary and content are empty
 * - Title contains ad keywords
 */
export function validateQuality(articles: Article[]): QualityValidationResult {
  const valid: Article[] = [];
  const invalid: Article[] = [];

  for (const article of articles) {
    if (!isValidArticle(article)) {
      invalid.push(article);
    } else {
      valid.push(article);
    }
  }

  return { valid, invalid };
}

function isValidArticle(article: Article): boolean {
  if (!article.title || article.title.trim().length < MIN_TITLE_LENGTH) {
    return false;
  }

  if (!article.url || !isValidUrl(article.url)) {
    return false;
  }

  if (!article.publishedAt || Number.isNaN(new Date(article.publishedAt).getTime())) {
    return false;
  }

  if (!article.summary && !article.content) {
    return false;
  }

  if (containsAdKeyword(article.title)) {
    return false;
  }

  return true;
}

function isValidUrl(url: string): boolean {
  try {
    const parsed = new URL(url);
    return parsed.protocol === "http:" || parsed.protocol === "https:";
  } catch {
    return false;
  }
}

function containsAdKeyword(title: string): boolean {
  return AD_KEYWORDS.some((keyword) => title.includes(keyword));
}

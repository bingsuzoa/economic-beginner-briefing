import type { Article } from "../../../domain/article.js";
import type { AnalyzedNews, SourceReference } from "../../../domain/analyzedNews.js";
import type { Briefing, BriefingMetadata } from "../../../domain/briefing.js";
import type { AIResponse, AIAnalyzedNews } from "../prompts/responseSchema.js";
import { nowISOStringKST } from "../../../utils/date.js";

export interface BuildBriefingOptions {
  aiResponse: AIResponse;
  targetDate: string;
  articles: Article[];
  modelName: string;
  promptVersion: string;
}

export function buildBriefingFromAIResponse(
  options: BuildBriefingOptions,
): Briefing {
  const { aiResponse, targetDate, articles, modelName, promptVersion } = options;

  const articleMap = new Map(articles.map((a) => [a.id, a]));

  const news: AnalyzedNews[] = aiResponse.news.map((aiNews) =>
    mapAINewsToAnalyzedNews(aiNews, articleMap),
  );

  const metadata: BriefingMetadata = {
    collectedArticleCount: articles.length,
    analyzedArticleCount: articles.length,
    selectedNewsCount: news.length,
    modelName,
    promptVersion,
  };

  return {
    id: `briefing-${targetDate}`,
    targetDate,
    generatedAt: nowISOStringKST(),
    title: `${targetDate} 경제 브리핑`,
    overallSummary: aiResponse.overallSummary,
    news,
    glossary: aiResponse.glossary,
    metadata,
  };
}

function mapAINewsToAnalyzedNews(
  aiNews: AIAnalyzedNews,
  articleMap: Map<string, Article>,
): AnalyzedNews {
  const sources: SourceReference[] = aiNews.sources.map((src) => {
    const article = articleMap.get(src.articleId);
    return {
      articleId: src.articleId,
      sourceName: article?.sourceName ?? "Unknown",
      title: article?.title ?? "Unknown",
      url: article?.url ?? "https://unknown",
      publishedAt: article?.publishedAt ?? "1970-01-01T00:00:00+09:00",
      isPrimary: src.isPrimary,
    };
  });

  return {
    id: aiNews.id,
    representativeTitle: aiNews.representativeTitle,
    category: aiNews.category,
    importance: aiNews.importance,
    relevanceReason: aiNews.relevanceReason,
    oneLineSummary: aiNews.oneLineSummary,
    explanation: aiNews.explanation,
    expectedNextEffects: aiNews.expectedNextEffects,
    recommendedChecks: aiNews.recommendedChecks,
    evidenceStatus: aiNews.evidenceStatus,
    uncertaintyNote: aiNews.uncertaintyNote,
    economicTerms: aiNews.economicTerms,
    sources,
  };
}

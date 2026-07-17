import type { NewsAnalyzer } from "../NewsAnalyzer.js";
import type { AnalyzeNewsRequest, AnalyzeNewsResult } from "../../domain/analyzedNews.js";
import { BriefingSchema } from "../../domain/briefing.js";
import { AppError } from "../../errors/AppError.js";
import type { AIClient } from "./OpenAIClient.js";
import { SYSTEM_PROMPT } from "./prompts/systemPrompt.js";
import { buildAnalysisPrompt } from "./prompts/buildAnalysisPrompt.js";
import { AIResponseSchema } from "./prompts/responseSchema.js";
import { retryWithBackoff } from "./utils/retryWithBackoff.js";
import { buildBriefingFromAIResponse } from "./utils/buildBriefingFromAIResponse.js";

export interface OpenAINewsAnalyzerOptions {
  aiClient: AIClient;
  modelName?: string;
  promptVersion?: string;
}

export class OpenAINewsAnalyzer implements NewsAnalyzer {
  private readonly aiClient: AIClient;
  private readonly modelName: string;
  private readonly promptVersion: string;

  constructor(options: OpenAINewsAnalyzerOptions) {
    this.aiClient = options.aiClient;
    this.modelName = options.modelName ?? "gpt-4o";
    this.promptVersion = options.promptVersion ?? "v1";
  }

  async analyze(request: AnalyzeNewsRequest): Promise<AnalyzeNewsResult> {
    if (request.articles.length === 0) {
      throw new AppError({
        code: "ANALYZE_EMPTY_INPUT",
        stage: "analyze",
        retryable: false,
        safeMessage: "No articles to analyze",
      });
    }

    const userPrompt = buildAnalysisPrompt({
      articles: request.articles,
      targetDate: request.targetDate,
      maxSelectedNews: request.maxSelectedNews,
      audience: request.audience,
    });

    const aiResponse = await retryWithBackoff(async () => {
      const response = await this.aiClient.complete({
        systemPrompt: SYSTEM_PROMPT,
        userPrompt,
      });

      const parsed = parseJSON(response.content);
      const validated = validateAIResponse(parsed);
      return validated;
    });

    const briefing = buildBriefingFromAIResponse({
      aiResponse,
      targetDate: request.targetDate,
      articles: request.articles,
      modelName: this.modelName,
      promptVersion: this.promptVersion,
    });

    const briefingValidation = BriefingSchema.safeParse(briefing);
    if (!briefingValidation.success) {
      throw new AppError({
        code: "ANALYZE_VALIDATION_ERROR",
        stage: "analyze",
        retryable: false,
        safeMessage: `Briefing validation failed: ${briefingValidation.error.message}`,
      });
    }

    const selectedIds = new Set(
      aiResponse.news.flatMap((n) => n.sources.map((s) => s.articleId)),
    );
    const rejectedArticleIds = request.articles
      .filter((a) => !selectedIds.has(a.id))
      .map((a) => a.id);

    return {
      briefing: briefingValidation.data,
      rejectedArticleIds,
      warnings: [],
    };
  }
}

function parseJSON(content: string): unknown {
  try {
    return JSON.parse(content);
  } catch {
    throw new AppError({
      code: "ANALYZE_VALIDATION_ERROR",
      stage: "analyze",
      retryable: true,
      safeMessage: "AI response is not valid JSON",
    });
  }
}

function validateAIResponse(data: unknown) {
  const result = AIResponseSchema.safeParse(data);
  if (!result.success) {
    throw new AppError({
      code: "ANALYZE_VALIDATION_ERROR",
      stage: "analyze",
      retryable: true,
      safeMessage: `AI response validation failed: ${result.error.message}`,
    });
  }
  return result.data;
}

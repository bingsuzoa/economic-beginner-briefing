import { z } from "zod";
import { NewsCategoryValues } from "../../../domain/article.js";
import { NewsEvidenceStatusValues } from "../../../domain/analyzedNews.js";

const AIEconomicTermSchema = z.object({
  term: z.string().min(1),
  explanation: z.string().min(1),
  example: z.string().optional(),
});

const AISourceReferenceSchema = z.object({
  articleId: z.string().min(1),
  isPrimary: z.boolean(),
});

const AIAnalyzedNewsSchema = z.object({
  id: z.string().min(1),
  representativeTitle: z.string().min(1),
  category: z.enum(NewsCategoryValues),
  importance: z.union([
    z.literal(1),
    z.literal(2),
    z.literal(3),
    z.literal(4),
    z.literal(5),
  ]),
  relevanceReason: z.string().min(1),

  oneLineSummary: z.string().min(1),
  explanation: z.string().min(1),
  expectedNextEffects: z.array(z.string()),
  recommendedChecks: z.array(z.string()),

  evidenceStatus: z.enum(NewsEvidenceStatusValues),
  uncertaintyNote: z.string().optional(),

  economicTerms: z.array(AIEconomicTermSchema),
  sources: z.array(AISourceReferenceSchema),
});

export const AIResponseSchema = z.object({
  overallSummary: z.array(z.string().min(1)),
  news: z.array(AIAnalyzedNewsSchema).min(1),
  glossary: z.array(AIEconomicTermSchema),
});

export type AIResponse = z.infer<typeof AIResponseSchema>;
export type AIAnalyzedNews = z.infer<typeof AIAnalyzedNewsSchema>;
export type AISourceReference = z.infer<typeof AISourceReferenceSchema>;

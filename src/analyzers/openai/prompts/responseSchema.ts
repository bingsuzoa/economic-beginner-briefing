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

const AIImpactScoreSchema = z.object({
  target: z.string().min(1),
  score: z.number().int().min(0).max(5),
  reason: z.string().min(1),
});

const AITargetAudienceSchema = z.object({
  mustRead: z.array(z.string().min(1)),
  notRelevant: z.array(z.string()),
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
  whyImportant: z.string().min(1),
  targetAudience: AITargetAudienceSchema.optional(),

  impactAssessment: z.array(AIImpactScoreSchema).optional(),
  oneLineSummary: z.string().min(1),
  explanation: z.string().min(1),

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
export type AITargetAudience = z.infer<typeof AITargetAudienceSchema>;

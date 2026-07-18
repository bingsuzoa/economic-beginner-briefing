import { z } from "zod";
import {
  ISODateSchema,
  ISODateTimeSchema,
  UrlStringSchema,
  NewsCategorySchema,
  ArticleSchema,
} from "./article.js";
import type { ISODate, Article } from "./article.js";
import type { Briefing } from "./briefing.js";

// --- NewsEvidenceStatus ---

export const NewsEvidenceStatusValues = [
  "confirmed",
  "proposed",
  "expected",
] as const;

export const NewsEvidenceStatusSchema = z.enum(NewsEvidenceStatusValues);

export type NewsEvidenceStatus = z.infer<typeof NewsEvidenceStatusSchema>;

// --- ImpactScore ---

export const ImpactScoreSchema = z.object({
  target: z.string().min(1),
  score: z.number().int().min(0).max(5),
  reason: z.string().min(1),
});

export type ImpactScore = z.infer<typeof ImpactScoreSchema>;

// --- EconomicTerm ---

export const EconomicTermSchema = z.object({
  term: z.string().min(1),
  explanation: z.string().min(1),
  example: z.string().optional(),
});

export type EconomicTerm = z.infer<typeof EconomicTermSchema>;

// --- SourceReference ---

export const SourceReferenceSchema = z.object({
  articleId: z.string().min(1),
  sourceName: z.string().min(1),
  title: z.string().min(1),
  url: UrlStringSchema,
  publishedAt: ISODateTimeSchema,
  isPrimary: z.boolean(),
});

export type SourceReference = z.infer<typeof SourceReferenceSchema>;

// --- AnalyzedNews ---

export const AnalyzedNewsSchema = z.object({
  id: z.string().min(1),
  representativeTitle: z.string().min(1),
  category: NewsCategorySchema,
  importance: z.union([
    z.literal(1),
    z.literal(2),
    z.literal(3),
    z.literal(4),
    z.literal(5),
  ]),
  relevanceReason: z.string().min(1),

  impactAssessment: z.array(ImpactScoreSchema).optional(),
  oneLineSummary: z.string().min(1),
  explanation: z.string().min(1),
  expectedNextEffects: z.array(z.string()),
  recommendedChecks: z.array(z.string()),

  evidenceStatus: NewsEvidenceStatusSchema,
  uncertaintyNote: z.string().optional(),

  economicTerms: z.array(EconomicTermSchema),
  sources: z.array(SourceReferenceSchema),
});

export type AnalyzedNews = z.infer<typeof AnalyzedNewsSchema>;

// --- AudienceProfile ---

export const AudienceProfileSchema = z.object({
  economicKnowledgeLevel: z.literal("beginner"),
  interests: z.array(NewsCategorySchema),
  contextNotes: z.array(z.string()),
});

export type AudienceProfile = z.infer<typeof AudienceProfileSchema>;

// --- AnalyzeNewsRequest ---

export const AnalyzeNewsRequestSchema = z.object({
  targetDate: ISODateSchema,
  articles: z.array(ArticleSchema),
  maxSelectedNews: z.number().int().positive(),
  audience: AudienceProfileSchema,
  briefingTitle: z.string().optional(),
});

export interface AnalyzeNewsRequest {
  targetDate: ISODate;
  articles: Article[];
  maxSelectedNews: number;
  audience: AudienceProfile;
  /** Optional custom title for the briefing (e.g., hourly title). */
  briefingTitle?: string;
}

// --- AnalyzeNewsResult ---

export interface AnalyzeNewsResult {
  briefing: Briefing;
  rejectedArticleIds: string[];
  warnings: string[];
}

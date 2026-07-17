import { z } from "zod";
import {
  ISODateSchema,
  ISODateTimeSchema,
} from "./article.js";
import {
  AnalyzedNewsSchema,
  EconomicTermSchema,
} from "./analyzedNews.js";

// --- BriefingMetadata ---

export const BriefingMetadataSchema = z.object({
  collectedArticleCount: z.number().int().nonnegative(),
  analyzedArticleCount: z.number().int().nonnegative(),
  selectedNewsCount: z.number().int().nonnegative(),
  modelName: z.string().optional(),
  promptVersion: z.string().optional(),
});

export type BriefingMetadata = z.infer<typeof BriefingMetadataSchema>;

// --- Briefing ---

export const BriefingSchema = z.object({
  id: z.string().min(1),
  targetDate: ISODateSchema,
  generatedAt: ISODateTimeSchema,
  title: z.string().min(1),
  overallSummary: z.array(z.string()),
  news: z.array(AnalyzedNewsSchema),
  glossary: z.array(EconomicTermSchema),
  metadata: BriefingMetadataSchema,
});

export type Briefing = z.infer<typeof BriefingSchema>;

// --- PublishBriefingRequest ---

export const PublishBriefingRequestSchema = z.object({
  briefing: BriefingSchema,
  dryRun: z.boolean(),
});

export type PublishBriefingRequest = z.infer<typeof PublishBriefingRequestSchema>;

// --- PublishChannelResult ---

export const PublishChannelResultSchema = z.object({
  channel: z.enum(["email", "notion", "mock"]),
  status: z.enum(["success", "skipped", "failed"]),
  externalId: z.string().optional(),
  errorCode: z.string().optional(),
  errorMessage: z.string().optional(),
});

export type PublishChannelResult = z.infer<typeof PublishChannelResultSchema>;

// --- PublishBriefingResult ---

export const PublishBriefingResultSchema = z.object({
  briefingId: z.string().min(1),
  results: z.array(PublishChannelResultSchema),
  completedAt: ISODateTimeSchema,
});

export type PublishBriefingResult = z.infer<typeof PublishBriefingResultSchema>;

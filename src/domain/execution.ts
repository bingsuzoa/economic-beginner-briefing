import { z } from "zod";
import { ISODateSchema, ISODateTimeSchema } from "./article.js";

// --- ExecutionError ---

export const ExecutionErrorSchema = z.object({
  stage: z.enum(["collect", "analyze", "publish", "system"]),
  code: z.string().min(1),
  message: z.string(),
  retryable: z.boolean(),
  sourceName: z.string().optional(),
});

export type ExecutionError = z.infer<typeof ExecutionErrorSchema>;

// --- ExecutionLog ---

export const ExecutionLogSchema = z.object({
  executionId: z.string().min(1),
  targetDate: ISODateSchema,
  startedAt: ISODateTimeSchema,
  completedAt: ISODateTimeSchema.optional(),
  status: z.enum(["running", "success", "partial_success", "failed"]),
  collectedArticleCount: z.number().int().nonnegative(),
  selectedNewsCount: z.number().int().nonnegative(),
  errors: z.array(ExecutionErrorSchema),
});

export type ExecutionLog = z.infer<typeof ExecutionLogSchema>;

// --- PublicationDecision ---

export const PublicationDecisionValues = [
  "publish",
  "skip_already_published",
  "retry_previous_failure",
] as const;

export const PublicationDecisionSchema = z.enum(PublicationDecisionValues);

export type PublicationDecision = z.infer<typeof PublicationDecisionSchema>;

import { z } from "zod";

// --- Base types ---

export type ISODate = string;
export type ISODateTime = string;
export type UrlString = string;

export const ISODateSchema = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/, "ISO date format required (YYYY-MM-DD)");

export const ISODateTimeSchema = z
  .string()
  .regex(
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/,
    "ISO datetime format required",
  );

export const UrlStringSchema = z
  .string()
  .url("Valid URL required");

// --- NewsCategory ---

export const NewsCategoryValues = [
  "interest_rate",
  "deposit_saving",
  "loan",
  "housing",
  "jeonse_monthly_rent",
  "subscription",
  "tax",
  "pension",
  "insurance",
  "cost_of_living",
  "exchange_rate",
  "investment",
  "government_support",
  "employment_income",
  "household_debt",
  "other",
] as const;

export const NewsCategorySchema = z.enum(NewsCategoryValues);

export type NewsCategory = z.infer<typeof NewsCategorySchema>;

// --- ArticleSourceType ---

export const ArticleSourceTypeValues = [
  "news_media",
  "government",
  "public_institution",
  "financial_institution",
  "other",
] as const;

export const ArticleSourceTypeSchema = z.enum(ArticleSourceTypeValues);

export type ArticleSourceType = z.infer<typeof ArticleSourceTypeSchema>;

// --- Article ---

export const ArticleSchema = z.object({
  id: z.string().min(1),
  title: z.string().min(1, "Title must not be empty"),
  summary: z.string(),
  sourceName: z.string().min(1),
  sourceType: ArticleSourceTypeSchema,
  publishedAt: ISODateTimeSchema,
  collectedAt: ISODateTimeSchema,
  url: UrlStringSchema,
  categories: z.array(NewsCategorySchema),
  language: z.literal("ko"),
  content: z.string().optional(),
});

export type Article = z.infer<typeof ArticleSchema>;

// --- CollectNewsRequest ---

export const CollectNewsRequestSchema = z.object({
  targetDate: ISODateSchema,
  timezone: z.literal("Asia/Seoul"),
  maxArticles: z.number().int().positive().optional(),
});

export type CollectNewsRequest = z.infer<typeof CollectNewsRequestSchema>;

// --- SourceCollectionReport ---

export const SourceCollectionReportSchema = z.object({
  sourceName: z.string(),
  status: z.enum(["success", "partial", "failed"]),
  collectedCount: z.number().int().nonnegative(),
  acceptedCount: z.number().int().nonnegative(),
  errorCode: z.string().optional(),
  errorMessage: z.string().optional(),
});

export type SourceCollectionReport = z.infer<typeof SourceCollectionReportSchema>;

// --- CollectNewsResult ---

export const CollectNewsResultSchema = z.object({
  targetDate: ISODateSchema,
  articles: z.array(ArticleSchema),
  sourceReports: z.array(SourceCollectionReportSchema),
  totalCollected: z.number().int().nonnegative(),
  totalAccepted: z.number().int().nonnegative(),
  totalRejected: z.number().int().nonnegative(),
});

export type CollectNewsResult = z.infer<typeof CollectNewsResultSchema>;

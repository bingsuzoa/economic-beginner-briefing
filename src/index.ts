// Domain types
export type {
  ISODate,
  ISODateTime,
  UrlString,
  NewsCategory,
  ArticleSourceType,
  Article,
  CollectNewsRequest,
  CollectNewsResult,
  SourceCollectionReport,
} from "./domain/article.js";

export type {
  NewsEvidenceStatus,
  EconomicTerm,
  SourceReference,
  AnalyzedNews,
  AudienceProfile,
  AnalyzeNewsRequest,
  AnalyzeNewsResult,
} from "./domain/analyzedNews.js";

export type {
  BriefingMetadata,
  Briefing,
  PublishBriefingRequest,
  PublishChannelResult,
  PublishBriefingResult,
} from "./domain/briefing.js";

export type {
  ExecutionError,
  ExecutionLog,
  PublicationDecision,
} from "./domain/execution.js";

// Schemas
export {
  ISODateSchema,
  ISODateTimeSchema,
  UrlStringSchema,
  NewsCategorySchema,
  ArticleSourceTypeSchema,
  ArticleSchema,
  CollectNewsRequestSchema,
  CollectNewsResultSchema,
  SourceCollectionReportSchema,
} from "./domain/article.js";

export {
  NewsEvidenceStatusSchema,
  EconomicTermSchema,
  SourceReferenceSchema,
  AnalyzedNewsSchema,
  AudienceProfileSchema,
  AnalyzeNewsRequestSchema,
} from "./domain/analyzedNews.js";

export {
  BriefingMetadataSchema,
  BriefingSchema,
  PublishBriefingRequestSchema,
  PublishChannelResultSchema,
  PublishBriefingResultSchema,
} from "./domain/briefing.js";

export {
  ExecutionErrorSchema,
  ExecutionLogSchema,
  PublicationDecisionSchema,
} from "./domain/execution.js";

// Interfaces
export type { NewsCollector } from "./collectors/NewsCollector.js";
export type { NewsAnalyzer } from "./analyzers/NewsAnalyzer.js";
export type { BriefingPublisher } from "./publishers/BriefingPublisher.js";

// Mock implementations
export { MockNewsCollector } from "./collectors/mock/MockNewsCollector.js";
export { MockNewsAnalyzer } from "./analyzers/mock/MockNewsAnalyzer.js";
export { MockBriefingPublisher } from "./publishers/mock/MockBriefingPublisher.js";

// Real implementations
export { RealNewsCollector, createDefaultAdapters } from "./collectors/RealNewsCollector.js";

// Source adapters
export type { SourceAdapter, SourceCollectionResult } from "./collectors/sources/SourceAdapter.js";
export { YonhapSourceAdapter } from "./collectors/sources/YonhapSourceAdapter.js";
export { HankyungSourceAdapter } from "./collectors/sources/HankyungSourceAdapter.js";
export { MKSourceAdapter } from "./collectors/sources/MKSourceAdapter.js";
export { SBSBizSourceAdapter } from "./collectors/sources/SBSBizSourceAdapter.js";
export { KBSSourceAdapter } from "./collectors/sources/KBSSourceAdapter.js";

// Filters
export { filterByDate } from "./collectors/filters/dateFilter.js";
export { classifyCategories, containsExcludedTopic } from "./collectors/filters/categoryClassifier.js";
export { removeDuplicates, normalizeUrl } from "./collectors/filters/duplicateRemover.js";
export { validateQuality } from "./collectors/filters/qualityValidator.js";

// App
export { createApplication } from "./app/createApplication.js";
export type { ApplicationDeps } from "./app/createApplication.js";
export { runDailyBriefing } from "./app/runDailyBriefing.js";

// Pipeline validation
export {
  validateCollectResult,
  validateAnalyzeResult,
} from "./app/validatePipelineData.js";
export type {
  ValidationWarning,
  CollectValidationResult,
  AnalyzeValidationResult,
} from "./app/validatePipelineData.js";

// Execution tracking
export type { ExecutionTracker } from "./app/ExecutionTracker.js";
export { MockExecutionTracker } from "./app/ExecutionTracker.js";

// Config
export { loadEnv } from "./config/env.js";
export type { AppEnv } from "./config/env.js";
export { TIMEZONE, DEFAULT_MAX_SELECTED_NEWS, DEFAULT_AUDIENCE, TIMEOUTS, RETRY } from "./config/constants.js";

// Errors
export { AppError } from "./errors/AppError.js";
export { ErrorCodes } from "./errors/errorCodes.js";
export type { ErrorCode } from "./errors/errorCodes.js";

// Utils
export { getYesterdayDateInKST, getTargetDateStartKST, getTargetDateEndKST, validateISODate, nowISOStringKST } from "./utils/date.js";
export { ok, err } from "./utils/result.js";
export type { Result } from "./utils/result.js";

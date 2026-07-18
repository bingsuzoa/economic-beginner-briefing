import type { NewsCollector } from "../collectors/NewsCollector.js";
import type { NewsAnalyzer } from "../analyzers/NewsAnalyzer.js";
import type { BriefingPublisher } from "../publishers/BriefingPublisher.js";
import type { AudienceProfile } from "../domain/analyzedNews.js";
import type { AppEnv } from "../config/env.js";
import type { ExecutionTracker } from "./ExecutionTracker.js";

export interface ApplicationDeps {
  collector: NewsCollector;
  analyzer: NewsAnalyzer;
  publisher: BriefingPublisher;
  env: AppEnv;
  audience: AudienceProfile;
  executionTracker?: ExecutionTracker;
}

export function createApplication(deps: ApplicationDeps): ApplicationDeps {
  if (!deps.collector) {
    throw new Error("collector is required");
  }
  if (!deps.analyzer) {
    throw new Error("analyzer is required");
  }
  if (!deps.publisher) {
    throw new Error("publisher is required");
  }
  if (!deps.env) {
    throw new Error("env is required");
  }
  if (!deps.audience) {
    throw new Error("audience is required");
  }
  return deps;
}

import type { NewsCollector } from "../collectors/NewsCollector.js";
import type { NewsAnalyzer } from "../analyzers/NewsAnalyzer.js";
import type { BriefingPublisher } from "../publishers/BriefingPublisher.js";
import type { AudienceProfile } from "../domain/analyzedNews.js";
import type { AppEnv } from "../config/env.js";

export interface ApplicationDeps {
  collector: NewsCollector;
  analyzer: NewsAnalyzer;
  publisher: BriefingPublisher;
  env: AppEnv;
  audience: AudienceProfile;
}

export function createApplication(deps: ApplicationDeps): ApplicationDeps {
  return deps;
}

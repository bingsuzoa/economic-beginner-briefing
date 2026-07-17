import { MockNewsAnalyzer } from "../analyzers/mock/MockNewsAnalyzer.js";
import { MockNewsCollector } from "../collectors/mock/MockNewsCollector.js";
import { DEFAULT_AUDIENCE } from "../config/constants.js";
import type { AppEnv } from "../config/env.js";
import { MockBriefingPublisher } from "../publishers/mock/MockBriefingPublisher.js";
import { createApplication, type ApplicationDeps } from "./createApplication.js";

export function createDefaultApplication(env: AppEnv): ApplicationDeps {
  return createApplication({
    collector: new MockNewsCollector(),
    analyzer: new MockNewsAnalyzer(),
    publisher: new MockBriefingPublisher(),
    env,
    audience: DEFAULT_AUDIENCE,
  });
}

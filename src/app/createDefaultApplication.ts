import { MockNewsAnalyzer } from "../analyzers/mock/MockNewsAnalyzer.js";
import { OpenAINewsAnalyzer } from "../analyzers/openai/OpenAINewsAnalyzer.js";
import { OpenAIClient } from "../analyzers/openai/OpenAIClient.js";
import { MockNewsCollector } from "../collectors/mock/MockNewsCollector.js";
import { RealNewsCollector, createDefaultAdapters } from "../collectors/RealNewsCollector.js";
import { DEFAULT_AUDIENCE } from "../config/constants.js";
import type { AppEnv } from "../config/env.js";
import { MockBriefingPublisher } from "../publishers/mock/MockBriefingPublisher.js";
import { NotionBriefingPublisher } from "../publishers/notion/NotionBriefingPublisher.js";
import { NotionClientAdapter } from "../publishers/notion/NotionClientAdapter.js";
import { MockExecutionTracker } from "./ExecutionTracker.js";
import { createApplication, type ApplicationDeps } from "./createApplication.js";

export function createDefaultApplication(env: AppEnv): ApplicationDeps {
  const collector = env.DRY_RUN
    ? new MockNewsCollector()
    : new RealNewsCollector(createDefaultAdapters());

  const analyzer = env.OPENAI_API_KEY
    ? new OpenAINewsAnalyzer({
        aiClient: new OpenAIClient({ apiKey: env.OPENAI_API_KEY }),
      })
    : new MockNewsAnalyzer();

  const publisher =
    env.NOTION_API_KEY && env.NOTION_DATABASE_ID
      ? new NotionBriefingPublisher({
          databaseId: env.NOTION_DATABASE_ID,
          client: new NotionClientAdapter(env.NOTION_API_KEY),
        })
      : new MockBriefingPublisher();

  return createApplication({
    collector,
    analyzer,
    publisher,
    env,
    audience: DEFAULT_AUDIENCE,
    executionTracker: new MockExecutionTracker(),
  });
}

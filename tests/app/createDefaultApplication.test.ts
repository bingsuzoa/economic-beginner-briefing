import { describe, it, expect } from "vitest";
import { createDefaultApplication } from "../../src/app/createDefaultApplication.js";
import { loadEnv } from "../../src/config/env.js";
import { MockNewsCollector } from "../../src/collectors/mock/MockNewsCollector.js";
import { MockNewsAnalyzer } from "../../src/analyzers/mock/MockNewsAnalyzer.js";
import { MockBriefingPublisher } from "../../src/publishers/mock/MockBriefingPublisher.js";

describe("createDefaultApplication", () => {
  it("uses mock collector in DRY_RUN mode", () => {
    const env = loadEnv({
      NODE_ENV: "test",
      DRY_RUN: "true",
    });
    const app = createDefaultApplication(env);

    expect(app.collector).toBeInstanceOf(MockNewsCollector);
  });

  it("uses mock analyzer when OPENAI_API_KEY is not set", () => {
    const env = loadEnv({
      NODE_ENV: "test",
      DRY_RUN: "true",
    });
    const app = createDefaultApplication(env);

    expect(app.analyzer).toBeInstanceOf(MockNewsAnalyzer);
  });

  it("uses mock publisher when NOTION keys are not set", () => {
    const env = loadEnv({
      NODE_ENV: "test",
      DRY_RUN: "true",
    });
    const app = createDefaultApplication(env);

    expect(app.publisher).toBeInstanceOf(MockBriefingPublisher);
  });

  it("includes executionTracker", () => {
    const env = loadEnv({
      NODE_ENV: "test",
      DRY_RUN: "true",
    });
    const app = createDefaultApplication(env);

    expect(app.executionTracker).toBeDefined();
  });

  it("includes audience profile", () => {
    const env = loadEnv({
      NODE_ENV: "test",
      DRY_RUN: "true",
    });
    const app = createDefaultApplication(env);

    expect(app.audience).toBeDefined();
    expect(app.audience.economicKnowledgeLevel).toBe("beginner");
  });
});

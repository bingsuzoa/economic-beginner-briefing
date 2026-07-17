import { describe, it, expect } from "vitest";
import { loadEnv } from "../../src/config/env.js";

describe("loadEnv", () => {
  it("loads defaults when no env vars are set", () => {
    const env = loadEnv({});

    expect(env.NODE_ENV).toBe("development");
    expect(env.TZ).toBe("Asia/Seoul");
    expect(env.DRY_RUN).toBe(true);
    expect(env.LOG_LEVEL).toBe("info");
  });

  it("parses DRY_RUN=false correctly", () => {
    const env = loadEnv({ DRY_RUN: "false" });

    expect(env.DRY_RUN).toBe(false);
  });

  it("accepts valid NODE_ENV values", () => {
    expect(loadEnv({ NODE_ENV: "production" }).NODE_ENV).toBe("production");
    expect(loadEnv({ NODE_ENV: "test" }).NODE_ENV).toBe("test");
    expect(loadEnv({ NODE_ENV: "development" }).NODE_ENV).toBe("development");
  });

  it("rejects invalid NODE_ENV", () => {
    expect(() => loadEnv({ NODE_ENV: "staging" })).toThrow();
  });

  it("treats API keys as optional", () => {
    const env = loadEnv({});

    expect(env.OPENAI_API_KEY).toBeUndefined();
    expect(env.NOTION_API_KEY).toBeUndefined();
    expect(env.NOTION_DATABASE_ID).toBeUndefined();
    expect(env.EMAIL_API_KEY).toBeUndefined();
  });

  it("preserves API keys when provided", () => {
    const env = loadEnv({
      OPENAI_API_KEY: "test-key",
      NOTION_API_KEY: "notion-key",
      NOTION_DATABASE_ID: "db-id",
    });

    expect(env.OPENAI_API_KEY).toBe("test-key");
    expect(env.NOTION_API_KEY).toBe("notion-key");
    expect(env.NOTION_DATABASE_ID).toBe("db-id");
  });

  it("rejects invalid LOG_LEVEL", () => {
    expect(() => loadEnv({ LOG_LEVEL: "verbose" })).toThrow();
  });
});

import { describe, it, expect, vi } from "vitest";
import { OpenAINewsAnalyzer } from "../../../src/analyzers/openai/OpenAINewsAnalyzer.js";
import type { AIClient, AIClientRequest } from "../../../src/analyzers/openai/OpenAIClient.js";
import type { AnalyzeNewsRequest } from "../../../src/domain/analyzedNews.js";
import { BriefingSchema } from "../../../src/domain/briefing.js";
import { AppError } from "../../../src/errors/AppError.js";
import { DEFAULT_AUDIENCE } from "../../../src/config/constants.js";
import { sampleArticles } from "./fixtures/sampleArticles.js";
import { validAIResponse, invalidJSONResponse, emptyNewsResponse } from "./fixtures/sampleAIResponses.js";

function createMockClient(response: string): AIClient {
  return {
    complete: vi.fn().mockResolvedValue({ content: response }),
  };
}

function createRequest(overrides?: Partial<AnalyzeNewsRequest>): AnalyzeNewsRequest {
  return {
    targetDate: "2026-07-16",
    articles: sampleArticles,
    maxSelectedNews: 10,
    audience: DEFAULT_AUDIENCE,
    ...overrides,
  };
}

describe("OpenAINewsAnalyzer", () => {
  it("returns a valid AnalyzeNewsResult with mock AI client", async () => {
    const client = createMockClient(JSON.stringify(validAIResponse));
    const analyzer = new OpenAINewsAnalyzer({ aiClient: client });
    const result = await analyzer.analyze(createRequest());

    expect(result.briefing).toBeDefined();
    expect(result.briefing.news.length).toBe(3);
    expect(result.rejectedArticleIds).toBeDefined();
    expect(result.warnings).toEqual([]);

    const validation = BriefingSchema.safeParse(result.briefing);
    expect(validation.success).toBe(true);
  });

  it("sets correct briefing metadata", async () => {
    const client = createMockClient(JSON.stringify(validAIResponse));
    const analyzer = new OpenAINewsAnalyzer({
      aiClient: client,
      modelName: "gpt-4o",
      promptVersion: "v1",
    });
    const result = await analyzer.analyze(createRequest());

    expect(result.briefing.metadata.modelName).toBe("gpt-4o");
    expect(result.briefing.metadata.promptVersion).toBe("v1");
    expect(result.briefing.metadata.collectedArticleCount).toBe(sampleArticles.length);
    expect(result.briefing.metadata.selectedNewsCount).toBe(3);
  });

  it("passes system prompt and user prompt to AI client", async () => {
    const client = createMockClient(JSON.stringify(validAIResponse));
    const analyzer = new OpenAINewsAnalyzer({ aiClient: client });
    await analyzer.analyze(createRequest());

    const call = (client.complete as ReturnType<typeof vi.fn>).mock.calls[0]![0] as AIClientRequest;
    expect(call.systemPrompt).toContain("경제 초보자");
    expect(call.userPrompt).toContain("2026-07-16");
    expect(call.userPrompt).toContain("art-interest-001");
  });

  it("identifies rejected article IDs", async () => {
    const client = createMockClient(JSON.stringify(validAIResponse));
    const analyzer = new OpenAINewsAnalyzer({ aiClient: client });
    const result = await analyzer.analyze(createRequest());

    // validAIResponse uses art-interest-001, art-housing-001, art-policy-001
    expect(result.rejectedArticleIds).toContain("art-exchange-001");
    expect(result.rejectedArticleIds).toContain("art-intl-001");
    expect(result.rejectedArticleIds).toContain("art-stock-001");
    expect(result.rejectedArticleIds).toContain("art-earnings-001");
    expect(result.rejectedArticleIds).not.toContain("art-interest-001");
  });

  it("throws ANALYZE_EMPTY_INPUT for empty articles", async () => {
    const client = createMockClient("");
    const analyzer = new OpenAINewsAnalyzer({ aiClient: client });

    await expect(
      analyzer.analyze(createRequest({ articles: [] })),
    ).rejects.toThrow("No articles to analyze");

    expect(client.complete).not.toHaveBeenCalled();
  });

  it("throws on invalid JSON response from AI", async () => {
    const client = createMockClient(invalidJSONResponse);
    const analyzer = new OpenAINewsAnalyzer({ aiClient: client });

    await expect(
      analyzer.analyze(createRequest()),
    ).rejects.toThrow("not valid JSON");
  });

  it("throws on empty news array from AI", async () => {
    const client = createMockClient(emptyNewsResponse);
    const analyzer = new OpenAINewsAnalyzer({ aiClient: client });

    await expect(
      analyzer.analyze(createRequest()),
    ).rejects.toThrow("validation failed");
  });

  it("retries on retryable errors from AI client", async () => {
    const retryableError = new AppError({
      code: "ANALYZE_API_ERROR",
      stage: "analyze",
      retryable: true,
      safeMessage: "rate limited",
    });

    const client: AIClient = {
      complete: vi
        .fn()
        .mockRejectedValueOnce(retryableError)
        .mockResolvedValueOnce({ content: JSON.stringify(validAIResponse) }),
    };

    const analyzer = new OpenAINewsAnalyzer({ aiClient: client });
    const result = await analyzer.analyze(createRequest());

    expect(result.briefing).toBeDefined();
    expect(client.complete).toHaveBeenCalledTimes(2);
  });

  it("does not retry non-retryable errors", async () => {
    const nonRetryableError = new AppError({
      code: "ANALYZE_API_ERROR",
      stage: "analyze",
      retryable: false,
      safeMessage: "invalid api key",
    });

    const client: AIClient = {
      complete: vi.fn().mockRejectedValue(nonRetryableError),
    };

    const analyzer = new OpenAINewsAnalyzer({ aiClient: client });

    await expect(analyzer.analyze(createRequest())).rejects.toThrow("invalid api key");
    expect(client.complete).toHaveBeenCalledTimes(1);
  });

  it("uses default modelName and promptVersion", async () => {
    const client = createMockClient(JSON.stringify(validAIResponse));
    const analyzer = new OpenAINewsAnalyzer({ aiClient: client });
    const result = await analyzer.analyze(createRequest());

    expect(result.briefing.metadata.modelName).toBe("gpt-4o");
    expect(result.briefing.metadata.promptVersion).toBe("v1");
  });
});

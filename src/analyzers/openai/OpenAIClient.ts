import OpenAI from "openai";
import { AppError } from "../../errors/AppError.js";
import { TIMEOUTS } from "../../config/constants.js";

export interface AIClientRequest {
  systemPrompt: string;
  userPrompt: string;
  model?: string;
  temperature?: number;
}

export interface AIClientResponse {
  content: string;
}

export interface AIClient {
  complete(request: AIClientRequest): Promise<AIClientResponse>;
}

export class OpenAIClient implements AIClient {
  private readonly client: OpenAI;
  private readonly defaultModel: string;

  constructor(options: { apiKey: string; model?: string; timeoutMs?: number }) {
    this.client = new OpenAI({
      apiKey: options.apiKey,
      timeout: options.timeoutMs ?? TIMEOUTS.AI_API,
    });
    this.defaultModel = options.model ?? "gpt-4o";
  }

  async complete(request: AIClientRequest): Promise<AIClientResponse> {
    try {
      const response = await this.client.chat.completions.create({
        model: request.model ?? this.defaultModel,
        temperature: request.temperature ?? 0.3,
        response_format: { type: "json_object" },
        messages: [
          { role: "system", content: request.systemPrompt },
          { role: "user", content: request.userPrompt },
        ],
      });

      const content = response.choices[0]?.message?.content;
      if (!content) {
        throw new AppError({
          code: "ANALYZE_API_ERROR",
          stage: "analyze",
          retryable: true,
          safeMessage: "AI API returned empty response",
        });
      }

      return { content };
    } catch (error) {
      if (error instanceof AppError) {
        throw error;
      }

      if (error instanceof OpenAI.APIError) {
        const retryable = error.status === 429 || error.status === 500 || error.status === 503;
        throw new AppError({
          code: "ANALYZE_API_ERROR",
          stage: "analyze",
          retryable,
          safeMessage: `OpenAI API error: ${error.status} ${error.message}`,
          cause: error,
        });
      }

      if (error instanceof OpenAI.APIConnectionError) {
        throw new AppError({
          code: "ANALYZE_TIMEOUT",
          stage: "analyze",
          retryable: true,
          safeMessage: "OpenAI API connection error",
          cause: error,
        });
      }

      throw new AppError({
        code: "ANALYZE_API_ERROR",
        stage: "analyze",
        retryable: false,
        safeMessage: "Unexpected error during AI API call",
        cause: error instanceof Error ? error : undefined,
      });
    }
  }
}

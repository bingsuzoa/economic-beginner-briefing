import type { ErrorCode } from "./errorCodes.js";

export type ErrorStage = "collect" | "analyze" | "publish" | "system";

export class AppError extends Error {
  readonly code: ErrorCode;
  readonly stage: ErrorStage;
  readonly retryable: boolean;
  readonly safeMessage: string;
  readonly cause?: Error;

  constructor(options: {
    code: ErrorCode;
    stage: ErrorStage;
    retryable: boolean;
    safeMessage: string;
    cause?: Error;
  }) {
    super(options.safeMessage);
    this.name = "AppError";
    this.code = options.code;
    this.stage = options.stage;
    this.retryable = options.retryable;
    this.safeMessage = options.safeMessage;
    this.cause = options.cause;
  }

  toExecutionError(): {
    stage: ErrorStage;
    code: string;
    message: string;
    retryable: boolean;
  } {
    return {
      stage: this.stage,
      code: this.code,
      message: this.safeMessage,
      retryable: this.retryable,
    };
  }
}

import { AppError } from "../../../errors/AppError.js";
import { RETRY } from "../../../config/constants.js";

export interface RetryOptions {
  maxAttempts?: number;
  initialDelayMs?: number;
  nextDelayMs?: number;
}

export async function retryWithBackoff<T>(
  fn: () => Promise<T>,
  options: RetryOptions = {},
): Promise<T> {
  const maxAttempts = options.maxAttempts ?? RETRY.MAX_ATTEMPTS;
  const delays = [
    options.initialDelayMs ?? RETRY.INITIAL_DELAY_MS,
    options.nextDelayMs ?? RETRY.NEXT_DELAY_MS,
  ];

  let lastError: unknown;

  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      return await fn();
    } catch (error) {
      lastError = error;

      const isLastAttempt = attempt >= maxAttempts;
      if (isLastAttempt) {
        break;
      }

      const isRetryable =
        error instanceof AppError ? error.retryable : false;
      if (!isRetryable) {
        break;
      }

      const delayIndex = Math.min(attempt - 1, delays.length - 1);
      const delay = delays[delayIndex] ?? delays[delays.length - 1]!;
      await sleep(delay);
    }
  }

  throw lastError;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

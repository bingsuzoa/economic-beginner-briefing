import { describe, it, expect, vi } from "vitest";
import { retryWithBackoff } from "../../../src/analyzers/openai/utils/retryWithBackoff.js";
import { AppError } from "../../../src/errors/AppError.js";

describe("retryWithBackoff", () => {
  it("returns the result on first success", async () => {
    const fn = vi.fn().mockResolvedValue("ok");
    const result = await retryWithBackoff(fn, { maxAttempts: 2 });
    expect(result).toBe("ok");
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it("retries on retryable AppError and succeeds on second attempt", async () => {
    const retryableError = new AppError({
      code: "ANALYZE_API_ERROR",
      stage: "analyze",
      retryable: true,
      safeMessage: "temporary error",
    });

    const fn = vi
      .fn()
      .mockRejectedValueOnce(retryableError)
      .mockResolvedValueOnce("ok");

    const result = await retryWithBackoff(fn, {
      maxAttempts: 2,
      initialDelayMs: 1,
      nextDelayMs: 1,
    });
    expect(result).toBe("ok");
    expect(fn).toHaveBeenCalledTimes(2);
  });

  it("does not retry non-retryable AppError", async () => {
    const nonRetryableError = new AppError({
      code: "ANALYZE_VALIDATION_ERROR",
      stage: "analyze",
      retryable: false,
      safeMessage: "validation failed",
    });

    const fn = vi.fn().mockRejectedValue(nonRetryableError);

    await expect(
      retryWithBackoff(fn, { maxAttempts: 3, initialDelayMs: 1 }),
    ).rejects.toThrow("validation failed");
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it("does not retry non-AppError exceptions", async () => {
    const fn = vi.fn().mockRejectedValue(new Error("unknown"));

    await expect(
      retryWithBackoff(fn, { maxAttempts: 3, initialDelayMs: 1 }),
    ).rejects.toThrow("unknown");
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it("throws last error after exhausting all attempts", async () => {
    const retryableError = new AppError({
      code: "ANALYZE_API_ERROR",
      stage: "analyze",
      retryable: true,
      safeMessage: "still failing",
    });

    const fn = vi.fn().mockRejectedValue(retryableError);

    await expect(
      retryWithBackoff(fn, { maxAttempts: 2, initialDelayMs: 1, nextDelayMs: 1 }),
    ).rejects.toThrow("still failing");
    expect(fn).toHaveBeenCalledTimes(2);
  });

  it("uses default RETRY constants when no options provided", async () => {
    const fn = vi.fn().mockResolvedValue("ok");
    const result = await retryWithBackoff(fn);
    expect(result).toBe("ok");
    expect(fn).toHaveBeenCalledTimes(1);
  });
});

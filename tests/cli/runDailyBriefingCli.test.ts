import { describe, it, expect, vi } from "vitest";
import { runDailyBriefingCli } from "../../src/cli/runDailyBriefingCli.js";

function findJsonOutput(consoleSpy: ReturnType<typeof vi.spyOn>): unknown {
  for (const call of consoleSpy.mock.calls) {
    const arg = call[0] as string;
    if (arg.startsWith("{")) {
      return JSON.parse(arg);
    }
  }
  throw new Error("No JSON output found in console.log calls");
}

describe("runDailyBriefingCli", () => {
  it("returns 0 on successful pipeline execution with mocks", async () => {
    const consoleSpy = vi.spyOn(console, "log").mockImplementation(() => {});

    const exitCode = await runDailyBriefingCli(
      ["--target-date", "2026-07-16"],
      {
        NODE_ENV: "test",
        DRY_RUN: "true",
        TZ: "Asia/Seoul",
      },
    );

    expect(exitCode).toBe(0);

    const output = findJsonOutput(consoleSpy) as Record<string, unknown>;
    expect((output.scheduler as Record<string, unknown>).mode).toBe("automatic");
    expect((output.scheduler as Record<string, unknown>).timezone).toBe("Asia/Seoul");
    expect((output.scheduler as Record<string, unknown>).requestedTargetDate).toBe("2026-07-16");
    expect((output.execution as Record<string, unknown>).status).toBe("success");
    expect((output.execution as Record<string, unknown>).targetDate).toBe("2026-07-16");
    expect((output.execution as Record<string, unknown>).collectedArticleCount).toBeGreaterThan(0);

    consoleSpy.mockRestore();
  });

  it("detects workflow_dispatch as manual mode", async () => {
    const consoleSpy = vi.spyOn(console, "log").mockImplementation(() => {});

    await runDailyBriefingCli(
      ["--target-date", "2026-07-16"],
      {
        NODE_ENV: "test",
        DRY_RUN: "true",
        TZ: "Asia/Seoul",
        GITHUB_EVENT_NAME: "workflow_dispatch",
      },
    );

    const output = findJsonOutput(consoleSpy) as Record<string, unknown>;
    expect((output.scheduler as Record<string, unknown>).mode).toBe("manual");

    consoleSpy.mockRestore();
  });

  it("uses yesterday date when no target date is specified", async () => {
    const consoleSpy = vi.spyOn(console, "log").mockImplementation(() => {});

    const exitCode = await runDailyBriefingCli(
      [],
      {
        NODE_ENV: "test",
        DRY_RUN: "true",
        TZ: "Asia/Seoul",
      },
    );

    expect(exitCode).toBe(0);

    const output = findJsonOutput(consoleSpy) as Record<string, unknown>;
    expect((output.scheduler as Record<string, unknown>).requestedTargetDate).toBeNull();
    expect((output.execution as Record<string, unknown>).targetDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);

    consoleSpy.mockRestore();
  });
});

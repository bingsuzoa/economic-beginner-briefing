import { describe, it, expect, vi } from "vitest";
import { runDailyBriefingCli } from "../../src/cli/runDailyBriefingCli.js";

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

    const output = JSON.parse(consoleSpy.mock.calls[0]![0] as string);
    expect(output.scheduler.mode).toBe("automatic");
    expect(output.scheduler.timezone).toBe("Asia/Seoul");
    expect(output.scheduler.requestedTargetDate).toBe("2026-07-16");
    expect(output.execution.status).toBe("success");
    expect(output.execution.targetDate).toBe("2026-07-16");
    expect(output.execution.collectedArticleCount).toBeGreaterThan(0);

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

    const output = JSON.parse(consoleSpy.mock.calls[0]![0] as string);
    expect(output.scheduler.mode).toBe("manual");

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

    const output = JSON.parse(consoleSpy.mock.calls[0]![0] as string);
    expect(output.scheduler.requestedTargetDate).toBeNull();
    expect(output.execution.targetDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);

    consoleSpy.mockRestore();
  });
});

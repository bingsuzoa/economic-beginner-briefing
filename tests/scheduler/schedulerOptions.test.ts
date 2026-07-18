import { describe, expect, it } from "vitest";
import { parseSchedulerOptions } from "../../src/scheduler/schedulerOptions.js";

describe("parseSchedulerOptions", () => {
  it("uses automatic mode and default target date when no target date is provided", () => {
    const options = parseSchedulerOptions([], {});

    expect(options).toEqual({
      targetDate: undefined,
      mode: "automatic",
    });
  });

  it("reads target date from cli option", () => {
    const options = parseSchedulerOptions(["--target-date", "2026-07-16"], {});

    expect(options.targetDate).toBe("2026-07-16");
  });

  it("prefers cli target date over TARGET_DATE env", () => {
    const options = parseSchedulerOptions(["--target-date=2026-07-16"], {
      TARGET_DATE: "2026-07-15",
    });

    expect(options.targetDate).toBe("2026-07-16");
  });

  it("marks workflow_dispatch runs as manual", () => {
    const options = parseSchedulerOptions([], {
      GITHUB_EVENT_NAME: "workflow_dispatch",
    });

    expect(options.mode).toBe("manual");
  });

  it("rejects invalid target date", () => {
    expect(() => parseSchedulerOptions(["--target-date", "2026-02-30"], {})).toThrow();
  });
});

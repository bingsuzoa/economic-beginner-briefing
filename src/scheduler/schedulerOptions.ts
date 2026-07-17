import { validateISODate } from "../utils/date.js";

export type SchedulerRunMode = "automatic" | "manual";

export interface SchedulerOptions {
  targetDate?: string;
  mode: SchedulerRunMode;
}

export function parseSchedulerOptions(
  argv: string[],
  env: Record<string, string | undefined>,
): SchedulerOptions {
  const targetDate = readTargetDate(argv, env);

  if (targetDate) {
    validateISODate(targetDate);
  }

  return {
    targetDate,
    mode: env.GITHUB_EVENT_NAME === "workflow_dispatch" ? "manual" : "automatic",
  };
}

function readTargetDate(
  argv: string[],
  env: Record<string, string | undefined>,
): string | undefined {
  const cliTargetDate = readOption(argv, "--target-date");
  if (cliTargetDate) {
    return cliTargetDate;
  }

  const envTargetDate = env.TARGET_DATE?.trim();
  return envTargetDate ? envTargetDate : undefined;
}

function readOption(argv: string[], optionName: string): string | undefined {
  for (let index = 0; index < argv.length; index += 1) {
    const value = argv[index];
    if (!value) {
      continue;
    }

    if (value === optionName) {
      const nextValue = argv[index + 1];
      return nextValue && !nextValue.startsWith("--") ? nextValue : undefined;
    }

    const prefix = `${optionName}=`;
    if (value.startsWith(prefix)) {
      return value.slice(prefix.length);
    }
  }

  return undefined;
}

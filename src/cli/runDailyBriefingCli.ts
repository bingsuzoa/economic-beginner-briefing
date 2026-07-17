import { createDefaultApplication } from "../app/createDefaultApplication.js";
import { runDailyBriefing } from "../app/runDailyBriefing.js";
import { loadEnv } from "../config/env.js";
import { parseSchedulerOptions } from "../scheduler/schedulerOptions.js";

export async function runDailyBriefingCli(
  argv: string[] = process.argv.slice(2),
  envSource: Record<string, string | undefined> = process.env,
): Promise<number> {
  const schedulerOptions = parseSchedulerOptions(argv, envSource);
  const env = loadEnv(envSource);
  const app = createDefaultApplication(env);

  const log = await runDailyBriefing(app, schedulerOptions.targetDate);

  const result = {
    scheduler: {
      mode: schedulerOptions.mode,
      timezone: "Asia/Seoul",
      requestedTargetDate: schedulerOptions.targetDate ?? null,
    },
    execution: log,
  };

  console.log(JSON.stringify(result, null, 2));

  return log.status === "success" ? 0 : 1;
}

export async function main(): Promise<void> {
  try {
    const exitCode = await runDailyBriefingCli();
    process.exitCode = exitCode;
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown scheduler error";
    console.error(message);
    process.exitCode = 1;
  }
}

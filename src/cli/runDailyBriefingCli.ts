import { createDefaultApplication } from "../app/createDefaultApplication.js";
import { runDailyBriefing } from "../app/runDailyBriefing.js";
import type { RunBriefingOptions } from "../app/runDailyBriefing.js";
import { loadEnv } from "../config/env.js";
import { parseSchedulerOptions } from "../scheduler/schedulerOptions.js";
import { getHourlyTimeRange } from "../utils/date.js";

export async function runDailyBriefingCli(
  argv: string[] = process.argv.slice(2),
  envSource: Record<string, string | undefined> = process.env,
): Promise<number> {
  const schedulerOptions = parseSchedulerOptions(argv, envSource);
  const env = loadEnv(envSource);
  const app = createDefaultApplication(env);

  const options: RunBriefingOptions = schedulerOptions.targetDate
    ? { targetDate: schedulerOptions.targetDate }
    : { timeRange: getHourlyTimeRange() };

  console.log("[briefing] Starting pipeline...");
  const startMs = Date.now();

  const log = await runDailyBriefing(app, options);

  const durationMs = Date.now() - startMs;
  console.log(`[briefing] Pipeline completed in ${durationMs}ms (status: ${log.status})`);

  const result = {
    scheduler: {
      mode: schedulerOptions.mode,
      timezone: "Asia/Seoul",
      requestedTargetDate: schedulerOptions.targetDate ?? null,
      timeRange: options.timeRange ?? null,
    },
    execution: log,
  };

  console.log(JSON.stringify(result, null, 2));

  return log.status === "success" ? 0 : 1;
}

export async function main(): Promise<void> {
  let exitCode = 0;
  try {
    exitCode = await runDailyBriefingCli();
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown scheduler error";
    console.error(`[briefing] Fatal error: ${message}`);
    exitCode = 1;
  }

  // External SDK clients (OpenAI, Notion) retain internal HTTP keep-alive
  // connections that prevent the Node.js event loop from draining naturally.
  // After the pipeline work is fully complete, we exit explicitly.
  process.exit(exitCode);
}

# Scheduler Operation

## Scope

The briefing pipeline runs hourly from a scheduler inside the application itself. There is no
CI-based trigger: the server that serves `/api/briefing/**` is also the thing that produces the
briefings.

The scheduler only decides *when*. Collection, classification, embedding and analysis all happen
in `BriefingPipeline`, reached through `PipelineExecutionService` — the same entry point the
admin API uses, so there is one execution path, not two.

```bash
java -jar build/libs/economic-briefing-0.1.0.jar
```

No Spring profile is required. `application.yml` is the operating configuration.

## Schedule

| Setting | Default | Notes |
|---------|---------|-------|
| `briefing.scheduler.enabled` (`SCHEDULER_ENABLED`) | `true` | `false` disables the trigger entirely |
| `briefing.scheduler.cron` (`SCHEDULER_CRON`) | `0 0 * * * *` | 6 fields (sec min hour day month weekday), evaluated in `Asia/Seoul` |

Each run collects the **previous** completed hour, so the 17:00 run covers 16:00–16:59.

A malformed cron does not stop the application. The trigger is left unarmed, an ERROR is logged,
and the public API keeps serving:

```
[Scheduler] MISCONFIGURED cron='0' - hourly briefings will NOT run.
```

State is reported at startup and on `/api/admin/status` as `ENABLED` / `DISABLED` / `MISCONFIGURED`.

## Target Date

With no target date the application resolves the window from the current hour in `Asia/Seoul`.

Recovery run for a specific date:

```bash
# via the admin API (server already running)
curl -X POST -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
     -d '{"targetDate":"2026-07-16"}' http://localhost:3000/api/admin/runs

# or as a one-shot CLI process
BRIEFING_CLI_ENABLED=true SCHEDULER_ENABLED=false \
  java -jar build/libs/economic-briefing-0.1.0.jar --target-date=2026-07-16
```

Always set `SCHEDULER_ENABLED=false` for a one-shot CLI run so it does not arm a second trigger.

## Duplicate Runs

Two independent guards, answering different questions:

| Guard | Question | Behaviour |
|-------|----------|-----------|
| `PipelineLock` | Is a run in flight right now? | `[Scheduler] Previous execution is still running. Skip.` |
| `pipeline_runs.dedupe_key` | Was this hour already published? | `Skipping already published: dedupeKey=2026-07-25T16` |

The dedupe key is `date` for manual runs and `date+hour` for scheduled ones, and it lives in the
database, so protection survives a restart.

Both guards are process-local for the lock and database-wide for the dedupe key. Running more
than one instance against the same database would need a distributed lock.

## Secrets

Read from the environment (see `.env.example`); never printed in logs:

- `OPENAI_API_KEY` — required
- `ADMIN_TOKEN` — required; a blank value stops startup and the admin API rejects every request
- `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`

In production these are read from `.env` by `scripts/install-service.ps1` and written into the
`EconomicBriefing` service's environment at install time. Editing `.env` therefore has no effect
until the service is re-registered — run `scripts/deploy.ps1` (or `install-service.ps1`) again.

## Monitoring

`GET /api/health/briefing` — unauthenticated, `200 UP` / `503 DOWN`.

DOWN when the database is unreachable, the cron is invalid, or no successful run has completed
within `briefing.health.max-success-age` (default `3h`, only enforced while the scheduler is
enabled).

```json
{"status":"DOWN","scheduler":"ENABLED","cron":"0 0 * * * *","dbConnected":true,
 "lastSuccessAgeMinutes":310,"reasons":["no successful run in 310m (limit 180m)"]}
```

Point an uptime check at this URL. Do not wire it to a container liveness probe — restarting the
process does not produce briefings, it only takes the website down.

## Failure Checks

A scheduled tick never throws; a failing run is recorded and the next tick still fires. To
investigate:

- `GET /api/admin/runs` — run history with status, counts and duration
- `GET /api/admin/runs/{runId}/logs` — per-step log for one run
- `GET /api/admin/runs/{runId}/items` — per-article results

Operational log lines are prefixed `[Scheduler]` or `[Admin]` by trigger source.

## Dry Run

`DRY_RUN=true` replaces every external call (RSS, OpenAI) with a mock. Use it to verify wiring
without spending tokens; it writes to the database as usual.

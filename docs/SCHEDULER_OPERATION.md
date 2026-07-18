# Scheduler Operation

## Scope

`feature/scheduler` schedules the existing daily briefing pipeline. It does not collect news, analyze news, or publish to Notion/email directly.

The scheduled command calls the same application entrypoint used for manual runs:

```bash
npm start
```

For local development before build:

```bash
npm run briefing:run
```

## Schedule

GitHub Actions runs daily at:

```text
04:30 Asia/Seoul
19:30 UTC
```

This keeps the run before the product target of 05:00 KST while avoiding server-local timezone assumptions.

## Target Date

If no target date is provided, the application resolves the target date as yesterday in `Asia/Seoul`.

Manual recovery runs can specify a date:

```bash
npm run briefing:run -- --target-date 2026-07-16
```

In GitHub Actions, use `workflow_dispatch` and set `target_date` to `YYYY-MM-DD`.

## Duplicate Runs

The workflow uses GitHub Actions `concurrency` with `cancel-in-progress: false`.

This prevents overlapping scheduled/manual executions for the same workflow group. Completed-run duplicate publication is still the responsibility of Integration and Publisher idempotency, especially Notion duplicate detection.

## Secrets

The workflow reads external service settings only from GitHub Actions secrets:

- `OPENAI_API_KEY`
- `NOTION_API_KEY`
- `NOTION_DATABASE_ID`
- `EMAIL_PROVIDER`
- `EMAIL_FROM`
- `EMAIL_TO`
- `EMAIL_API_KEY`

Secrets must not be printed in logs.

## Failure Checks

GitHub Actions marks the run failed when:

- dependency installation fails
- typecheck, lint, test, or build fails
- the briefing command returns a non-zero exit code

Check the failed workflow run logs in GitHub Actions. The briefing command prints a JSON execution result containing:

- scheduler mode
- timezone
- requested target date
- execution ID
- resolved target date
- start and completion time
- status
- collected article count
- selected news count
- structured errors

## Manual Dry Run

Manual GitHub Actions runs default to `dry_run=true`. Scheduled runs use `dry_run=false`.

Use manual dry runs for environment checks before enabling real publication.

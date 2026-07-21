# Scheduler Operation

## Scope

The scheduler runs the briefing pipeline on a schedule via GitHub Actions. It does not collect news, analyze news, or publish to Notion/email directly.

The scheduled command runs the Spring Boot application JAR:

```bash
java -jar build/libs/economic-briefing-0.1.0.jar
```

## Schedule

GitHub Actions runs hourly at :00 (UTC).

## Target Date

If no target date is provided, the application resolves the target date based on the current hour in `Asia/Seoul`.

Manual recovery runs can specify a date:

```bash
java -jar build/libs/economic-briefing-0.1.0.jar --target-date=2026-07-16
```

In GitHub Actions, use `workflow_dispatch` and set `target_date` to `YYYY-MM-DD`.

## Duplicate Runs

The workflow uses GitHub Actions `concurrency` with `cancel-in-progress: true`.

This prevents overlapping scheduled/manual executions for the same workflow group.

## Secrets

The workflow reads external service settings only from GitHub Actions secrets:

- `OPENAI_API_KEY`
- `NOTION_API_KEY`
- `NOTION_DATABASE_ID`

Secrets must not be printed in logs.

## Failure Checks

GitHub Actions marks the run failed when:

- Gradle build or test fails
- the briefing command returns a non-zero exit code

Check the failed workflow run logs in GitHub Actions.

## Manual Dry Run

Manual GitHub Actions runs default to `dry_run=true`. Scheduled runs use `dry_run=false`.

Use manual dry runs for environment checks before enabling real publication.

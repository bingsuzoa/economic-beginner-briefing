# Repository guide

This repository runs the Thoth daily economic-flow service.

Before changing collection, prompts, retrieval, database schema, scheduling, or deployment, read:

1. `docs/ECONOMIC_FLOW_OPERATIONS.md` — operational source of truth
2. `docs/ECONOMIC_FLOW_DAILY_BRIEFING_FINAL_DESIGN_V1.md` — architecture and rationale
3. `docs/ECONOMIC_FLOW_LLM_PROMPT_DESIGN_V1.md` — LLM contracts

Do not restore the removed article-by-article analyzer, economic graph, verifier-LLM chain, fixed flow count, metadata fields such as `statementKind`/`role`, or past generated prose as evidence. Diagnose the first failing stage from `daily_briefings.usage_json` and `trace_json`, then replay a fixed date.

Run `./gradlew clean test`, `frontend/npm run build`, and `node scripts/check-mock-data.mjs` before merge.

A push to `main` deploys DEV only. Production is a separate elevated Windows deployment; see the operations guide.


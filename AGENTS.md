# Repository instructions

Before changing collection, LLM prompts, retrieval, database schema, scheduling, or deployment, read `docs/ECONOMIC_FLOW_OPERATIONS.md`. It is the operational source of truth. The detailed rationale is in `docs/ECONOMIC_FLOW_DAILY_BRIEFING_FINAL_DESIGN_V1.md` and the prompt contracts are in `docs/ECONOMIC_FLOW_LLM_PROMPT_DESIGN_V1.md`.

Do not reintroduce the deleted article-by-article analyzer, economic graph, verifier-LLM chain, fixed flow count, `statementKind`/`role` metadata, or past generated prose as retrieval evidence. Diagnose the failing stage with `daily_briefings.usage_json` and `trace_json`, replay a fixed date, and change only the owning stage. Keep the daily cost guard and source-span validation intact.

`main` push deploys DEV only. Production deployment is a separate elevated Windows operation documented in the operations guide; never claim production was deployed from the DEV workflow.

import { describe, it, expect, beforeEach } from "vitest";
import { DbPipelineLock } from "../../src/pipeline/DbPipelineLock.js";
import { MockPipelineRunRepository } from "../../src/db/mock/MockPipelineRunRepository.js";
import { PIPELINE_LOCK_MAX_AGE_MS } from "../../src/config/constants.js";

describe("DbPipelineLock", () => {
  let runRepo: MockPipelineRunRepository;
  let lock: DbPipelineLock;

  beforeEach(() => {
    runRepo = new MockPipelineRunRepository();
    lock = new DbPipelineLock(runRepo);
  });

  it("should acquire lock when no pipeline is running", async () => {
    const acquired = await lock.acquire("run-1");
    expect(acquired).toBe(true);
    expect(await lock.isLocked()).toBe(true);
  });

  it("should reject second acquire when pipeline is running", async () => {
    await lock.acquire("run-1");
    const acquired = await lock.acquire("run-2");
    expect(acquired).toBe(false);
  });

  it("should allow acquire when previous run expired", async () => {
    await lock.acquire("run-1");
    // Manually set started_at to past
    const run = runRepo.runs.get("run-1")!;
    run.startedAt = new Date(Date.now() - PIPELINE_LOCK_MAX_AGE_MS - 1000);
    runRepo.runs.set("run-1", run);

    const acquired = await lock.acquire("run-2");
    expect(acquired).toBe(true);
    // Old run should be FAILED
    const oldRun = await runRepo.findById("run-1");
    expect(oldRun?.status).toBe("FAILED");
  });

  it("should return current run id", async () => {
    expect(await lock.getCurrentRunId()).toBeNull();
    await lock.acquire("run-1");
    expect(await lock.getCurrentRunId()).toBe("run-1");
  });

  it("should release lock", async () => {
    await lock.acquire("run-1");
    await lock.release("run-1");
    const run = await runRepo.findById("run-1");
    expect(run?.status).toBe("FAILED");
    expect(await lock.isLocked()).toBe(false);
  });

  it("should not be locked after release", async () => {
    await lock.acquire("run-1");
    await lock.release("run-1");
    const acquired = await lock.acquire("run-2");
    expect(acquired).toBe(true);
  });
});

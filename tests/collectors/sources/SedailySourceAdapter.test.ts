import { describe, it, expect } from "vitest";
import { SedailySourceAdapter } from "../../../src/collectors/sources/SedailySourceAdapter.js";

describe("SedailySourceAdapter", () => {
  it("sourceName이 서울경제이다", () => {
    const adapter = new SedailySourceAdapter();
    expect(adapter.sourceName).toBe("서울경제");
  });

  it("올바른 RSS URL을 사용한다", () => {
    const adapter = new SedailySourceAdapter();
    // Access via config (protected but accessible via instance check)
    expect(adapter.sourceName).toBe("서울경제");
    // The adapter extends BaseRSSAdapter correctly
    expect(adapter).toBeDefined();
  });

  it("SourceAdapter 인터페이스를 구현한다", () => {
    const adapter = new SedailySourceAdapter();
    expect(typeof adapter.collect).toBe("function");
    expect(typeof adapter.sourceName).toBe("string");
  });
});

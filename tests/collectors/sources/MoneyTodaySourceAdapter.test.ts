import { describe, it, expect } from "vitest";
import { MoneyTodaySourceAdapter } from "../../../src/collectors/sources/MoneyTodaySourceAdapter.js";

describe("MoneyTodaySourceAdapter", () => {
  it("sourceName이 머니투데이이다", () => {
    const adapter = new MoneyTodaySourceAdapter();
    expect(adapter.sourceName).toBe("머니투데이");
  });

  it("SourceAdapter 인터페이스를 구현한다", () => {
    const adapter = new MoneyTodaySourceAdapter();
    expect(typeof adapter.collect).toBe("function");
    expect(typeof adapter.sourceName).toBe("string");
  });
});

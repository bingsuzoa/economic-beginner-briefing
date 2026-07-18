import { describe, it, expect } from "vitest";
import { NewsisSourceAdapter } from "../../../src/collectors/sources/NewsisSourceAdapter.js";

describe("NewsisSourceAdapter", () => {
  it("sourceName이 뉴시스이다", () => {
    const adapter = new NewsisSourceAdapter();
    expect(adapter.sourceName).toBe("뉴시스");
  });

  it("SourceAdapter 인터페이스를 구현한다", () => {
    const adapter = new NewsisSourceAdapter();
    expect(typeof adapter.collect).toBe("function");
    expect(typeof adapter.sourceName).toBe("string");
  });
});

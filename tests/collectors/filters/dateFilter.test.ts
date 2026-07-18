import { describe, it, expect } from "vitest";
import { filterByDate } from "../../../src/collectors/filters/dateFilter.js";
import type { Article } from "../../../src/domain/article.js";

function makeArticle(publishedAt: string, overrides?: Partial<Article>): Article {
  return {
    id: "test-id-" + Math.random().toString(36).substring(7),
    title: "테스트 기사",
    summary: "테스트 요약",
    sourceName: "테스트 출처",
    sourceType: "news_media",
    publishedAt,
    collectedAt: "2026-07-17T06:00:00+09:00",
    url: "https://example.com/article/" + Math.random(),
    categories: ["other"],
    language: "ko",
    ...overrides,
  };
}

describe("filterByDate", () => {
  const startTime = "2026-07-16T00:00:00+09:00";
  const endTime = "2026-07-16T23:59:59+09:00";

  it("전날 기사를 포함한다", () => {
    const articles = [
      makeArticle("2026-07-16T10:30:00+09:00"),
    ];

    const result = filterByDate(articles, startTime, endTime);

    expect(result.accepted).toHaveLength(1);
    expect(result.rejected).toHaveLength(0);
  });

  it("오늘 기사를 제외한다", () => {
    const articles = [
      makeArticle("2026-07-17T08:00:00+09:00"),
    ];

    const result = filterByDate(articles, startTime, endTime);

    expect(result.accepted).toHaveLength(0);
    expect(result.rejected).toHaveLength(1);
  });

  it("전전날 기사를 제외한다", () => {
    const articles = [
      makeArticle("2026-07-15T15:00:00+09:00"),
    ];

    const result = filterByDate(articles, startTime, endTime);

    expect(result.accepted).toHaveLength(0);
    expect(result.rejected).toHaveLength(1);
  });

  it("전날 00:00:00 경계를 포함한다", () => {
    const articles = [
      makeArticle("2026-07-16T00:00:00+09:00"),
    ];

    const result = filterByDate(articles, startTime, endTime);

    expect(result.accepted).toHaveLength(1);
  });

  it("전날 23:59:59 경계를 포함한다", () => {
    const articles = [
      makeArticle("2026-07-16T23:59:59+09:00"),
    ];

    const result = filterByDate(articles, startTime, endTime);

    expect(result.accepted).toHaveLength(1);
  });

  it("오늘 00:00:00 경계를 제외한다", () => {
    const articles = [
      makeArticle("2026-07-17T00:00:00+09:00"),
    ];

    const result = filterByDate(articles, startTime, endTime);

    expect(result.accepted).toHaveLength(0);
    expect(result.rejected).toHaveLength(1);
  });

  it("UTC와 KST 날짜 차이를 정확히 처리한다", () => {
    // UTC 2026-07-15T16:00:00Z = KST 2026-07-16T01:00:00+09:00 → 전날에 포함
    const articles = [
      makeArticle("2026-07-15T16:00:00Z"),
    ];

    const result = filterByDate(articles, startTime, endTime);

    expect(result.accepted).toHaveLength(1);
  });

  it("UTC 기준 전날이지만 KST 기준 전전날인 기사를 제외한다", () => {
    // UTC 2026-07-15T14:00:00Z = KST 2026-07-15T23:00:00+09:00 → 전전날
    const articles = [
      makeArticle("2026-07-15T14:00:00Z"),
    ];

    const result = filterByDate(articles, startTime, endTime);

    expect(result.accepted).toHaveLength(0);
  });

  it("발행일이 파싱 불가능한 기사를 제외한다", () => {
    const articles = [
      makeArticle("invalid-date"),
    ];

    const result = filterByDate(articles, startTime, endTime);

    expect(result.accepted).toHaveLength(0);
    expect(result.rejected).toHaveLength(1);
  });

  it("여러 기사를 정확히 분류한다", () => {
    const articles = [
      makeArticle("2026-07-16T10:00:00+09:00"), // 포함
      makeArticle("2026-07-17T10:00:00+09:00"), // 제외 (오늘)
      makeArticle("2026-07-15T10:00:00+09:00"), // 제외 (전전날)
      makeArticle("2026-07-16T23:59:59+09:00"), // 포함
    ];

    const result = filterByDate(articles, startTime, endTime);

    expect(result.accepted).toHaveLength(2);
    expect(result.rejected).toHaveLength(2);
  });

  it("월말 경계를 정확히 처리한다", () => {
    const monthEndStart = "2026-07-31T00:00:00+09:00";
    const monthEndEnd = "2026-07-31T23:59:59+09:00";

    const articles = [
      makeArticle("2026-07-31T12:00:00+09:00"),
      makeArticle("2026-08-01T00:00:00+09:00"),
    ];

    const result = filterByDate(articles, monthEndStart, monthEndEnd);

    expect(result.accepted).toHaveLength(1);
    expect(result.rejected).toHaveLength(1);
  });
});

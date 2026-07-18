import { describe, it, expect } from "vitest";
import { validateQuality } from "../../../src/collectors/filters/qualityValidator.js";
import type { Article } from "../../../src/domain/article.js";

function makeArticle(overrides: Partial<Article> = {}): Article {
  return {
    id: "test-id",
    title: "정상적인 기사 제목입니다",
    summary: "정상적인 요약 내용",
    sourceName: "테스트 출처",
    sourceType: "news_media",
    publishedAt: "2026-07-16T10:00:00+09:00",
    collectedAt: "2026-07-17T06:00:00+09:00",
    url: "https://example.com/article/1",
    categories: ["other"],
    language: "ko",
    ...overrides,
  };
}

describe("validateQuality", () => {
  it("정상 기사를 통과시킨다", () => {
    const articles = [makeArticle()];

    const result = validateQuality(articles);

    expect(result.valid).toHaveLength(1);
    expect(result.invalid).toHaveLength(0);
  });

  it("제목이 없는 기사를 제외한다", () => {
    const articles = [makeArticle({ title: "" })];

    const result = validateQuality(articles);

    expect(result.valid).toHaveLength(0);
    expect(result.invalid).toHaveLength(1);
  });

  it("제목이 5자 미만인 기사를 제외한다", () => {
    const articles = [makeArticle({ title: "짧은" })];

    const result = validateQuality(articles);

    expect(result.valid).toHaveLength(0);
    expect(result.invalid).toHaveLength(1);
  });

  it("URL이 없는 기사를 제외한다", () => {
    const articles = [makeArticle({ url: "" })];

    const result = validateQuality(articles);

    expect(result.valid).toHaveLength(0);
    expect(result.invalid).toHaveLength(1);
  });

  it("잘못된 URL 형식의 기사를 제외한다", () => {
    const articles = [makeArticle({ url: "not-a-url" })];

    const result = validateQuality(articles);

    expect(result.valid).toHaveLength(0);
    expect(result.invalid).toHaveLength(1);
  });

  it("발행일이 없는 기사를 제외한다", () => {
    const articles = [makeArticle({ publishedAt: "" })];

    const result = validateQuality(articles);

    expect(result.valid).toHaveLength(0);
    expect(result.invalid).toHaveLength(1);
  });

  it("발행일이 파싱 불가능한 기사를 제외한다", () => {
    const articles = [makeArticle({ publishedAt: "invalid-date" })];

    const result = validateQuality(articles);

    expect(result.valid).toHaveLength(0);
    expect(result.invalid).toHaveLength(1);
  });

  it("요약과 내용 모두 없어도 제목이 있으면 통과시킨다", () => {
    const articles = [makeArticle({ summary: "", content: undefined })];

    const result = validateQuality(articles);

    expect(result.valid).toHaveLength(1);
    expect(result.invalid).toHaveLength(0);
  });

  it("요약은 없지만 내용이 있으면 통과시킨다", () => {
    const articles = [makeArticle({ summary: "", content: "본문 내용" })];

    const result = validateQuality(articles);

    expect(result.valid).toHaveLength(1);
  });

  it("[AD] 태그가 있는 기사를 제외한다", () => {
    const articles = [makeArticle({ title: "[AD] 특가 상품 안내" })];

    const result = validateQuality(articles);

    expect(result.valid).toHaveLength(0);
    expect(result.invalid).toHaveLength(1);
  });

  it("[광고] 태그가 있는 기사를 제외한다", () => {
    const articles = [makeArticle({ title: "[광고] 신규 대출 상품 안내" })];

    const result = validateQuality(articles);

    expect(result.valid).toHaveLength(0);
    expect(result.invalid).toHaveLength(1);
  });

  it("여러 기사를 정확히 분류한다", () => {
    const articles = [
      makeArticle({ title: "정상적인 경제 뉴스 기사" }),
      makeArticle({ title: "" }),
      makeArticle({ title: "[AD] 광고 기사입니다" }),
      makeArticle({ title: "또 다른 정상 기사 제목" }),
    ];

    const result = validateQuality(articles);

    expect(result.valid).toHaveLength(2);
    expect(result.invalid).toHaveLength(2);
  });
});

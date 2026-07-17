import { describe, it, expect } from "vitest";
import { removeDuplicates, normalizeUrl } from "../../../src/collectors/filters/duplicateRemover.js";
import type { Article } from "../../../src/domain/article.js";

function makeArticle(overrides: Partial<Article> = {}): Article {
  return {
    id: "default-id",
    title: "기본 제목",
    summary: "기본 요약",
    sourceName: "기본 출처",
    sourceType: "news_media",
    publishedAt: "2026-07-16T10:00:00+09:00",
    collectedAt: "2026-07-17T06:00:00+09:00",
    url: "https://example.com/article/1",
    categories: ["other"],
    language: "ko",
    ...overrides,
  };
}

describe("normalizeUrl", () => {
  it("UTM 파라미터를 제거한다", () => {
    const url = "https://example.com/article?utm_source=rss&utm_medium=feed&id=123";
    const normalized = normalizeUrl(url);

    expect(normalized).toContain("id=123");
    expect(normalized).not.toContain("utm_source");
    expect(normalized).not.toContain("utm_medium");
  });

  it("모바일 URL을 PC URL로 통일한다", () => {
    const url = "https://m.example.com/article/1";
    const normalized = normalizeUrl(url);

    expect(normalized).toContain("www.example.com");
    expect(normalized).not.toContain("m.example.com");
  });

  it("해시를 제거한다", () => {
    const url = "https://example.com/article/1#section";
    const normalized = normalizeUrl(url);

    expect(normalized).not.toContain("#section");
  });

  it("잘못된 URL은 원본을 반환한다", () => {
    const url = "not-a-valid-url";
    const normalized = normalizeUrl(url);

    expect(normalized).toBe(url);
  });
});

describe("removeDuplicates", () => {
  it("동일 URL 기사를 중복으로 처리한다", () => {
    const articles = [
      makeArticle({ id: "id-1", url: "https://example.com/article/1" }),
      makeArticle({ id: "id-2", url: "https://example.com/article/1" }),
    ];

    const result = removeDuplicates(articles);

    expect(result.unique).toHaveLength(1);
    expect(result.duplicates).toHaveLength(1);
  });

  it("동일 ID 기사를 중복으로 처리한다", () => {
    const articles = [
      makeArticle({ id: "same-id", url: "https://example.com/1" }),
      makeArticle({ id: "same-id", url: "https://example.com/2" }),
    ];

    const result = removeDuplicates(articles);

    expect(result.unique).toHaveLength(1);
    expect(result.duplicates).toHaveLength(1);
  });

  it("추적 파라미터만 다른 URL을 중복으로 판단한다", () => {
    const articles = [
      makeArticle({ id: "id-1", url: "https://example.com/article/1" }),
      makeArticle({ id: "id-2", url: "https://example.com/article/1?utm_source=rss" }),
    ];

    const result = removeDuplicates(articles);

    expect(result.unique).toHaveLength(1);
    expect(result.duplicates).toHaveLength(1);
  });

  it("같은 출처에서 유사 제목 기사를 중복으로 처리한다", () => {
    const articles = [
      makeArticle({
        id: "id-1",
        url: "https://example.com/1",
        title: "한국은행 기준금리 인하 결정",
        sourceName: "연합뉴스",
      }),
      makeArticle({
        id: "id-2",
        url: "https://example.com/2",
        title: "[속보] 한국은행 기준금리 인하 결정",
        sourceName: "연합뉴스",
      }),
    ];

    const result = removeDuplicates(articles);

    expect(result.unique).toHaveLength(1);
    expect(result.duplicates).toHaveLength(1);
  });

  it("다른 출처의 동일 사건 보도는 유지한다", () => {
    const articles = [
      makeArticle({
        id: "id-1",
        url: "https://yna.co.kr/1",
        title: "한국은행 기준금리 인하 결정",
        sourceName: "연합뉴스",
      }),
      makeArticle({
        id: "id-2",
        url: "https://hankyung.com/1",
        title: "한국은행 기준금리 인하 결정",
        sourceName: "한국경제",
      }),
    ];

    const result = removeDuplicates(articles);

    expect(result.unique).toHaveLength(2);
    expect(result.duplicates).toHaveLength(0);
  });

  it("서로 다른 기사를 모두 유지한다", () => {
    const articles = [
      makeArticle({ id: "id-1", url: "https://example.com/1", title: "기준금리 인하" }),
      makeArticle({ id: "id-2", url: "https://example.com/2", title: "아파트 가격 상승" }),
      makeArticle({ id: "id-3", url: "https://example.com/3", title: "청약 제도 변경" }),
    ];

    const result = removeDuplicates(articles);

    expect(result.unique).toHaveLength(3);
    expect(result.duplicates).toHaveLength(0);
  });
});

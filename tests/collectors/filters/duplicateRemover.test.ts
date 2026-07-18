import { describe, it, expect } from "vitest";
import { removeDuplicates, normalizeUrl, calculateSimilarity } from "../../../src/collectors/filters/duplicateRemover.js";
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

  it("다른 출처의 동일 사건 보도는 대표 기사 하나만 유지한다", () => {
    const articles = [
      makeArticle({
        id: "id-1",
        url: "https://yna.co.kr/1",
        title: "한국은행 기준금리 인하 결정",
        sourceName: "연합뉴스",
        summary: "짧은 요약",
      }),
      makeArticle({
        id: "id-2",
        url: "https://hankyung.com/1",
        title: "한국은행 기준금리 인하 결정",
        sourceName: "한국경제",
        summary: "한국은행이 기준금리를 0.25%p 인하하여 3.25%로 결정했습니다. 이는 경기 둔화에 대응하기 위한 조치입니다.",
      }),
    ];

    const result = removeDuplicates(articles);

    expect(result.unique).toHaveLength(1);
    expect(result.duplicates).toHaveLength(1);
    // 더 긴 요약을 가진 한국경제 기사가 대표로 선정
    expect(result.unique[0].sourceName).toBe("한국경제");
    expect(result.eventGroups).toHaveLength(1);
    expect(result.eventGroups[0].sourceNames).toContain("연합뉴스");
    expect(result.eventGroups[0].sourceNames).toContain("한국경제");
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

  it("eventGroups에 모든 고유 기사의 그룹 정보가 포함된다", () => {
    const articles = [
      makeArticle({ id: "id-1", url: "https://example.com/1", title: "기준금리 인하", sourceName: "A" }),
      makeArticle({ id: "id-2", url: "https://example.com/2", title: "아파트 가격 상승", sourceName: "B" }),
    ];

    const result = removeDuplicates(articles);

    expect(result.eventGroups).toHaveLength(2);
    expect(result.eventGroups[0].relatedArticles).toHaveLength(0);
    expect(result.eventGroups[1].relatedArticles).toHaveLength(0);
  });

  it("크로스소스 대표 기사 선정이 배열 순서에 의존하지 않는다", () => {
    // 짧은 요약의 기사를 먼저, 긴 요약의 기사를 나중에 배치
    const articles = [
      makeArticle({
        id: "short",
        url: "https://a.com/1",
        title: "한국은행 기준금리 인하 결정",
        sourceName: "출처A",
        summary: "짧음",
      }),
      makeArticle({
        id: "long",
        url: "https://b.com/1",
        title: "한국은행 기준금리 인하 결정",
        sourceName: "출처B",
        summary: "한국은행 금융통화위원회는 기준금리를 3.50%에서 3.25%로 0.25%p 인하하기로 결정했습니다.",
      }),
    ];

    const result = removeDuplicates(articles);

    // 더 긴 요약을 가진 기사가 배열 순서와 무관하게 대표로 선정되어야 함
    expect(result.unique).toHaveLength(1);
    expect(result.unique[0].id).toBe("long");
  });

  it("3개 이상 출처의 동일 사건을 하나로 그룹화한다", () => {
    const articles = [
      makeArticle({ id: "a", url: "https://a.com/1", title: "기준금리 인하 결정 발표", sourceName: "A" }),
      makeArticle({ id: "b", url: "https://b.com/1", title: "기준금리 인하 결정 발표", sourceName: "B" }),
      makeArticle({ id: "c", url: "https://c.com/1", title: "기준금리 인하 결정 발표", sourceName: "C" }),
    ];

    const result = removeDuplicates(articles);

    expect(result.unique).toHaveLength(1);
    expect(result.duplicates).toHaveLength(2);
    expect(result.eventGroups).toHaveLength(1);
    expect(result.eventGroups[0].sourceNames).toHaveLength(3);
  });
});

describe("calculateSimilarity", () => {
  it("동일 문자열이면 1을 반환한다", () => {
    expect(calculateSimilarity("한국은행", "한국은행")).toBe(1);
  });

  it("빈 문자열이면 0을 반환한다", () => {
    expect(calculateSimilarity("", "한국은행")).toBe(0);
    expect(calculateSimilarity("한국은행", "")).toBe(0);
  });

  it("유사한 제목은 높은 유사도를 반환한다", () => {
    const similarity = calculateSimilarity(
      "한국은행기준금리인하결정",
      "한국은행기준금리인하결정발표",
    );
    expect(similarity).toBeGreaterThan(0.8);
  });

  it("전혀 다른 제목은 낮은 유사도를 반환한다", () => {
    const similarity = calculateSimilarity(
      "기준금리인하결정",
      "아파트가격상승전망",
    );
    expect(similarity).toBeLessThan(0.5);
  });
});

import { describe, it, expect } from "vitest";
import { scoreRelevance } from "../../../src/collectors/filters/relevanceScorer.js";
import type { Article } from "../../../src/domain/article.js";

function makeArticle(overrides: Partial<Article> = {}): Article {
  return {
    id: `test-${Math.random().toString(36).substring(7)}`,
    title: "기본 제목입니다",
    summary: "기본 요약입니다",
    sourceName: "테스트 출처",
    sourceType: "news_media",
    publishedAt: "2026-07-16T10:00:00+09:00",
    collectedAt: "2026-07-17T06:00:00+09:00",
    url: `https://example.com/article/${Math.random()}`,
    categories: ["other"],
    language: "ko",
    ...overrides,
  };
}

describe("scoreRelevance", () => {
  it("기준금리 변경 기사를 5점으로 평가한다", () => {
    const articles = [
      makeArticle({ title: "한국은행 기준금리 인하 결정", categories: ["interest_rate"] }),
    ];

    const result = scoreRelevance(articles);

    expect(result.scores[0].score).toBe(5);
    expect(result.scores[0].matchedKeywords).toContain("기준금리 인하");
  });

  it("대출 규제 변경 기사를 5점으로 평가한다", () => {
    const articles = [
      makeArticle({ title: "DSR 완화 방안 발표", summary: "대출 규제 완화", categories: ["loan"] }),
    ];

    const result = scoreRelevance(articles);

    expect(result.scores[0].score).toBe(5);
  });

  it("아파트 가격 동향 기사를 4점으로 평가한다", () => {
    const articles = [
      makeArticle({ title: "서울 아파트 가격 3주 연속 상승", categories: ["housing"] }),
    ];

    const result = scoreRelevance(articles);

    expect(result.scores[0].score).toBe(4);
  });

  it("물가 기사를 3점으로 평가한다", () => {
    const articles = [
      makeArticle({ title: "소비자물가 4개월 연속 상승", categories: ["cost_of_living"] }),
    ];

    const result = scoreRelevance(articles);

    expect(result.scores[0].score).toBe(3);
  });

  it("증시 기사를 3점으로 평가한다", () => {
    const articles = [
      makeArticle({ title: "코스피 장 마감 동향", categories: ["investment"] }),
    ];

    const result = scoreRelevance(articles);

    expect(result.scores[0].score).toBe(3);
  });

  it("경제 카테고리가 있지만 키워드 매칭이 안 되면 기본 2점을 부여한다", () => {
    const articles = [
      makeArticle({ title: "경제 관련 일반 기사", categories: ["housing"] }),
    ];

    const result = scoreRelevance(articles);

    expect(result.scores[0].score).toBe(2);
  });

  it("카테고리가 other만 있고 키워드 매칭이 없으면 0점이다", () => {
    const articles = [
      makeArticle({ title: "일반 뉴스 기사입니다", categories: ["other"] }),
    ];

    const result = scoreRelevance(articles);

    expect(result.scores[0].score).toBe(0);
  });

  it("minScore로 저관련성 기사를 필터링한다", () => {
    const articles = [
      makeArticle({ id: "high", title: "기준금리 인하 결정", categories: ["interest_rate"] }),
      makeArticle({ id: "low", title: "일반 뉴스", categories: ["other"] }),
    ];

    const result = scoreRelevance(articles, 3);

    expect(result.filtered).toHaveLength(1);
    expect(result.filtered[0].id).toBe("high");
    expect(result.excluded).toHaveLength(1);
    expect(result.excluded[0].id).toBe("low");
  });

  it("minScore 0이면 모든 기사를 포함한다", () => {
    const articles = [
      makeArticle({ title: "기준금리 인하" }),
      makeArticle({ title: "일반 뉴스" }),
    ];

    const result = scoreRelevance(articles, 0);

    expect(result.filtered).toHaveLength(2);
    expect(result.excluded).toHaveLength(0);
  });

  it("복수 키워드가 매칭되면 가장 높은 점수를 사용한다", () => {
    const articles = [
      makeArticle({
        title: "기준금리 인하로 코스피 상승",
        summary: "물가 상승 우려 속 기준금리 인하",
        categories: ["interest_rate", "investment"],
      }),
    ];

    const result = scoreRelevance(articles);

    expect(result.scores[0].score).toBe(5); // 기준금리 인하 = 5점
    expect(result.scores[0].matchedKeywords.length).toBeGreaterThan(1);
  });

  it("빈 배열을 처리한다", () => {
    const result = scoreRelevance([]);

    expect(result.scores).toHaveLength(0);
    expect(result.filtered).toHaveLength(0);
    expect(result.excluded).toHaveLength(0);
  });

  it("재테크 키워드를 3점으로 평가한다", () => {
    const articles = [
      makeArticle({ title: "자산배분 전략 안내", summary: "재테크 초보자 가이드", categories: ["investment"] }),
    ];

    const result = scoreRelevance(articles);

    expect(result.scores[0]!.score).toBe(3);
  });

  it("IRP 키워드를 4점으로 평가한다", () => {
    const articles = [
      makeArticle({ title: "IRP 가입자 혜택 확대", categories: ["pension"] }),
    ];

    const result = scoreRelevance(articles);

    expect(result.scores[0]!.score).toBe(4);
  });

  it("통신비 키워드를 3점으로 평가한다", () => {
    const articles = [
      makeArticle({ title: "통신비 인상 결정", categories: ["cost_of_living"] }),
    ];

    const result = scoreRelevance(articles);

    expect(result.scores[0]!.score).toBe(3);
  });

  it("스트레스DSR 키워드를 5점으로 평가한다", () => {
    const articles = [
      makeArticle({ title: "스트레스DSR 2단계 시행", categories: ["loan"] }),
    ];

    const result = scoreRelevance(articles);

    expect(result.scores[0]!.score).toBe(5);
  });
});

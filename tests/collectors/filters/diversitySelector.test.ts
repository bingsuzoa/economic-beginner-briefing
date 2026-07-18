import { describe, it, expect } from "vitest";
import { selectWithDiversity } from "../../../src/collectors/filters/diversitySelector.js";
import type { RelevanceScore } from "../../../src/collectors/filters/relevanceScorer.js";
import type { Article } from "../../../src/domain/article.js";

function makeArticle(overrides: Partial<Article> = {}): Article {
  const id = overrides.id ?? `test-${Math.random().toString(36).substring(7)}`;
  return {
    id,
    title: "기본 제목입니다",
    summary: "기본 요약입니다",
    sourceName: "테스트 출처",
    sourceType: "news_media",
    publishedAt: "2026-07-16T10:00:00+09:00",
    collectedAt: "2026-07-17T06:00:00+09:00",
    url: `https://example.com/article/${id}`,
    categories: ["other"],
    language: "ko",
    ...overrides,
  };
}

function makeScore(articleId: string, score: number): RelevanceScore {
  return { articleId, score, matchedKeywords: [] };
}

describe("selectWithDiversity", () => {
  it("관련성이 높은 기사를 우선 선택한다", () => {
    const articles = [
      makeArticle({ id: "low", sourceName: "A", categories: ["housing"] }),
      makeArticle({ id: "high", sourceName: "B", categories: ["loan"] }),
    ];
    const scores = [
      makeScore("low", 2),
      makeScore("high", 5),
    ];

    const result = selectWithDiversity(articles, scores);

    expect(result.selected[0].id).toBe("high");
  });

  it("동일 출처 기사가 maxArticlesPerSource를 초과하면 제외한다", () => {
    const articles = Array.from({ length: 7 }, (_, i) =>
      makeArticle({
        id: `art-${i}`,
        sourceName: "연합뉴스",
        categories: [`interest_rate`],
      }),
    );
    const scores = articles.map((a) => makeScore(a.id, 4));

    const result = selectWithDiversity(articles, scores, {
      maxArticlesPerSource: 3,
    });

    expect(result.selected).toHaveLength(3);
    expect(result.stats.excludedBySourceLimit).toBe(4);
  });

  it("동일 카테고리 기사가 maxArticlesPerCategory를 초과하면 제외한다", () => {
    const articles = Array.from({ length: 6 }, (_, i) =>
      makeArticle({
        id: `art-${i}`,
        sourceName: `출처${i}`,
        categories: ["housing"],
      }),
    );
    const scores = articles.map((a) => makeScore(a.id, 4));

    const result = selectWithDiversity(articles, scores, {
      maxArticlesPerCategory: 2,
      maxArticlesPerSource: 10,
    });

    expect(result.selected).toHaveLength(2);
    expect(result.stats.excludedByCategoryLimit).toBe(4);
  });

  it("관련성이 minRelevanceScore 미만인 기사를 제외한다", () => {
    const articles = [
      makeArticle({ id: "relevant", categories: ["loan"] }),
      makeArticle({ id: "irrelevant", categories: ["other"] }),
    ];
    const scores = [
      makeScore("relevant", 4),
      makeScore("irrelevant", 1),
    ];

    const result = selectWithDiversity(articles, scores, {
      minRelevanceScore: 2,
    });

    expect(result.selected).toHaveLength(1);
    expect(result.selected[0].id).toBe("relevant");
    expect(result.stats.excludedByRelevance).toBe(1);
  });

  it("maxTotal을 초과하면 추가 기사를 제외한다", () => {
    const articles = Array.from({ length: 5 }, (_, i) =>
      makeArticle({
        id: `art-${i}`,
        sourceName: `출처${i}`,
        categories: ["housing"],
      }),
    );
    const scores = articles.map((a) => makeScore(a.id, 4));

    const result = selectWithDiversity(articles, scores, {
      maxTotal: 3,
      maxArticlesPerCategory: 10,
    });

    expect(result.selected).toHaveLength(3);
  });

  it("다양한 출처에서 골고루 선택한다", () => {
    const articles = [
      makeArticle({ id: "yna-1", sourceName: "연합뉴스", categories: ["housing"] }),
      makeArticle({ id: "yna-2", sourceName: "연합뉴스", categories: ["loan"] }),
      makeArticle({ id: "hk-1", sourceName: "한국경제", categories: ["housing"] }),
      makeArticle({ id: "mk-1", sourceName: "매일경제", categories: ["tax"] }),
    ];
    const scores = articles.map((a) => makeScore(a.id, 4));

    const result = selectWithDiversity(articles, scores, {
      maxArticlesPerSource: 2,
    });

    expect(result.selected).toHaveLength(4);
    expect(result.stats.bySource["연합뉴스"]).toBe(2);
    expect(result.stats.bySource["한국경제"]).toBe(1);
    expect(result.stats.bySource["매일경제"]).toBe(1);
  });

  it("저품질 기사를 강제로 선택하지 않는다", () => {
    const articles = [
      makeArticle({ id: "good", sourceName: "A", categories: ["housing"] }),
      makeArticle({ id: "bad", sourceName: "B", categories: ["other"] }),
    ];
    const scores = [
      makeScore("good", 4),
      makeScore("bad", 0),
    ];

    const result = selectWithDiversity(articles, scores, {
      minRelevanceScore: 2,
    });

    expect(result.selected).toHaveLength(1);
    expect(result.selected[0].id).toBe("good");
  });

  it("빈 배열을 처리한다", () => {
    const result = selectWithDiversity([], []);

    expect(result.selected).toHaveLength(0);
    expect(result.excluded).toHaveLength(0);
  });

  it("동점일 때 더 최신 기사를 우선한다", () => {
    const articles = [
      makeArticle({
        id: "old",
        sourceName: "A",
        publishedAt: "2026-07-16T08:00:00+09:00",
        categories: ["housing"],
      }),
      makeArticle({
        id: "new",
        sourceName: "B",
        publishedAt: "2026-07-16T14:00:00+09:00",
        categories: ["housing"],
      }),
    ];
    const scores = [
      makeScore("old", 4),
      makeScore("new", 4),
    ];

    const result = selectWithDiversity(articles, scores);

    expect(result.selected[0].id).toBe("new");
  });

  it("softMax: 높은 점수의 기사는 카테고리 제한을 1개 초과할 수 있다", () => {
    // Create articles that fill the category limit
    const articles = [
      makeArticle({ id: "high-1", sourceName: "A", categories: ["housing"] }),
      makeArticle({ id: "high-2", sourceName: "B", categories: ["housing"] }),
      // This article has score 5 and should be allowed to exceed the limit by 1
      makeArticle({ id: "critical", sourceName: "C", categories: ["housing"] }),
      // This article has score 4 and should be excluded by the limit
      makeArticle({ id: "normal", sourceName: "D", categories: ["housing"] }),
    ];
    const scores = [
      makeScore("high-1", 5),
      makeScore("high-2", 5),
      makeScore("critical", 5),  // Should pass via soft max override
      makeScore("normal", 4),     // Should be excluded
    ];

    const result = selectWithDiversity(articles, scores, {
      maxArticlesPerCategory: 2,
      maxArticlesPerSource: 10,
      minRelevanceScore: 3,
      softMaxOverrideScore: 5,
    });

    expect(result.selected).toHaveLength(3);
    expect(result.selected.map((a) => a.id)).toContain("critical");
    expect(result.excluded.map((a) => a.id)).toContain("normal");
    expect(result.stats.excludedByCategoryLimit).toBe(1);
  });

  it("softMax: 일반 점수 기사는 카테고리 제한을 초과할 수 없다", () => {
    const articles = [
      makeArticle({ id: "a1", sourceName: "A", categories: ["loan"] }),
      makeArticle({ id: "a2", sourceName: "B", categories: ["loan"] }),
      makeArticle({ id: "a3", sourceName: "C", categories: ["loan"] }),
    ];
    const scores = [
      makeScore("a1", 4),
      makeScore("a2", 4),
      makeScore("a3", 4),
    ];

    const result = selectWithDiversity(articles, scores, {
      maxArticlesPerCategory: 2,
      maxArticlesPerSource: 10,
      minRelevanceScore: 3,
      softMaxOverrideScore: 5,
    });

    expect(result.selected).toHaveLength(2);
    expect(result.stats.excludedByCategoryLimit).toBe(1);
  });
});

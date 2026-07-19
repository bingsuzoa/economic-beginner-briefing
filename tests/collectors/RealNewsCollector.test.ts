import { describe, it, expect, vi } from "vitest";
import { RealNewsCollector, createDefaultAdapters } from "../../src/collectors/RealNewsCollector.js";
import type { SourceAdapter, SourceCollectionResult } from "../../src/collectors/sources/SourceAdapter.js";
import type { Article } from "../../src/domain/article.js";
import { AppError } from "../../src/errors/AppError.js";
import { ErrorCodes } from "../../src/errors/errorCodes.js";

function makeArticle(overrides: Partial<Article> = {}): Article {
  const id = Math.random().toString(36).substring(7);
  return {
    id: `article-${id}`,
    title: "기준금리 인하 결정 관련 기사",
    summary: "한국은행이 기준금리를 인하했다.",
    sourceName: "테스트 출처",
    sourceType: "news_media",
    publishedAt: "2026-07-16T10:00:00+09:00",
    collectedAt: "2026-07-17T06:00:00+09:00",
    url: `https://example.com/article/${id}`,
    categories: ["interest_rate"],
    language: "ko",
    ...overrides,
  };
}

function createMockAdapter(
  sourceName: string,
  articles: Article[],
): SourceAdapter {
  return {
    sourceName,
    collect: vi.fn().mockResolvedValue({
      articles,
      collectedCount: articles.length,
      acceptedCount: articles.length,
    } satisfies SourceCollectionResult),
  };
}

function createFailingAdapter(sourceName: string): SourceAdapter {
  return {
    sourceName,
    collect: vi.fn().mockRejectedValue(
      new AppError({
        code: ErrorCodes.COLLECT_SOURCE_UNAVAILABLE,
        stage: "collect",
        retryable: true,
        safeMessage: `${sourceName} 접속 실패`,
      }),
    ),
  };
}

describe("RealNewsCollector", () => {
  it("여러 소스의 기사를 수집하여 반환한다", async () => {
    const adapter1 = createMockAdapter("출처A", [
      makeArticle({ sourceName: "출처A", title: "기준금리 인하 결정 관련 기사" }),
    ]);
    const adapter2 = createMockAdapter("출처B", [
      makeArticle({ sourceName: "출처B", title: "아파트 가격 상승세 지속 전망" }),
    ]);

    const collector = new RealNewsCollector([adapter1, adapter2]);
    const result = await collector.collect({
      targetDate: "2026-07-16",
      timezone: "Asia/Seoul",
    });

    expect(result.articles.length).toBe(2);
    expect(result.sourceReports).toHaveLength(2);
    expect(result.sourceReports.every((r) => r.status === "success")).toBe(true);
  });

  it("한 출처 실패 시 나머지 수집이 계속된다", async () => {
    const successAdapter = createMockAdapter("정상출처", [
      makeArticle({ sourceName: "정상출처" }),
    ]);
    const failAdapter = createFailingAdapter("실패출처");

    const collector = new RealNewsCollector([successAdapter, failAdapter]);
    const result = await collector.collect({
      targetDate: "2026-07-16",
      timezone: "Asia/Seoul",
    });

    expect(result.articles.length).toBe(1);
    expect(result.sourceReports).toHaveLength(2);

    const successReport = result.sourceReports.find((r) => r.sourceName === "정상출처");
    const failReport = result.sourceReports.find((r) => r.sourceName === "실패출처");

    expect(successReport?.status).toBe("success");
    expect(failReport?.status).toBe("failed");
    expect(failReport?.errorCode).toBe(ErrorCodes.COLLECT_SOURCE_UNAVAILABLE);
  });

  it("모든 출처 실패를 빈 결과와 구분할 수 있다", async () => {
    const failAdapter1 = createFailingAdapter("실패1");
    const failAdapter2 = createFailingAdapter("실패2");

    const collector = new RealNewsCollector([failAdapter1, failAdapter2]);
    const result = await collector.collect({
      targetDate: "2026-07-16",
      timezone: "Asia/Seoul",
    });

    expect(result.articles).toHaveLength(0);
    expect(result.totalAccepted).toBe(0);
    expect(result.sourceReports.every((r) => r.status === "failed")).toBe(true);
  });

  it("중복 기사를 제거한다", async () => {
    const sameArticle = makeArticle({
      id: "same-id",
      url: "https://example.com/same",
      sourceName: "출처A",
    });

    const adapter = createMockAdapter("출처A", [sameArticle, sameArticle]);

    const collector = new RealNewsCollector([adapter]);
    const result = await collector.collect({
      targetDate: "2026-07-16",
      timezone: "Asia/Seoul",
    });

    expect(result.articles).toHaveLength(1);
    expect(result.totalRejected).toBeGreaterThanOrEqual(1);
  });

  it("날짜 범위 밖 기사를 제외한다", async () => {
    const adapter = createMockAdapter("출처A", [
      makeArticle({
        sourceName: "출처A",
        publishedAt: "2026-07-16T10:00:00+09:00",
      }),
      makeArticle({
        sourceName: "출처A",
        publishedAt: "2026-07-17T10:00:00+09:00",
      }),
    ]);

    const collector = new RealNewsCollector([adapter]);
    const result = await collector.collect({
      targetDate: "2026-07-16",
      timezone: "Asia/Seoul",
    });

    expect(result.articles).toHaveLength(1);
  });

  it("품질이 낮은 기사를 제외한다", async () => {
    const adapter = createMockAdapter("출처A", [
      makeArticle({ sourceName: "출처A", title: "정상 기사 제목입니다" }),
      makeArticle({
        sourceName: "출처A",
        title: "[AD] 광고 기사 제목입니다",
        url: "https://example.com/ad",
      }),
    ]);

    const collector = new RealNewsCollector([adapter]);
    const result = await collector.collect({
      targetDate: "2026-07-16",
      timezone: "Asia/Seoul",
    });

    expect(result.articles).toHaveLength(1);
    expect(result.articles[0].title).toBe("정상 기사 제목입니다");
  });

  it("maxArticles 제한을 적용한다", async () => {
    const articles = Array.from({ length: 10 }, (_, i) =>
      makeArticle({
        sourceName: "출처A",
        url: `https://example.com/${i}`,
        id: `id-${i}`,
      }),
    );
    const adapter = createMockAdapter("출처A", articles);

    const collector = new RealNewsCollector([adapter]);
    const result = await collector.collect({
      targetDate: "2026-07-16",
      timezone: "Asia/Seoul",
      maxArticles: 5,
    });

    expect(result.articles.length).toBeLessThanOrEqual(5);
  });

  it("CollectNewsResult 계약을 준수한다", async () => {
    const adapter = createMockAdapter("출처A", [
      makeArticle({ sourceName: "출처A" }),
    ]);

    const collector = new RealNewsCollector([adapter]);
    const result = await collector.collect({
      targetDate: "2026-07-16",
      timezone: "Asia/Seoul",
    });

    expect(result).toHaveProperty("targetDate", "2026-07-16");
    expect(result).toHaveProperty("articles");
    expect(result).toHaveProperty("sourceReports");
    expect(result).toHaveProperty("totalCollected");
    expect(result).toHaveProperty("totalAccepted");
    expect(result).toHaveProperty("totalRejected");
    expect(Array.isArray(result.articles)).toBe(true);
    expect(Array.isArray(result.sourceReports)).toBe(true);
    expect(typeof result.totalCollected).toBe("number");
    expect(typeof result.totalAccepted).toBe("number");
    expect(typeof result.totalRejected).toBe("number");
  });

  it("소스 어댑터 없이 생성하면 기본 어댑터를 사용한다", () => {
    const collector = new RealNewsCollector();
    expect(collector).toBeDefined();
  });

  it("기본 어댑터에 올바른 소스가 포함된다", () => {
    const adapters = createDefaultAdapters();
    const sourceNames = adapters.map((a) => a.sourceName);

    expect(sourceNames).toContain("연합뉴스");
    expect(sourceNames).toContain("한국경제");
    expect(sourceNames).toContain("매일경제");
    expect(sourceNames).toContain("SBS Biz");
    expect(sourceNames).toContain("서울경제");
    expect(sourceNames).toContain("뉴시스");
    expect(sourceNames).toContain("머니투데이");
    expect(sourceNames).toContain("세계일보");
    expect(sourceNames).toContain("경향신문");
    expect(sourceNames).toContain("동아일보");
    expect(sourceNames).not.toContain("KBS");
    expect(adapters).toHaveLength(10);
  });

  it("소스 리포트에 durationMs가 포함된다", async () => {
    const adapter = createMockAdapter("출처A", [
      makeArticle({ sourceName: "출처A" }),
    ]);

    const collector = new RealNewsCollector([adapter]);
    const result = await collector.collect({
      targetDate: "2026-07-16",
      timezone: "Asia/Seoul",
    });

    const report = result.sourceReports[0];
    expect(report.durationMs).toBeDefined();
    expect(typeof report.durationMs).toBe("number");
    expect(report.durationMs).toBeGreaterThanOrEqual(0);
  });

  it("Non-AppError 실패도 정상적으로 처리한다", async () => {
    const adapter: SourceAdapter = {
      sourceName: "에러출처",
      collect: vi.fn().mockRejectedValue(new Error("일반 오류")),
    };

    const collector = new RealNewsCollector([adapter]);
    const result = await collector.collect({
      targetDate: "2026-07-16",
      timezone: "Asia/Seoul",
    });

    expect(result.articles).toHaveLength(0);
    expect(result.sourceReports[0].status).toBe("failed");
    expect(result.sourceReports[0].errorMessage).toBe("일반 오류");
  });
});

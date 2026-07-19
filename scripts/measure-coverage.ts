/**
 * RSS 수집 커버리지 측정 스크립트
 *
 * 현재 RSS 소스의 24시간 수집 결과를 측정하여
 * 언론사별, 카테고리별 분포를 분석합니다.
 *
 * Usage: npx tsx scripts/measure-coverage.ts
 */

import { createDefaultAdapters } from "../src/collectors/RealNewsCollector.js";
import { classifyCategories } from "../src/collectors/filters/categoryClassifier.js";
import { removeDuplicates } from "../src/collectors/filters/duplicateRemover.js";
import { validateQuality } from "../src/collectors/filters/qualityValidator.js";
import { scoreRelevance } from "../src/collectors/filters/relevanceScorer.js";
import { selectWithDiversity } from "../src/collectors/filters/diversitySelector.js";
import { NewsCategoryValues } from "../src/domain/article.js";
import type { Article, NewsCategory } from "../src/domain/article.js";

// 24시간 윈도우 설정 (현재 시간 기준 24시간 전 ~ 현재)
function get24HourWindow(): { startTime: string; endTime: string; targetDate: string } {
  const now = new Date();
  const start = new Date(now.getTime() - 24 * 60 * 60 * 1000);

  const formatKST = (d: Date): string => {
    const kstOffset = 9 * 60 * 60 * 1000;
    const utcOffset = d.getTimezoneOffset() * 60 * 1000;
    const kst = new Date(d.getTime() + kstOffset + utcOffset);
    const y = kst.getFullYear();
    const mo = String(kst.getMonth() + 1).padStart(2, "0");
    const day = String(kst.getDate()).padStart(2, "0");
    const h = String(kst.getHours()).padStart(2, "0");
    const mi = String(kst.getMinutes()).padStart(2, "0");
    const s = String(kst.getSeconds()).padStart(2, "0");
    return `${y}-${mo}-${day}T${h}:${mi}:${s}+09:00`;
  };

  const formatDate = (d: Date): string => {
    const kstOffset = 9 * 60 * 60 * 1000;
    const utcOffset = d.getTimezoneOffset() * 60 * 1000;
    const kst = new Date(d.getTime() + kstOffset + utcOffset);
    const y = kst.getFullYear();
    const mo = String(kst.getMonth() + 1).padStart(2, "0");
    const day = String(kst.getDate()).padStart(2, "0");
    return `${y}-${mo}-${day}`;
  };

  return {
    startTime: formatKST(start),
    endTime: formatKST(now),
    targetDate: formatDate(now),
  };
}

interface SourceStats {
  sourceName: string;
  rawCount: number;
  dateFilterPassed: number;
  qualityPassed: number;
  afterDedup: number;
  categoryDistribution: Record<string, number>;
  error?: string;
}

async function measureCoverage(): Promise<void> {
  const { startTime, endTime, targetDate } = get24HourWindow();

  console.log("=== RSS 수집 커버리지 측정 ===");
  console.log(`측정 시간 범위: ${startTime} ~ ${endTime}`);
  console.log(`대상 날짜: ${targetDate}`);
  console.log("");

  const adapters = createDefaultAdapters();
  const sourceStats: SourceStats[] = [];
  let allArticles: Article[] = [];

  // 1. 각 소스별 수집
  console.log("--- 1단계: 소스별 수집 ---");
  for (const adapter of adapters) {
    const stats: SourceStats = {
      sourceName: adapter.sourceName,
      rawCount: 0,
      dateFilterPassed: 0,
      qualityPassed: 0,
      afterDedup: 0,
      categoryDistribution: {},
    };

    try {
      const result = await adapter.collect(startTime, endTime);
      stats.rawCount = result.collectedCount;
      stats.dateFilterPassed = result.acceptedCount;

      // 품질 검증
      const qualityResult = validateQuality(result.articles);
      stats.qualityPassed = qualityResult.valid.length;

      // 카테고리 분포
      for (const article of qualityResult.valid) {
        for (const cat of article.categories) {
          stats.categoryDistribution[cat] = (stats.categoryDistribution[cat] ?? 0) + 1;
        }
      }

      allArticles.push(...qualityResult.valid);
      console.log(`  ${adapter.sourceName}: RSS 원본 ${stats.rawCount}건, 날짜 필터 ${stats.dateFilterPassed}건, 품질 통과 ${stats.qualityPassed}건`);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      stats.error = msg;
      console.log(`  ${adapter.sourceName}: 오류 - ${msg}`);
    }

    sourceStats.push(stats);
  }

  console.log("");

  // 2. 전체 중복 제거
  console.log("--- 2단계: 중복 제거 ---");
  const beforeDedup = allArticles.length;
  const dedupResult = removeDuplicates(allArticles);
  allArticles = dedupResult.unique;
  console.log(`  중복 제거 전: ${beforeDedup}건`);
  console.log(`  중복 제거 후: ${allArticles.length}건 (중복 ${dedupResult.duplicates.length}건)`);
  console.log(`  이벤트 그룹 수: ${dedupResult.eventGroups.length}개`);
  console.log("");

  // 3. 관련성 점수
  console.log("--- 3단계: 관련성 점수 ---");
  const relevanceResult = scoreRelevance(allArticles, 0);
  const scoreDistribution: Record<number, number> = {};
  for (const s of relevanceResult.scores) {
    scoreDistribution[s.score] = (scoreDistribution[s.score] ?? 0) + 1;
  }
  console.log("  점수 분포:");
  for (let i = 5; i >= 0; i--) {
    console.log(`    ${i}점: ${scoreDistribution[i] ?? 0}건`);
  }
  console.log("");

  // 4. 다양성 선택 (현재 정책)
  console.log("--- 4단계: 다양성 선택 (현재 정책) ---");
  const relevanceFiltered = scoreRelevance(allArticles, 2);
  const diversityResult = selectWithDiversity(
    relevanceFiltered.filtered,
    relevanceFiltered.scores,
  );
  console.log(`  관련성 2점 이상: ${relevanceFiltered.filtered.length}건`);
  console.log(`  다양성 선택: ${diversityResult.selected.length}건`);
  console.log(`  출처별 제한 초과 제외: ${diversityResult.stats.excludedBySourceLimit}건`);
  console.log(`  카테고리별 제한 초과 제외: ${diversityResult.stats.excludedByCategoryLimit}건`);
  console.log(`  관련성 부족 제외: ${diversityResult.stats.excludedByRelevance}건`);
  console.log("");

  // 5. 카테고리별 통계
  console.log("--- 5단계: 카테고리별 분포 (중복 제거 후) ---");
  const categoryStats: Record<string, { total: number; sources: Set<string> }> = {};
  for (const article of allArticles) {
    for (const cat of article.categories) {
      if (!categoryStats[cat]) {
        categoryStats[cat] = { total: 0, sources: new Set() };
      }
      categoryStats[cat].total++;
      categoryStats[cat].sources.add(article.sourceName);
    }
  }

  const allCategories = NewsCategoryValues;
  for (const cat of allCategories) {
    const stat = categoryStats[cat];
    if (stat) {
      console.log(`  ${cat}: ${stat.total}건 (출처: ${[...stat.sources].join(", ")})`);
    } else {
      console.log(`  ${cat}: 0건 *** 부족 ***`);
    }
  }
  console.log("");

  // 6. 부족 분야 판정
  console.log("--- 6단계: 부족 분야 판정 ---");
  const weakCategories: string[] = [];
  const emptyCategories: string[] = [];
  for (const cat of allCategories) {
    if (cat === "other") continue;
    const stat = categoryStats[cat];
    if (!stat || stat.total === 0) {
      emptyCategories.push(cat);
    } else if (stat.total <= 2 || stat.sources.size <= 1) {
      weakCategories.push(cat);
    }
  }

  if (emptyCategories.length > 0) {
    console.log(`  수집 없음: ${emptyCategories.join(", ")}`);
  }
  if (weakCategories.length > 0) {
    console.log(`  부족 (3건 이하 또는 단일 출처): ${weakCategories.join(", ")}`);
  }
  if (emptyCategories.length === 0 && weakCategories.length === 0) {
    console.log("  모든 카테고리 충분");
  }
  console.log("");

  // 7. 출처별 최종 선택 분포
  console.log("--- 7단계: 다양성 선택 후 출처별 분포 ---");
  for (const [source, count] of Object.entries(diversityResult.stats.bySource)) {
    console.log(`  ${source}: ${count}건`);
  }
  console.log("");

  console.log("--- 7단계: 다양성 선택 후 카테고리별 분포 ---");
  for (const [cat, count] of Object.entries(diversityResult.stats.byCategory)) {
    console.log(`  ${cat}: ${count}건`);
  }
  console.log("");

  // 8. 선택된 기사 목록
  console.log("--- 선택된 기사 목록 ---");
  for (const article of diversityResult.selected) {
    const score = relevanceFiltered.scores.find((s) => s.articleId === article.id);
    console.log(`  [${article.categories.join(",")}] ${article.title}`);
    console.log(`    출처: ${article.sourceName} | 관련성: ${score?.score ?? "?"}점`);
  }

  console.log("");
  console.log("=== 측정 완료 ===");
}

measureCoverage().catch((e) => {
  console.error("측정 실패:", e);
  process.exit(1);
});

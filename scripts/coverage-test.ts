/**
 * 커버리지 테스트: 최근 24시간 뉴스를 수집하고 통계를 출력합니다.
 * (3시간 범위는 토요일 아침이라 기사가 적을 수 있으므로 24시간으로 확장)
 */
import { RealNewsCollector, createDefaultAdapters } from "../src/collectors/RealNewsCollector.js";
import { scoreRelevance } from "../src/collectors/filters/relevanceScorer.js";

async function main() {
  const now = new Date();
  const endTime = now.toISOString();
  
  // 최근 24시간
  const start24h = new Date(now.getTime() - 24 * 60 * 60 * 1000);
  const startTime24h = start24h.toISOString();
  
  // 최근 3시간
  const start3h = new Date(now.getTime() - 3 * 60 * 60 * 1000);
  const startTime3h = start3h.toISOString();

  console.log(`\n=== 커버리지 테스트 ===`);
  console.log(`현재 시각: ${now.toLocaleString("ko-KR", { timeZone: "Asia/Seoul" })} KST`);
  console.log(`24시간 범위: ${start24h.toLocaleString("ko-KR", { timeZone: "Asia/Seoul" })} ~ ${now.toLocaleString("ko-KR", { timeZone: "Asia/Seoul" })}`);
  console.log(`3시간 범위: ${start3h.toLocaleString("ko-KR", { timeZone: "Asia/Seoul" })} ~ ${now.toLocaleString("ko-KR", { timeZone: "Asia/Seoul" })}`);

  const collector = new RealNewsCollector();
  
  // 24시간 수집
  console.log("\n--- 24시간 수집 중... ---\n");
  const result = await collector.collect({
    targetDate: now.toISOString().split("T")[0]!,
    timezone: "Asia/Seoul",
    startTime: startTime24h,
    endTime: endTime,
  });

  // 소스별 통계
  console.log("| 소스 | 상태 | 원본 수집 | 품질 통과 | 소요시간 |");
  console.log("|------|------|----------:|----------:|--------:|");
  for (const report of result.sourceReports) {
    console.log(`| ${report.sourceName.padEnd(10)} | ${report.status.padEnd(7)} | ${String(report.collectedCount).padStart(3)} | ${String(report.acceptedCount).padStart(3)} | ${report.durationMs ? report.durationMs + "ms" : "N/A"} |`);
  }

  console.log(`\n총 수집: ${result.totalCollected}`);
  console.log(`총 수락: ${result.totalAccepted}`);
  console.log(`총 제거: ${result.totalRejected}`);

  // 3시간 내 기사만 필터링
  const articles3h = result.articles.filter(a => {
    const pubTime = new Date(a.publishedAt).getTime();
    return pubTime >= start3h.getTime();
  });
  
  console.log(`\n최근 3시간 기사: ${articles3h.length}건`);

  // 카테고리별 통계
  const catCounts = new Map<string, number>();
  for (const article of result.articles) {
    for (const cat of article.categories) {
      catCounts.set(cat, (catCounts.get(cat) || 0) + 1);
    }
  }

  console.log("\n| 카테고리 | 기사 수 |");
  console.log("|----------|--------:|");
  const catLabels: Record<string, string> = {
    interest_rate: "금리",
    loan: "대출",
    housing: "부동산",
    deposit_saving: "예금·적금",
    investment: "주식/투자",
    tax: "세금",
    employment_income: "고용",
    pension: "연금",
    cost_of_living: "물가·환율",
    exchange_rate: "환율",
    jeonse_monthly_rent: "전세·월세",
    subscription: "청약",
    insurance: "보험",
    government_support: "정부지원",
    household_debt: "가계부채",
    other: "기타",
  };

  for (const [cat, count] of [...catCounts.entries()].sort((a, b) => b[1] - a[1])) {
    console.log(`| ${(catLabels[cat] || cat).padEnd(10)} | ${String(count).padStart(3)} |`);
  }

  // 소스별 기사 분포
  const sourceCounts = new Map<string, number>();
  for (const article of result.articles) {
    sourceCounts.set(article.sourceName, (sourceCounts.get(article.sourceName) || 0) + 1);
  }
  
  console.log("\n| 소스 | 최종 기사 수 |");
  console.log("|------|------------:|");
  for (const [source, count] of [...sourceCounts.entries()].sort((a, b) => b[1] - a[1])) {
    console.log(`| ${source.padEnd(10)} | ${String(count).padStart(3)} |`);
  }

  // 새로 추가된 소스에서 온 기사 예시
  const newSources = ["세계일보", "경향신문", "동아일보"];
  console.log("\n=== 신규 소스 기사 예시 ===\n");
  for (const source of newSources) {
    const articles = result.articles.filter(a => a.sourceName === source).slice(0, 3);
    console.log(`[${source}] (${articles.length}건 중 최대 3개)`);
    for (const a of articles) {
      console.log(`  - ${a.title}`);
      console.log(`    ${a.url}`);
      console.log(`    ${a.publishedAt}`);
    }
    console.log();
  }

  // 관련성 점수
  const relevanceResults = result.articles.map(a => ({
    article: a,
    score: scoreRelevance(a),
  }));
  
  const highRelevance = relevanceResults.filter(r => r.score.score >= 4);
  console.log(`\n=== 고관련성 기사 (점수 4+): ${highRelevance.length}건 ===\n`);
  for (const r of highRelevance.slice(0, 10)) {
    console.log(`  [${r.score.score}] ${r.article.sourceName} - ${r.article.title}`);
  }
}

main().catch(console.error);

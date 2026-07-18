import { describe, it, expect } from "vitest";
import { classifyCategories, containsExcludedTopic } from "../../../src/collectors/filters/categoryClassifier.js";

describe("classifyCategories", () => {
  it("기준금리 기사를 interest_rate로 분류한다", () => {
    const categories = classifyCategories(
      "한국은행, 기준금리 0.25%p 인하 결정",
      "한국은행 금융통화위원회가 기준금리를 인하했다.",
    );

    expect(categories).toContain("interest_rate");
  });

  it("부동산 기사를 housing으로 분류한다", () => {
    const categories = classifyCategories(
      "서울 아파트 매매가 3주 연속 상승",
      "서울 주요 지역 아파트 매매가격이 상승세를 이어가고 있다.",
    );

    expect(categories).toContain("housing");
  });

  it("전세 기사를 jeonse_monthly_rent로 분류한다", () => {
    const categories = classifyCategories(
      "전세보증금 별도관리 제도 검토 착수",
      "전세보증금을 별도 기관이 관리하는 방안이 검토되고 있다.",
    );

    expect(categories).toContain("jeonse_monthly_rent");
  });

  it("재테크 기사를 deposit_saving으로 분류한다", () => {
    const categories = classifyCategories(
      "주요 은행 정기예금 금리 일제히 인하",
      "정기예금 금리가 하락하고 있다.",
    );

    expect(categories).toContain("deposit_saving");
  });

  it("대출 기사를 loan으로 분류한다", () => {
    const categories = classifyCategories(
      "주택담보대출 금리 추가 인하 전망",
      "DSR 규제 완화로 대출 한도가 늘어날 전망이다.",
    );

    expect(categories).toContain("loan");
  });

  it("세금 기사를 tax로 분류한다", () => {
    const categories = classifyCategories(
      "양도소득세 비과세 기준 변경",
      "취득세와 양도세 관련 제도가 바뀐다.",
    );

    expect(categories).toContain("tax");
  });

  it("하나의 기사가 여러 카테고리에 해당할 수 있다", () => {
    const categories = classifyCategories(
      "주택담보대출 금리 인상, 부동산 시장 영향",
      "대출 규제 강화로 아파트 매매 시장이 위축될 전망이다.",
    );

    expect(categories).toContain("loan");
    expect(categories).toContain("housing");
  });

  it("연예 기사를 제외한다", () => {
    const categories = classifyCategories(
      "아이돌 그룹 콘서트 경제효과 분석",
      "아이돌 콘서트가 지역 경제에 미치는 영향을 분석했다.",
    );

    expect(categories).toHaveLength(0);
  });

  it("스포츠 기사를 제외한다", () => {
    const categories = classifyCategories(
      "프로야구 관중 수 역대 최다 기록",
      "프로야구 시즌 관중 수가 역대 최다를 기록했다.",
    );

    expect(categories).toHaveLength(0);
  });

  it("경제와 무관한 일반 기사에 빈 배열을 반환한다", () => {
    const categories = classifyCategories(
      "오늘의 날씨 전국 맑음",
      "전국적으로 맑은 날씨가 이어지겠습니다.",
    );

    expect(categories).toHaveLength(0);
  });

  it("물가 관련 기사를 cost_of_living으로 분류한다", () => {
    const categories = classifyCategories(
      "소비자물가 4개월 연속 상승",
      "소비자물가지수가 전년 대비 상승했다.",
    );

    expect(categories).toContain("cost_of_living");
  });

  it("환율 기사를 exchange_rate로 분류한다", () => {
    const categories = classifyCategories(
      "원달러 환율 1,300원 돌파",
      "달러 강세로 원달러 환율이 상승했다.",
    );

    expect(categories).toContain("exchange_rate");
  });

  it("정부지원 기사를 government_support로 분류한다", () => {
    const categories = classifyCategories(
      "신혼부부 지원 정책 확대",
      "청년 및 신혼부부 대상 지원금이 확대된다.",
    );

    expect(categories).toContain("government_support");
  });
});

describe("containsExcludedTopic", () => {
  it("연예 키워드가 있으면 true", () => {
    expect(containsExcludedTopic("연예 뉴스", "")).toBe(true);
  });

  it("스포츠 키워드가 있으면 true", () => {
    expect(containsExcludedTopic("축구 경기 결과", "")).toBe(true);
  });

  it("경제 키워드만 있으면 false", () => {
    expect(containsExcludedTopic("기준금리 인하", "한국은행 금통위 결과")).toBe(false);
  });
});

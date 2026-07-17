import type { NewsCategory } from "../../domain/article.js";

const CATEGORY_KEYWORDS: Record<NewsCategory, string[]> = {
  interest_rate: [
    "기준금리", "금리 인상", "금리 인하", "금리 동결", "한국은행 금리",
    "기준 금리", "금통위", "금융통화위원회", "콜금리", "시장금리",
  ],
  deposit_saving: [
    "예금", "적금", "정기예금", "정기적금", "파킹통장", "CMA",
    "예금금리", "적금금리", "저축", "예치금",
  ],
  loan: [
    "대출", "주택담보대출", "신용대출", "전세대출", "대출금리",
    "LTV", "DSR", "DTI", "대출 규제", "대출 한도",
    "주담대", "가계대출",
  ],
  housing: [
    "아파트", "주택", "부동산", "매매가", "집값",
    "재건축", "재개발", "공급", "분양", "부동산 대책",
    "부동산 정책", "주택가격", "주택 시장", "부동산 시장",
    "토지", "건설", "주거", "공공주택",
  ],
  jeonse_monthly_rent: [
    "전세", "월세", "임대차", "전세금", "전세보증금",
    "임차인", "임대인", "전세사기", "전월세", "보증금",
    "전세가", "임대료",
  ],
  subscription: [
    "청약", "분양", "당첨", "청약통장", "특별공급",
    "일반공급", "모집공고", "사전청약",
  ],
  tax: [
    "세금", "취득세", "양도세", "양도소득세", "보유세",
    "종합부동산세", "종부세", "재산세", "소득세", "부가세",
    "법인세", "상속세", "증여세", "세율", "과세",
    "연말정산", "세제", "조세",
  ],
  pension: [
    "연금", "국민연금", "퇴직연금", "연금저축", "개인연금",
    "IRP", "연금보험", "노후",
  ],
  insurance: [
    "보험", "건강보험", "건강보험료", "실손보험", "자동차보험",
    "보험료", "보험금",
  ],
  cost_of_living: [
    "물가", "소비자물가", "생활비", "공공요금", "전기요금",
    "가스요금", "교통요금", "수도요금", "최저임금",
    "식료품", "유가", "기름값", "물가지수",
  ],
  exchange_rate: [
    "환율", "원달러", "달러", "원화", "외환",
    "원/달러", "엔화", "유로", "위안화",
  ],
  investment: [
    "ETF", "펀드", "주식", "채권", "투자",
    "ISA", "증시", "코스피", "코스닥", "배당",
    "주가", "시가총액", "상장",
  ],
  government_support: [
    "정부 지원", "지원금", "보조금", "바우처", "출산지원",
    "육아지원", "청년 지원", "신혼부부 지원", "복지",
    "지원 정책", "지원제도", "혜택",
  ],
  employment_income: [
    "고용", "실업률", "취업", "일자리", "임금",
    "소득", "근로소득", "최저시급", "실업급여",
    "고용률", "경제활동", "노동",
  ],
  household_debt: [
    "가계부채", "가계 부채", "가처분소득", "부채비율",
    "부채", "빚", "연체율", "다중채무",
  ],
  other: [],
};

const EXCLUDE_KEYWORDS: string[] = [
  "연예", "스포츠", "아이돌", "드라마", "야구", "축구",
  "배구", "농구", "골프", "올림픽", "K리그", "프로야구",
  "영화", "예능", "가수", "배우", "콘서트",
];

/**
 * Classifies an article into one or more categories based on title and summary keywords.
 * Returns empty array if no category matches or if excluded keywords are found.
 */
export function classifyCategories(
  title: string,
  summary: string,
): NewsCategory[] {
  const text = `${title} ${summary}`;

  for (const keyword of EXCLUDE_KEYWORDS) {
    if (text.includes(keyword)) {
      return [];
    }
  }

  const matched: NewsCategory[] = [];

  for (const [category, keywords] of Object.entries(CATEGORY_KEYWORDS)) {
    if (category === "other") continue;

    for (const keyword of keywords) {
      if (text.includes(keyword)) {
        matched.push(category as NewsCategory);
        break;
      }
    }
  }

  return matched;
}

/**
 * Checks whether the article text contains excluded keywords (entertainment, sports, etc.)
 */
export function containsExcludedTopic(title: string, summary: string): boolean {
  const text = `${title} ${summary}`;
  return EXCLUDE_KEYWORDS.some((keyword) => text.includes(keyword));
}

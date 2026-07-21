package com.economicbriefing.collector.filter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.economicbriefing.domain.article.NewsCategory;
import org.springframework.stereotype.Component;

@Component
public class CategoryClassifier {

    private static final Map<NewsCategory, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();
    private static final List<String> EXCLUDE_KEYWORDS = List.of(
            "연예", "스포츠", "아이돌", "드라마", "야구", "축구",
            "배구", "농구", "골프", "올림픽", "K리그", "프로야구",
            "영화", "예능", "가수", "배우", "콘서트"
    );

    static {
        CATEGORY_KEYWORDS.put(NewsCategory.INTEREST_RATE, List.of(
                "기준금리", "금리 인상", "금리 인하", "금리 동결", "한국은행 금리",
                "기준 금리", "금통위", "금융통화위원회", "콜금리", "시장금리",
                "통화정책", "금리 전망", "금리 변동", "기준금리 동결"
        ));
        CATEGORY_KEYWORDS.put(NewsCategory.DEPOSIT_SAVING, List.of(
                "예금", "적금", "정기예금", "정기적금", "파킹통장", "CMA",
                "예금금리", "적금금리", "저축", "예치금",
                "자유적금", "정기저축", "저축은행"
        ));
        CATEGORY_KEYWORDS.put(NewsCategory.LOAN, List.of(
                "대출", "주택담보대출", "신용대출", "전세대출", "대출금리",
                "LTV", "DSR", "DTI", "대출 규제", "대출 한도",
                "주담대", "가계대출", "변동금리", "고정금리", "대환대출",
                "마이너스통장", "스트레스DSR"
        ));
        CATEGORY_KEYWORDS.put(NewsCategory.HOUSING, List.of(
                "아파트", "주택", "부동산", "매매가", "집값",
                "재건축", "재개발", "공급", "분양", "부동산 대책",
                "부동산 정책", "주택가격", "주택 시장", "부동산 시장",
                "토지", "건설", "주거", "공공주택",
                "분양가", "입주물량", "주택공급", "신축", "준공"
        ));
        CATEGORY_KEYWORDS.put(NewsCategory.JEONSE_MONTHLY_RENT, List.of(
                "전세 ", "전세가", "전세금", "전세보증금", "전세난", "전세값",
                "전세사기", "전세대출", "전세계약", "전월세",
                "월세", "임대차", "임차인", "임대인", "보증금",
                "임대료", "임대차보호법", "계약갱신"
        ));
        CATEGORY_KEYWORDS.put(NewsCategory.SUBSCRIPTION, List.of(
                "청약", "분양", "당첨", "청약통장", "특별공급",
                "일반공급", "모집공고", "사전청약"
        ));
        CATEGORY_KEYWORDS.put(NewsCategory.TAX, List.of(
                "세금", "취득세", "양도세", "양도소득세", "보유세",
                "종합부동산세", "종부세", "재산세", "소득세", "부가세",
                "법인세", "상속세", "증여세", "세율", "과세",
                "연말정산", "세제", "조세", "근로장려금", "EITC"
        ));
        CATEGORY_KEYWORDS.put(NewsCategory.PENSION, List.of(
                "연금", "국민연금", "퇴직연금", "연금저축", "개인연금",
                "IRP", "연금보험", "노후 대비", "노후 준비", "노후 생활",
                "노후 자금", "노후 불안", "노후대비", "노후자금"
        ));
        CATEGORY_KEYWORDS.put(NewsCategory.INSURANCE, List.of(
                "보험", "건강보험", "건강보험료", "실손보험", "자동차보험",
                "보험료", "보험금", "보장보험", "연금보험"
        ));
        CATEGORY_KEYWORDS.put(NewsCategory.COST_OF_LIVING, List.of(
                "물가", "소비자물가", "생활비", "공공요금", "전기요금",
                "가스요금", "교통요금", "수도요금", "최저임금",
                "식료품", "국제유가", "유가 상승", "유가 하락", "기름값", "물가지수",
                "통신비", "구독료", "카드혜택", "할인"
        ));
        CATEGORY_KEYWORDS.put(NewsCategory.EXCHANGE_RATE, List.of(
                "환율", "원달러", "달러", "원화", "외환",
                "원/달러", "엔화", "유로", "위안화"
        ));
        CATEGORY_KEYWORDS.put(NewsCategory.INVESTMENT, List.of(
                "ETF", "펀드", "주식", "채권", "투자",
                "ISA", "증시", "코스피", "코스닥", "배당",
                "주가", "시가총액", "상장", "공모주", "리츠", "REIT",
                "주주총회", "배당금", "상장폐지",
                "자산배분", "재테크", "금융상품",
                "신탁", "MMF", "RP", "ELS"
        ));
        CATEGORY_KEYWORDS.put(NewsCategory.GOVERNMENT_SUPPORT, List.of(
                "정부 지원", "지원금", "보조금", "바우처", "출산지원",
                "육아지원", "청년 지원", "신혼부부 지원", "복지",
                "지원 정책", "지원제도", "혜택",
                "청년도약", "주거급여", "아동수당", "육아휴직"
        ));
        CATEGORY_KEYWORDS.put(NewsCategory.EMPLOYMENT_INCOME, List.of(
                "고용", "실업률", "취업", "일자리", "임금",
                "소득", "근로소득", "최저시급", "실업급여",
                "고용률", "경제활동", "노동", "구직", "채용"
        ));
        CATEGORY_KEYWORDS.put(NewsCategory.HOUSEHOLD_DEBT, List.of(
                "가계부채", "가계 부채", "가처분소득", "부채비율",
                "부채", "빚", "연체율", "다중채무", "채무조정"
        ));
    }

    public List<NewsCategory> classify(String title, String summary) {
        String text = title + " " + (summary != null ? summary : "");

        for (String keyword : EXCLUDE_KEYWORDS) {
            if (text.contains(keyword)) {
                return List.of();
            }
        }

        List<NewsCategory> matched = new ArrayList<>();
        for (Map.Entry<NewsCategory, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            if (entry.getKey() == NewsCategory.OTHER) continue;
            for (String keyword : entry.getValue()) {
                if (text.contains(keyword)) {
                    matched.add(entry.getKey());
                    break;
                }
            }
        }

        return matched;
    }

    public boolean containsExcludedTopic(String title, String summary) {
        String text = title + " " + (summary != null ? summary : "");
        return EXCLUDE_KEYWORDS.stream().anyMatch(text::contains);
    }
}

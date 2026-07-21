package com.economicbriefing.collector.filter;

import java.util.ArrayList;
import java.util.List;

import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.NewsCategory;
import org.springframework.stereotype.Component;

@Component
public class RelevanceScorer {

    private static final List<RelevanceGroup> RELEVANCE_KEYWORDS = List.of(
            new RelevanceGroup(5, List.of(
                    "기준금리 인하", "기준금리 인상", "기준금리 동결",
                    "대출 규제", "DSR 완화", "DSR 강화", "LTV 완화", "LTV 강화",
                    "대출금리 인하", "대출금리 인상",
                    "청약 제도 변경", "청약 자격",
                    "세율 변경", "세율 인상", "세율 인하",
                    "취득세 인하", "취득세 인상", "양도세 비과세",
                    "종부세 완화", "종부세 강화",
                    "전세보증금 반환", "전세보증금 관리", "전세사기 방지",
                    "건강보험료 인상", "건강보험료 인하",
                    "스트레스DSR"
            )),
            new RelevanceGroup(4, List.of(
                    "아파트 가격", "집값 상승", "집값 하락",
                    "전세가 상승", "전세가 하락", "월세 상승",
                    "국민연금 개혁", "국민연금 수령", "퇴직연금",
                    "전세 정책", "임대차 보호",
                    "특별공급", "사전청약",
                    "예금금리", "적금금리",
                    "보험료 인상", "실손보험",
                    "주택담보대출", "전세대출",
                    "연말정산", "소득공제", "세액공제",
                    "분양가 상한제", "입주물량", "주택공급",
                    "IRP", "연금저축"
            )),
            new RelevanceGroup(3, List.of(
                    "물가 상승", "소비자물가", "물가지수",
                    "환율 상승", "환율 하락", "원달러",
                    "ETF", "ISA", "펀드",
                    "공공요금", "전기요금", "가스요금", "교통요금",
                    "최저임금", "최저시급",
                    "실업급여", "고용보험",
                    "지원금", "바우처", "출산지원", "육아지원",
                    "가계부채", "가처분소득",
                    "금통위", "금융통화위원회",
                    "자산배분", "재테크", "금융상품",
                    "통신비", "구독료", "카드혜택",
                    "주주총회", "배당금", "공모주",
                    "CMA", "MMF",
                    "코스피", "코스닥", "증시",
                    "국제유가", "유가 상승", "유가 하락", "기름값",
                    "부동산 시장",
                    "노후 대비", "노후 준비", "노후 생활", "노후 자금", "노후 불안",
                    "연금보험", "분양", "집값"
            )),
            new RelevanceGroup(2, List.of(
                    "건설 경기",
                    "수출", "수입", "무역수지",
                    "경제성장률", "GDP",
                    "기업 실적", "분기 실적",
                    "금융감독원", "금융위원회"
            )),
            new RelevanceGroup(1, List.of(
                    "인사", "임원 선임", "대표이사",
                    "합병", "인수", "IPO",
                    "해외 진출", "해외 시장",
                    "기업 간 분쟁", "소송"
            ))
    );

    public RelevanceScoringResult score(List<Article> articles, int minScore) {
        List<RelevanceScore> scores = new ArrayList<>();
        List<Article> filtered = new ArrayList<>();
        List<Article> excluded = new ArrayList<>();

        for (Article article : articles) {
            String text = article.title() + " " + (article.summary() != null ? article.summary() : "");
            int bestScore = 0;
            List<String> matchedKeywords = new ArrayList<>();

            for (RelevanceGroup group : RELEVANCE_KEYWORDS) {
                for (String keyword : group.keywords()) {
                    if (text.contains(keyword)) {
                        matchedKeywords.add(keyword);
                        if (group.score() > bestScore) {
                            bestScore = group.score();
                        }
                    }
                }
            }

            // Articles with economic categories but no keyword match get baseline score of 2
            if (bestScore == 0 && article.categories() != null && !article.categories().isEmpty()) {
                boolean hasEconomicCategory = article.categories().stream()
                        .anyMatch(c -> c != NewsCategory.OTHER);
                if (hasEconomicCategory) {
                    bestScore = 2;
                }
            }

            scores.add(new RelevanceScore(article.id(), bestScore, matchedKeywords));

            if (bestScore >= minScore) {
                filtered.add(article);
            } else {
                excluded.add(article);
            }
        }

        return new RelevanceScoringResult(scores, filtered, excluded);
    }

    public record RelevanceScore(String articleId, int score, List<String> matchedKeywords) {}
    public record RelevanceScoringResult(List<RelevanceScore> scores, List<Article> filtered, List<Article> excluded) {}
    private record RelevanceGroup(int score, List<String> keywords) {}
}

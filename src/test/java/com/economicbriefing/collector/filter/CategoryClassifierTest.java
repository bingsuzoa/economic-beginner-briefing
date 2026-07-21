package com.economicbriefing.collector.filter;

import java.util.List;

import com.economicbriefing.domain.article.NewsCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CategoryClassifierTest {

    private final CategoryClassifier classifier = new CategoryClassifier();

    @Test
    void shouldClassifyInterestRateArticle() {
        List<NewsCategory> categories = classifier.classify("한국은행 기준금리 인하 결정", "금통위가 기준금리를 인하했다");
        assertTrue(categories.contains(NewsCategory.INTEREST_RATE));
    }

    @Test
    void shouldClassifyHousingArticle() {
        List<NewsCategory> categories = classifier.classify("서울 아파트 가격 상승세 지속", "부동산 시장 전망");
        assertTrue(categories.contains(NewsCategory.HOUSING));
    }

    @Test
    void shouldClassifyLoanArticle() {
        List<NewsCategory> categories = classifier.classify("주택담보대출 금리 인하", "DSR 규제 완화");
        assertTrue(categories.contains(NewsCategory.LOAN));
    }

    @Test
    void shouldClassifyMultipleCategories() {
        List<NewsCategory> categories = classifier.classify("전세대출 금리 인하로 전세 시장 변화", "임대차 보호법");
        assertTrue(categories.size() >= 2);
        assertTrue(categories.contains(NewsCategory.LOAN));
        assertTrue(categories.contains(NewsCategory.JEONSE_MONTHLY_RENT));
    }

    @Test
    void shouldExcludeSportsArticle() {
        List<NewsCategory> categories = classifier.classify("프로야구 결과", "스포츠 뉴스");
        assertTrue(categories.isEmpty());
    }

    @Test
    void shouldExcludeEntertainmentArticle() {
        List<NewsCategory> categories = classifier.classify("아이돌 콘서트", "연예 뉴스");
        assertTrue(categories.isEmpty());
    }

    @Test
    void shouldReturnEmptyForNoMatch() {
        List<NewsCategory> categories = classifier.classify("일반적인 뉴스 제목", "내용 없음");
        assertTrue(categories.isEmpty());
    }

    @Test
    void shouldDetectExcludedTopics() {
        assertTrue(classifier.containsExcludedTopic("프로야구 결과", ""));
        assertFalse(classifier.containsExcludedTopic("기준금리 인하", "경제 뉴스"));
    }

    @Test
    void shouldClassifyTaxArticle() {
        List<NewsCategory> categories = classifier.classify("종부세 완화 방안", "양도소득세 개편");
        assertTrue(categories.contains(NewsCategory.TAX));
    }

    @Test
    void shouldClassifyPensionArticle() {
        List<NewsCategory> categories = classifier.classify("국민연금 개혁안", "IRP 가입자 증가");
        assertTrue(categories.contains(NewsCategory.PENSION));
    }
}

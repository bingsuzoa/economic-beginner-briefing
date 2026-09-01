package com.economicbriefing.analyzer.mock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.economicbriefing.analyzer.NewsAnalyzer;
import com.economicbriefing.analyzer.dto.AnalyzeNewsRequest;
import com.economicbriefing.analyzer.dto.AnalyzeNewsResult;
import com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse;
import com.economicbriefing.analyzer.openai.dto.RetrievalRouterResponse;
import com.economicbriefing.domain.analysis.AnalyzedNews;
import com.economicbriefing.domain.analysis.EconomicTerm;
import com.economicbriefing.domain.analysis.NewsEvidenceStatus;
import com.economicbriefing.domain.analysis.SourceReference;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.NewsCategory;
import com.economicbriefing.domain.briefing.Briefing;
import com.economicbriefing.domain.briefing.BriefingMetadata;
import com.economicbriefing.util.IdGenerator;
import com.economicbriefing.util.KstDateTimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "briefing.dry-run", havingValue = "true", matchIfMissing = true)
public class MockNewsAnalyzer implements NewsAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(MockNewsAnalyzer.class);

    private record MockAnalysisDetail(
        String easyTitle,
        List<String> threeLineSummary,
        String whatHappened,
        String whyItHappened,
        String economicImpact,
        NewsEvidenceStatus evidenceStatus,
        List<EconomicTerm> terms
    ) {}

    private static final Map<String, MockAnalysisDetail> ANALYSIS_MAP;

    static {
        Map<String, MockAnalysisDetail> map = new LinkedHashMap<>();

        map.put("mock-article-001", new MockAnalysisDetail(
                "한국은행이 금리를 낮췄어요 — 대출 이자가 줄어들 수 있습니다",
                List.of(
                        "한국은행이 기준금리를 3.25%에서 3.00%로 0.25%포인트 내렸습니다.",
                        "변동금리 대출자의 이자 부담이 줄어들 수 있습니다.",
                        "반면 예금 이자 수입은 줄어들 수 있습니다."
                ),
                "한국은행 금융통화위원회가 기준금리를 연 3.25%에서 3.00%로 0.25%포인트 내렸습니다. "
                        + "그동안 기준금리가 3.25%로 유지되면서 시중은행의 대출금리와 예금금리도 이에 맞춰 움직이고 있었는데, "
                        + "이번에 처음으로 인하가 이루어진 겁니다.",
                "경기 둔화 우려와 물가 안정세를 고려한 판단입니다. "
                        + "쉽게 말해 경제가 힘들어지고 있으니 돈을 빌리는 비용을 낮춰서 경기를 살려보자는 의도예요.",
                "기준금리가 내려가면 시중은행의 대출금리와 예금금리가 함께 내려가는 경향이 있습니다. "
                        + "이는 소비와 투자를 촉진하려는 통화정책의 일환입니다. "
                        + "변동금리 대출자의 이자 부담이 줄어들 수 있으며, 주택담보대출 예정자에게는 유리한 소식입니다. "
                        + "반면 예금·적금 이자 수입은 줄어들 수 있습니다.",
                NewsEvidenceStatus.CONFIRMED,
                List.of(new EconomicTerm(
                        "기준금리",
                        "한국은행이 정하는 금리로, 은행들이 서로 돈을 빌려줄 때 기준이 되는 금리입니다. "
                                + "시중은행의 예금금리와 대출금리는 이 기준금리에 영향을 받아 움직입니다.",
                        "기준금리가 3.00%이면 은행의 주택담보대출 금리는 보통 이보다 1~2%포인트 높은 4~5% 수준에서 형성됩니다."
                ))
        ));

        map.put("mock-article-002", new MockAnalysisDetail(
                "전세보증금을 따로 관리하는 제도가 검토되고 있어요",
                List.of(
                        "금융위원회가 전세보증금 별도관리 방안을 검토하기 시작했습니다.",
                        "전세 사기와 보증금 미반환 피해를 줄이기 위한 조치입니다.",
                        "아직 확정된 제도는 아니며 검토 단계입니다."
                ),
                "금융위원회가 전세보증금을 집주인이 아닌 별도 기관에서 관리하는 방안을 검토하기 시작했습니다. "
                        + "기존에는 세입자가 집주인에게 전세보증금을 직접 지급하면, "
                        + "집주인이 이 돈을 자유롭게 보유하고 활용할 수 있었습니다.",
                "최근 전세 사기와 보증금 미반환 피해가 사회적 문제로 대두되면서, "
                        + "세입자의 보증금을 더 안전하게 보호할 방법을 찾고 있는 겁니다.",
                "전세보증금 별도관리가 시행되면 집주인의 자금 운용에 제약이 생기고, "
                        + "전세 시장의 자금 흐름에 변화가 있을 수 있습니다. "
                        + "전세 계약 예정자는 보증금 보호 장치를 더 꼼꼼히 확인할 필요가 있습니다.",
                NewsEvidenceStatus.PROPOSED,
                List.of(new EconomicTerm(
                        "전세보증금",
                        "세입자가 집주인에게 맡기는 큰 금액의 보증금입니다. "
                                + "계약이 끝나면 돌려받아야 하는 돈으로, 그동안 집주인이 이 돈을 보유합니다.",
                        "서울의 전세보증금은 보통 수억 원 수준이며, 계약 기간은 2년이 일반적입니다."
                ))
        ));

        map.put("mock-article-003", new MockAnalysisDetail(
                "주요 은행들이 예금 금리를 내렸어요",
                List.of(
                        "KB국민, 신한, 우리 등 주요 시중은행이 정기예금 금리를 인하했습니다.",
                        "기준금리 인하의 영향으로 예금금리도 내려간 것입니다.",
                        "예금 이자 수입이 줄어들 수 있지만, 대출금리도 함께 내려갈 수 있습니다."
                ),
                "KB국민, 신한, 우리 등 주요 시중은행이 정기예금 금리를 0.1~0.2%포인트 인하했습니다. "
                        + "그동안 주요 은행의 1년 만기 정기예금 금리는 약 3.5% 수준이었는데, "
                        + "이번에 3.3~3.4% 수준으로 내려갔습니다.",
                "한국은행이 기준금리를 내리면서 은행들의 자금 조달 비용이 낮아졌고, "
                        + "이에 따라 예금금리도 함께 내려간 겁니다. "
                        + "기준금리와 예금금리는 보통 같은 방향으로 움직이기 때문에 예상된 변화라고 할 수 있습니다.",
                "예금금리 인하는 은행의 수익 구조에 영향을 주고, "
                        + "예금에서 다른 투자처로 자금이 이동할 가능성이 있습니다. "
                        + "예금 보유자의 이자 수입이 줄어들지만, 대출 보유자의 이자 부담도 줄어들 수 있습니다.",
                NewsEvidenceStatus.CONFIRMED,
                List.of(new EconomicTerm(
                        "정기예금",
                        "일정 기간 동안 돈을 은행에 맡기고, 만기 때 원금과 이자를 함께 돌려받는 상품입니다. "
                                + "보통 중도 해지하면 이자가 줄어듭니다.",
                        null
                ))
        ));

        ANALYSIS_MAP = Collections.unmodifiableMap(map);
    }

    @Override
    public AnalyzeNewsResult analyze(AnalyzeNewsRequest request) {
        log.info("[MOCK] Analyzing {} articles for {}", request.articles().size(), request.targetDate());

        List<Article> selectedArticles = request.articles().stream()
                .limit(request.maxSelectedNews())
                .toList();

        List<AnalyzedNews> news = new ArrayList<>();
        for (int i = 0; i < selectedArticles.size(); i++) {
            news.add(articleToAnalyzedNews(selectedArticles.get(i), i));
        }

        List<EconomicTerm> allTerms = news.stream()
                .flatMap(n -> n.terms().stream())
                .toList();
        List<EconomicTerm> uniqueTerms = allTerms.stream()
                .collect(Collectors.toMap(EconomicTerm::term, t -> t, (a, b) -> a, LinkedHashMap::new))
                .values().stream().toList();

        String title = request.briefingTitle() != null
                ? request.briefingTitle()
                : request.targetDate() + " 경제 브리핑";

        String briefingId = request.targetHour() != null
                ? IdGenerator.briefingId(request.targetDate(), request.targetHour())
                : IdGenerator.briefingId(request.targetDate());

        Briefing briefing = new Briefing(
                briefingId,
                request.targetDate(),
                KstDateTimeUtil.now(),
                title,
                List.of(
                        "한국은행이 기준금리를 인하하면서 대출금리와 예금금리 모두 영향을 받을 전망입니다.",
                        "전세보증금 별도관리 제도가 검토되고 있어 전세 시장에 변화가 있을 수 있습니다."
                ),
                news,
                uniqueTerms,
                new BriefingMetadata(
                        request.articles().size(),
                        request.articles().size(),
                        news.size(),
                        "mock",
                        "foundation-v1"
                )
        );

        Set<String> selectedIds = selectedArticles.stream()
                .map(Article::id)
                .collect(Collectors.toSet());
        List<String> rejectedArticleIds = request.articles().stream()
                .map(Article::id)
                .filter(id -> !selectedIds.contains(id))
                .toList();

        log.info("[MOCK] Analysis complete: selected={}, rejected={}",
                news.size(), rejectedArticleIds.size());

        ArticleAnalysisResponse articleAnalysis = new ArticleAnalysisResponse(selectedArticles.stream()
                .map(article -> new ArticleAnalysisResponse.ArticleAnalysis(article.id(), List.of(
                        new ArticleAnalysisResponse.Issue(
                                article.title(),
                                List.of(article.summary() != null ? article.summary() : article.title()),
                                List.of(), List.of(), List.of(), List.of()))))
                .toList());
        RetrievalRouterResponse routerResult = new RetrievalRouterResponse(articleAnalysis.articles().stream()
                .map(article -> new RetrievalRouterResponse.ArticleRoute(article.articleId(), article.issues().stream()
                        .map(issue -> new RetrievalRouterResponse.IssueRoute(issue.name(), false, List.of()))
                        .toList()))
                .toList());
        return new AnalyzeNewsResult(
                briefing, rejectedArticleIds, List.of(), null, articleAnalysis, routerResult);
    }

    private AnalyzedNews articleToAnalyzedNews(Article article, int index) {
        MockAnalysisDetail detail = ANALYSIS_MAP.get(article.id());

        NewsCategory category = article.categories().isEmpty()
                ? NewsCategory.OTHER
                : article.categories().get(0);

        if (detail != null) {
            return new AnalyzedNews(
                    "analyzed-" + article.id(),
                    detail.easyTitle(),
                    category,
                    Math.max(1, 5 - index),
                    detail.threeLineSummary(),
                    detail.whatHappened(),
                    detail.whyItHappened(),
                    detail.whyItHappened(),
                    detail.economicImpact(),
                    detail.terms(),
                    detail.evidenceStatus(),
                    List.of(new SourceReference(
                            article.id(), article.sourceName(), article.title(),
                            article.url(), article.publishedAt(), true
                    ))
            );
        }

        return new AnalyzedNews(
                "analyzed-" + article.id(),
                article.title(),
                category,
                Math.max(1, 5 - index),
                List.of(article.summary()),
                article.summary(),
                "확인된 내용이 부족합니다.",
                "확인된 내용이 부족합니다.",
                "확인된 내용이 부족합니다.",
                List.of(),
                NewsEvidenceStatus.EXPECTED,
                List.of(new SourceReference(
                        article.id(), article.sourceName(), article.title(),
                        article.url(), article.publishedAt(), true
                ))
        );
    }
}

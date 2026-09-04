package com.economicbriefing.analyzer.openai;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.economicbriefing.analyzer.NewsAnalyzer;
import com.economicbriefing.analyzer.dto.AnalyzeNewsRequest;
import com.economicbriefing.analyzer.dto.AnalyzeNewsResult;
import com.economicbriefing.analyzer.dto.ArticleValidationResult;
import com.economicbriefing.analyzer.openai.dto.AiResponse;
import com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse;
import com.economicbriefing.analyzer.openai.dto.ArticleAnalyzerDraftResponse;
import com.economicbriefing.analyzer.openai.dto.RetrievalRouterResponse;
import com.economicbriefing.analyzer.openai.prompt.AnalysisPromptBuilder;
import com.economicbriefing.analyzer.openai.prompt.ArticleAnalyzerPromptBuilder;
import com.economicbriefing.analyzer.openai.prompt.ArticleValidatorPromptBuilder;
import com.economicbriefing.analyzer.openai.prompt.RetrievalRouterPromptBuilder;
import com.economicbriefing.analyzer.openai.prompt.SystemPromptBuilder;
import com.economicbriefing.analyzer.openai.util.BriefingBuilder;
import com.economicbriefing.analyzer.openai.util.RetryExecutor;
import com.economicbriefing.config.AppProperties;
import com.economicbriefing.config.OpenAiProperties;
import com.economicbriefing.domain.briefing.Briefing;
import com.economicbriefing.exception.AnalyzeException;
import com.economicbriefing.exception.ErrorCode;
import com.economicbriefing.economicflow.EventCandidate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "briefing.dry-run", havingValue = "false")
public class OpenAiNewsAnalyzer implements NewsAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(OpenAiNewsAnalyzer.class);

    private final OpenAiClient aiClient;
    private final ObjectMapper objectMapper;
    private final OpenAiProperties openAiProperties;
    private final AppProperties appProperties;
    private final ArticleBodyFetcher articleBodyFetcher;
    private final com.economicbriefing.economicflow.EconomicFlowIngestor economicFlowIngestor;
    private final com.economicbriefing.economicflow.EconomicFlowContextService economicFlowContextService;
    private final com.economicbriefing.economicflow.EconomicPrincipleRetriever economicPrincipleRetriever;
    private final ArticlePresenter articlePresenter;
    private final com.economicbriefing.economicflow.EconomicFlowRetriever economicFlowRetriever;
    private final RelationValidator relationValidator;
    private final EconomicFlowJudge economicFlowJudge;
    private final RelationCandidateExtractor relationCandidateExtractor;

    @org.springframework.beans.factory.annotation.Autowired
    public OpenAiNewsAnalyzer(
            OpenAiClient aiClient,
            ObjectMapper objectMapper,
            OpenAiProperties openAiProperties,
            AppProperties appProperties,
            ArticleBodyFetcher articleBodyFetcher,
            com.economicbriefing.economicflow.EconomicFlowIngestor economicFlowIngestor,
            com.economicbriefing.economicflow.EconomicFlowContextService economicFlowContextService,
            com.economicbriefing.economicflow.EconomicPrincipleRetriever economicPrincipleRetriever,
            ArticlePresenter articlePresenter,
            com.economicbriefing.economicflow.EconomicFlowRetriever economicFlowRetriever) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.openAiProperties = openAiProperties;
        this.appProperties = appProperties;
        this.articleBodyFetcher = articleBodyFetcher;
        this.economicFlowIngestor = economicFlowIngestor;
        this.economicFlowContextService = economicFlowContextService;
        this.economicPrincipleRetriever = economicPrincipleRetriever;
        this.articlePresenter = articlePresenter;
        this.economicFlowRetriever = economicFlowRetriever;
        this.relationValidator = new RelationValidator(aiClient, objectMapper, openAiProperties, appProperties);
        this.economicFlowJudge = new EconomicFlowJudge(aiClient, objectMapper, openAiProperties, appProperties);
        this.relationCandidateExtractor = new RelationCandidateExtractor(aiClient, objectMapper, appProperties);
    }

    OpenAiNewsAnalyzer(OpenAiClient aiClient, ObjectMapper objectMapper,
            OpenAiProperties openAiProperties, AppProperties appProperties,
            ArticleBodyFetcher articleBodyFetcher) {
        this.aiClient = aiClient; this.objectMapper = objectMapper; this.openAiProperties = openAiProperties;
        this.appProperties = appProperties; this.articleBodyFetcher = articleBodyFetcher;
        this.economicFlowIngestor = null; this.economicFlowContextService = null;
        this.economicPrincipleRetriever = null;
        this.articlePresenter = null;
        this.economicFlowRetriever = null;
        this.relationValidator = openAiProperties == null || appProperties == null ? null
                : new RelationValidator(aiClient, objectMapper, openAiProperties, appProperties);
        this.economicFlowJudge = openAiProperties == null || appProperties == null ? null
                : new EconomicFlowJudge(aiClient, objectMapper, openAiProperties, appProperties);
        this.relationCandidateExtractor = appProperties == null ? null
                : new RelationCandidateExtractor(aiClient, objectMapper, appProperties);
    }

    @Override
    public AnalyzeNewsResult analyze(AnalyzeNewsRequest request) {
        if (request.articles().isEmpty()) {
            throw new AnalyzeException(ErrorCode.ANALYZE_EMPTY_INPUT);
        }

        log.info("Starting AI analysis (3-stage): articles={}, targetDate={}, maxNews={}",
                request.articles().size(), request.targetDate(), request.maxSelectedNews());

        // Stage 1: Selection
        log.info("Stage 1: Selecting important articles...");
        String selectionPrompt = com.economicbriefing.analyzer.openai.prompt.SelectionPromptBuilder.build(
                request.articles(),
                request.targetDate(),
                request.maxSelectedNews(),
                request.audience()
        );

        com.economicbriefing.analyzer.openai.dto.SelectionResponse selectionResponse = RetryExecutor.execute(
                () -> callAndParseSelection(selectionPrompt),
                appProperties.retry()
        );

        List<String> selectedIds = selectionResponse.selectedArticleIds();
        log.info("Stage 1 completed: selected {} articles", selectedIds.size());

        // Filter selected articles in order
        Map<String, com.economicbriefing.domain.article.Article> articleMap = request.articles().stream()
                .collect(Collectors.toMap(
                        com.economicbriefing.domain.article.Article::id,
                        a -> a
                ));

        List<com.economicbriefing.domain.article.Article> selectedArticles = articleBodyFetcher.enrich(selectedIds.stream()
                .map(articleMap::get)
                .filter(a -> a != null)
                .toList());

        // Stage 2: Article analysis
        log.info("Stage 2: Structuring selected article evidence...");
        String articleAnalyzerPrompt = ArticleAnalyzerPromptBuilder.build(selectedArticles);
        AnalyzerDraftBundle analyzerBundle = analyzeArticleDraftBundleWithRetry(
                articleAnalyzerPrompt, selectedArticles, appProperties.retry());
        ArticleAnalysisResponse articleAnalysis = analyzerBundle.analysis();
        String articleAnalysisJson = toJson(articleAnalysis);
        FlowBundle economicFlow = economicFlowContext(analyzerBundle, articleAnalysisJson);
        String flowClaimsJson = toJson(analyzerBundle.economicFlows().stream()
                .map(flow -> flow.flow().flowClaims()).toList());
        String explainedPathsJson = toJson(sameEvidencePaths(articleAnalysis));
        RetrievalRouterResponse routerResult = RetryExecutor.execute(
                () -> callAndParseRouter(
                        RetrievalRouterPromptBuilder.build(
                                articleAnalysisJson, flowClaimsJson, explainedPathsJson), articleAnalysis),
                appProperties.retry()
        );
        var relatedFlows = flowRequests(routerResult, economicFlow.startNodeIds());
        log.info("Retrieved related historical flow paths={}", relatedFlows.results().size());
        String economicPrincipleContextJson = economicPrincipleContext(routerResult, economicFlow.context());
        var presenterPrinciples = presenterPrinciples(articleAnalysis);
        String validationPrompt = ArticleValidatorPromptBuilder.build(
                selectedArticles, articleAnalysisJson, articleAnalysis);
        ArticleValidationResult itemValidation = validateWithRetry(
                ArticleValidatorPromptBuilder.ITEM_VALIDATION_SYSTEM_PROMPT,
                validationPrompt, selectedArticles, articleAnalysis,
                Set.of(ArticleValidationResult.FindingType.WRONG_TYPE,
                        ArticleValidationResult.FindingType.WRONG_SPEAKER,
                        ArticleValidationResult.FindingType.UNSUPPORTED,
                        ArticleValidationResult.FindingType.INACCURATE));
        ArticleValidationResult missingReview = validateWithRetry(
                ArticleValidatorPromptBuilder.MISSING_REVIEW_SYSTEM_PROMPT,
                validationPrompt, selectedArticles, articleAnalysis,
                Set.of(ArticleValidationResult.FindingType.MISSING));
        ArticleValidationResult validation = ArticleValidationMerger.merge(
                selectedArticles, articleAnalysis, itemValidation, missingReview);
        var presentations = articlePresenter == null ? List.<ArticlePresenter.PresentedArticle>of()
                : articlePresenter.present(articleAnalysis, analyzerBundle.economicFlows(), presenterPrinciples);
        log.info("Stage 2 completed: structured {} articles, validator findings={}",
                articleAnalysis.articles().size(),
                validation.articles().stream().mapToInt(a -> a.findings().size()).sum());

        // Stage 3: Final analysis
        log.info("Stage 3: Analyzing selected articles...");
        String analysisPrompt = AnalysisPromptBuilder.build(
                selectedArticles,
                request.targetDate(),
                request.maxSelectedNews(),
                request.audience(),
                articleAnalysisJson,
                economicFlow.json(),
                economicPrincipleContextJson
        );

        AiResponse aiResponse = RetryExecutor.execute(
                () -> callAndParseAnalysis(analysisPrompt),
                appProperties.retry()
        );
        aiResponse = applyPrincipleBoundary(aiResponse, articleAnalysis,
                economicPrincipleContextJson != null);

        log.info("Stage 3 completed: analyzed {} news items", aiResponse.news().size());

        Briefing briefing = BriefingBuilder.build(
                aiResponse,
                request.targetDate(),
                selectedArticles,
                request.articles().size(),
                openAiProperties.model(),
                "v4-article-analyzer",
                request.briefingTitle(),
                request.targetHour()
        );

        List<String> rejectedArticleIds = request.articles().stream()
                .map(com.economicbriefing.domain.article.Article::id)
                .filter(id -> !selectedIds.contains(id))
                .toList();

        log.info("AI analysis completed: selected={}, rejected={}",
                briefing.news().size(), rejectedArticleIds.size());

        return new AnalyzeNewsResult(
                briefing, rejectedArticleIds, List.of(), validation, articleAnalysis, routerResult,
                analyzerBundle.eventCandidates(), analyzerBundle.eventRelations(), presentations,
                openAiProperties.model(), ArticleAnalyzerPromptBuilder.PROMPT_VERSION);
    }

    private com.economicbriefing.economicflow.EconomicPrincipleRetriever.Context presenterPrinciples(
            ArticleAnalysisResponse analysis) {
        if (economicPrincipleRetriever == null) return new com.economicbriefing.economicflow.EconomicPrincipleRetriever.Context(List.of());
        var queries = articlePresenter == null ? ArticlePresenter.flowRequests(analysis).stream().map(request ->
                new com.economicbriefing.economicflow.EconomicPrincipleRetriever.Query(
                        "ANALYZER_RELATION", request.sourceReference(), request.query())).toList()
                : articlePresenter.principleQueries(analysis);
        return economicPrincipleRetriever.retrieve(queries);
    }

    private FlowBundle economicFlowContext(AnalyzerDraftBundle bundle, String currentEvent) {
        if (economicFlowIngestor == null || bundle.economicFlows().stream()
                .allMatch(item -> item.flow().flowClaims().isEmpty())) return new FlowBundle(null, null, Set.of());
        var startIds = bundle.economicFlows().stream()
                .map(item -> economicFlowIngestor.ingestFlow(item.article(), item.flow()))
                .flatMap(result -> result.resolvedNodes().stream())
                .map(com.economicbriefing.economicflow.EconomicFlowIngestor.ResolvedFlowNode::resolvedNodeId)
                .collect(java.util.stream.Collectors.toSet());
        if (startIds.isEmpty()) return new FlowBundle(null, null, Set.of());
        try {
            var context = economicFlowContextService.retrieve(currentEvent, startIds);
            return new FlowBundle(context, objectMapper.writeValueAsString(context), Set.copyOf(startIds));
        } catch (JsonProcessingException e) {
            throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR, e);
        }
    }

    private String economicPrincipleContext(
            RetrievalRouterResponse router,
            com.economicbriefing.economicflow.EconomicFlowContextService.Context flow) {
        if (economicPrincipleRetriever == null) return null;
        var queries = router.articles().stream().flatMap(article -> article.issues().stream())
                .flatMap(issue -> issue.requests().stream())
                .filter(request -> request.gapType() == RetrievalRouterResponse.GapType.WHY
                        && request.knowledgeType() == RetrievalRouterResponse.KnowledgeType.PRINCIPLE)
                .map(request -> new com.economicbriefing.economicflow.EconomicPrincipleRetriever.Query(
                        "ROUTER_WHY", request.sourceReference(), request.query()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        if (flow != null && flow.principleQuery() != null) {
            queries.add(new com.economicbriefing.economicflow.EconomicPrincipleRetriever.Query(
                    "FLOW_JUDGE", "economicFlow.principleQuery", flow.principleQuery()));
        }
        var context = economicPrincipleRetriever.retrieve(queries);
        if (context.queries().isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException e) {
            throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR, e);
        }
    }

    private com.economicbriefing.economicflow.EconomicFlowRetriever.Context flowRequests(
            RetrievalRouterResponse router, Set<Long> anchors) {
        if (economicFlowRetriever == null) return new com.economicbriefing.economicflow.EconomicFlowRetriever.Context(List.of());
        var requests = router.articles().stream().flatMap(article -> article.issues().stream())
                .flatMap(issue -> issue.requests().stream())
                .filter(request -> request.gapType() == RetrievalRouterResponse.GapType.WHY
                        && request.knowledgeType() == RetrievalRouterResponse.KnowledgeType.FLOW)
                .map(request -> new com.economicbriefing.economicflow.EconomicFlowRetriever.Request(
                        request.sourceReference(), request.query())).toList();
        return economicFlowRetriever.retrieve(requests, anchors);
    }

    private record FlowBundle(
            com.economicbriefing.economicflow.EconomicFlowContextService.Context context, String json, Set<Long> startNodeIds) {}

    static AiResponse applyPrincipleBoundary(
            AiResponse response, ArticleAnalysisResponse analysis, boolean hasPrinciples) {
        if (hasPrinciples) return response;
        Map<String, ArticleAnalysisResponse.ArticleAnalysis> byId = analysis.articles().stream()
                .collect(Collectors.toMap(ArticleAnalysisResponse.ArticleAnalysis::articleId, item -> item));
        return new AiResponse(response.overallSummary(), response.news().stream().map(news -> {
            var article = byId.get(news.id());
            String explanation = article == null ? "기사에서 확인된 관계만 제공됩니다."
                    : article.issues().stream().flatMap(issue -> issue.relations().stream())
                            .map(relation -> "기사에서는 '%s → %s' 관계가 제시됐습니다."
                                    .formatted(relation.from(), relation.to()))
                            .collect(Collectors.joining(" "));
            if (explanation.isBlank()) explanation = "기사에서 확인된 경제 메커니즘 없음";
            return new AiResponse.AiAnalyzedNews(news.id(), news.easyTitle(), news.category(), news.importance(),
                    news.threeLineSummary(), news.whatHappened(), news.whyItHappened(), explanation,
                    "기사에서 확인된 영향 없음", news.terms(), news.evidenceStatus());
        }).toList(), response.glossary());
    }

    private RetrievalRouterResponse callAndParseRouter(
            String userPrompt, ArticleAnalysisResponse baseline) {
        String content = aiClient.complete(RetrievalRouterPromptBuilder.SYSTEM_PROMPT, userPrompt, 0);
        try {
            RetrievalRouterResponse response = objectMapper.readValue(content, RetrievalRouterResponse.class);
            validateRouterResult(response, baseline);
            return response;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.error("Failed to parse or validate Retrieval Router response", e);
            throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR, e);
        }
    }

    static void validateRouterResult(
            RetrievalRouterResponse response, ArticleAnalysisResponse baseline) {
        if (response.articles() == null || response.articles().size() != baseline.articles().size()) {
            throw new IllegalArgumentException("Router article count does not match Analyzer result");
        }
        for (int articleIndex = 0; articleIndex < baseline.articles().size(); articleIndex++) {
            var sourceArticle = baseline.articles().get(articleIndex);
            var route = response.articles().get(articleIndex);
            if (route == null || !sourceArticle.articleId().equals(route.articleId())
                    || route.issues() == null || route.issues().size() != sourceArticle.issues().size()) {
                throw new IllegalArgumentException("Invalid Router article at index " + articleIndex);
            }
            for (int issueIndex = 0; issueIndex < sourceArticle.issues().size(); issueIndex++) {
                var sourceIssue = sourceArticle.issues().get(issueIndex);
                var issueRoute = route.issues().get(issueIndex);
                if (issueRoute == null || !sourceIssue.name().equals(issueRoute.issueName())
                        || issueRoute.requests() == null
                        || issueRoute.needsRetrieval() != !issueRoute.requests().isEmpty()) {
                    throw new IllegalArgumentException("Invalid Router issue at index " + issueIndex);
                }
                for (var request : issueRoute.requests()) {
                    if (request == null || request.gapType() == null || request.priority() == null
                            || isBlank(request.target()) || isBlank(request.query())
                            || isBlank(request.reason())
                            || (request.gapType() == RetrievalRouterResponse.GapType.WHY
                                    && request.knowledgeType() == null)
                            || (request.gapType() != RetrievalRouterResponse.GapType.WHY
                                    && request.knowledgeType() != null)
                            || !validSourceReference(request.sourceReference(), issueIndex, sourceIssue)) {
                        throw new IllegalArgumentException("Invalid Router request at issue " + issueIndex);
                    }
                }
            }
        }
    }

    private static boolean validSourceReference(
            String reference, int issueIndex, ArticleAnalysisResponse.Issue issue) {
        if (reference == null) return false;
        var matcher = java.util.regex.Pattern.compile(
                "issues\\[(\\d+)]\\.(mainFacts|changes|relations|statements|keyTerms)\\[(\\d+)](?:\\.([A-Za-z]+))?")
                .matcher(reference);
        if (!matcher.matches() || Integer.parseInt(matcher.group(1)) != issueIndex) return false;
        int itemIndex = Integer.parseInt(matcher.group(3));
        String collection = matcher.group(2);
        String field = matcher.group(4);
        boolean validField = switch (collection) {
            case "mainFacts", "keyTerms" -> field == null;
            case "changes" -> Set.of("target", "before", "after", "status").contains(field);
            case "relations" -> Set.of("from", "to", "relationType", "articleExplanation", "evidenceType", "speaker")
                    .contains(field);
            case "statements" -> Set.of("type", "speaker", "content").contains(field);
            default -> false;
        };
        return validField && itemIndex < switch (collection) {
            case "mainFacts" -> issue.mainFacts().size();
            case "changes" -> issue.changes().size();
            case "relations" -> issue.relations().size();
            case "statements" -> issue.statements().size();
            case "keyTerms" -> issue.keyTerms().size();
            default -> 0;
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private com.economicbriefing.analyzer.openai.dto.SelectionResponse callAndParseSelection(String userPrompt) {
        String content = aiClient.complete(
                com.economicbriefing.analyzer.openai.prompt.SelectionPromptBuilder.SYSTEM_PROMPT,
                userPrompt
        );
        return parseSelection(content);
    }

    private AiResponse callAndParseAnalysis(String userPrompt) {
        String content = aiClient.complete(SystemPromptBuilder.SYSTEM_PROMPT, userPrompt);
        return parseAndValidateAnalysis(content);
    }

    ArticleAnalysisResponse analyzeArticleDraftWithRetry(
            String userPrompt,
            List<com.economicbriefing.domain.article.Article> selectedArticles,
            AppProperties.RetryProperties retryProperties) {
        return analyzeArticleDraftBundleWithRetry(userPrompt, selectedArticles, retryProperties).analysis();
    }

    AnalyzerDraftBundle analyzeArticleDraftBundleWithRetry(
            String userPrompt,
            List<com.economicbriefing.domain.article.Article> selectedArticles,
            AppProperties.RetryProperties retryProperties) {
        AtomicReference<String> retryContext = new AtomicReference<>("");
        AnalyzerDraftBundle bundle = RetryExecutor.execute(
                () -> callAndParseArticleAnalysis(
                        userPrompt, selectedArticles, retryContext),
                retryProperties);
        if (relationCandidateExtractor != null) {
            bundle = new AnalyzerDraftBundle(
                    relationCandidateExtractor.extract(selectedArticles, bundle.analysis()),
                    bundle.eventCandidates(), bundle.eventRelations(), bundle.economicFlows());
        }
        if (relationValidator == null) return bundle;
        return economicFlowJudge.judge(relationValidator.validate(bundle));
    }

    private AnalyzerDraftBundle callAndParseArticleAnalysis(
            String userPrompt,
            List<com.economicbriefing.domain.article.Article> selectedArticles,
            AtomicReference<String> retryContext) {
        String contextualPrompt = retryContext.get().isBlank()
                ? userPrompt
                : userPrompt + "\n\n" + retryContext.get();
        String content = aiClient.completeWithSchema(
                ArticleAnalyzerPromptBuilder.SYSTEM_PROMPT,
                contextualPrompt,
                0,
                "article_analyzer_draft",
                ArticleAnalyzerPromptBuilder.schemaForArticleCount(selectedArticles.size()));
        try {
            AnalyzerDraftBundle bundle = parseDraftBundle(
                    objectMapper, content, selectedArticles);
            ArticleAnalysisResponse response = bundle.analysis();
            validateArticleAnalysis(response, selectedArticles);
            return bundle;
        } catch (AtomicityViolationException e) {
            retryContext.set(e.getMessage());
            log.warn("Article Analyzer atomicity validation failed: {}", e.getMessage());
            throw new AnalyzeException(ErrorCode.ANALYZE_ATOMICITY_ERROR, e);
        } catch (DraftIntegrityViolationException e) {
            retryContext.set(e.getMessage());
            log.warn("Article Analyzer draft integrity validation failed: {}", e.getMessage());
            throw new AnalyzeException(ErrorCode.ANALYZE_DRAFT_INTEGRITY_ERROR, e);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.error("Failed to parse or validate Article Analyzer response", e);
            throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR, e);
        }
    }

    static ArticleAnalysisResponse parseAndFlattenDraft(
            ObjectMapper objectMapper,
            String content,
            List<com.economicbriefing.domain.article.Article> sourceArticles)
            throws JsonProcessingException {
        return parseDraftBundle(objectMapper, content, sourceArticles).analysis();
    }

    static AnalyzerDraftBundle parseDraftBundle(
            ObjectMapper objectMapper,
            String content,
            List<com.economicbriefing.domain.article.Article> sourceArticles)
            throws JsonProcessingException {
        ArticleAnalyzerDraftResponse draft = objectMapper.readValue(content, ArticleAnalyzerDraftResponse.class);
        if (draft.articles() == null || draft.articles().size() != sourceArticles.size()) {
            List<String> receivedIds = draft.articles() == null ? List.of() : draft.articles().stream()
                    .map(article -> article == null ? null : article.articleId()).toList();
            throw new DraftIntegrityViolationException("""
                    Previous draft violated the article integrity contract.
                    Expected exactly %d article object(s), one for each input article.
                    Received articleIds: %s
                    Return each input articleId exactly once. Do not duplicate or omit an article.
                    """.formatted(sourceArticles.size(), receivedIds).trim());
        }
        validateFlowContract(draft);

        List<ArticleAnalysisResponse.ArticleAnalysis> articles = java.util.stream.IntStream
                .range(0, sourceArticles.size())
                .mapToObj(i -> flattenDraftArticle(draft.articles().get(i), sourceArticles.get(i)))
                .toList();
        List<EventCandidate> candidates = java.util.stream.IntStream.range(0, sourceArticles.size())
                .boxed().flatMap(i -> eventCandidates(draft.articles().get(i), sourceArticles.get(i)).stream()).toList();
        List<com.economicbriefing.economicflow.EventRelationCandidate> eventRelations =
                draft.articles().stream().filter(article -> article.eventCandidates() != null)
                        .flatMap(article -> article.issues().stream()
                        .flatMap(issue -> issue.relationCandidates().stream()
                                .flatMap(candidate -> candidate.atomicRelations().stream()
                                        .filter(ArticleAnalyzerDraftResponse.AtomicRelation::storeInEconomicFlow)
                                        .map(atomic -> new com.economicbriefing.economicflow.EventRelationCandidate(
                                                article.articleId(), atomic.fromCandidateKey(),
                                                atomic.toCandidateKey(), flowRelationType(atomic.relationType()),
                                                candidate.evidence(), atomic.evidenceType(), atomic.speaker())))))
                        .toList();
        List<com.economicbriefing.economicflow.ArticleEconomicFlow> economicFlows = new java.util.ArrayList<>();
        for (int i = 0; i < sourceArticles.size(); i++) {
            var article = draft.articles().get(i); var source = sourceArticles.get(i);
            var claims = article.flowClaims() == null ? List.<com.economicbriefing.economicflow.FlowClaimCandidate>of()
                    : article.flowClaims().stream().map(claim -> new com.economicbriefing.economicflow.FlowClaimCandidate(
                            claim.from().trim(), claim.to().trim(), flowRelationType(claim.relationType()))).toList();
            economicFlows.add(new com.economicbriefing.economicflow.ArticleEconomicFlow(
                    com.economicbriefing.economicflow.ArticleContext.from(source),
                    new com.economicbriefing.economicflow.EconomicFlowExtraction(claims)));
        }
        return new AnalyzerDraftBundle(new ArticleAnalysisResponse(articles), candidates, eventRelations,
                List.copyOf(economicFlows));
    }

    private static void validateFlowContract(ArticleAnalyzerDraftResponse draft) {
        for (var article : draft.articles()) {
            if (article.flowClaims() != null) {
                for (var claim : article.flowClaims()) {
                    if (claim == null || claim.relationType() == null || isBlank(claim.from())
                            || isBlank(claim.to()) || normalizeText(claim.from()).equals(normalizeText(claim.to()))) {
                        throw new DraftIntegrityViolationException("Invalid flowClaim endpoints");
                    }
                }
            }
            if (article.eventCandidates() == null) continue;
            var keys = article.eventCandidates().stream()
                    .map(ArticleAnalyzerDraftResponse.EventCandidateDraft::candidateKey)
                    .filter(key -> !isBlank(key)).collect(java.util.stream.Collectors.toSet());
            long keyCount = article.eventCandidates().stream()
                    .map(ArticleAnalyzerDraftResponse.EventCandidateDraft::candidateKey)
                    .filter(key -> !isBlank(key)).count();
            if (keys.size() != keyCount) {
                throw new DraftIntegrityViolationException("Duplicate eventCandidate candidateKey");
            }
            var eventsByKey = article.eventCandidates().stream().filter(event -> !isBlank(event.candidateKey()))
                    .collect(java.util.stream.Collectors.toMap(
                            ArticleAnalyzerDraftResponse.EventCandidateDraft::candidateKey, event -> event));
            var addresses = new java.util.HashMap<String, String>();
            for (var event : article.eventCandidates()) {
                if (isBlank(event.candidateKey()) || event.nodeKind() == null
                        || isBlank(event.scopeKey()) || isBlank(event.slotKey())) continue;
                String address = String.join("|", event.nodeKind().name(), event.scopeKey(), event.subjectKey(),
                        event.slotKey(), java.util.Objects.toString(event.valueKey(), ""));
                String existing = addresses.putIfAbsent(address, event.candidateKey());
                if (existing != null) {
                    throw new DraftIntegrityViolationException("""
                            Previous draft created duplicate EventCandidates for one normalized address.
                            Address: %s
                            Existing candidateKey=%s, duplicate candidateKey=%s
                            Keep one EventCandidate and reuse its candidateKey in every relation.
                            """.formatted(address, existing, event.candidateKey()).trim());
                }
            }
            for (var issue : article.issues()) for (var candidate : issue.relationCandidates()) {
                for (var atomic : candidate.atomicRelations()) {
                    boolean from = !isBlank(atomic.fromCandidateKey());
                    boolean to = !isBlank(atomic.toCandidateKey());
                    var fromEvent = eventsByKey.get(atomic.fromCandidateKey());
                    var toEvent = eventsByKey.get(atomic.toCandidateKey());
                    boolean validStored = atomic.storeInEconomicFlow() && from && to
                            && fromEvent != null && toEvent != null
                            && !atomic.fromCandidateKey().equals(atomic.toCandidateKey())
                            && normalizeText(atomic.from()).equals(normalizeText(fromEvent.title()))
                            && normalizeText(atomic.to()).equals(normalizeText(toEvent.title()));
                    boolean validAnalysisOnly = !atomic.storeInEconomicFlow() && !from && !to;
                    if (!validStored && !validAnalysisOnly) {
                        throw new DraftIntegrityViolationException("""
                                Previous draft violated the relation endpoint contract.
                                Relation: %s -> %s
                                fromCandidateKey=%s, toCandidateKey=%s
                                storeInEconomicFlow=true requires two distinct candidateKeys that exist in this article.
                                The linked Candidate titles must exactly match relation.from and relation.to.
                                storeInEconomicFlow=false requires both keys to be null.
                                """.formatted(atomic.from(), atomic.to(), atomic.fromCandidateKey(),
                                atomic.toCandidateKey()).trim());
                    }
                }
            }
        }
    }

    private static List<EventCandidate> eventCandidates(
            ArticleAnalyzerDraftResponse.DraftArticle draft,
            com.economicbriefing.domain.article.Article source) {
        if (draft.eventCandidates() == null) return List.of();
        String sourceText = normalizeText(String.join("\n",
                source.title() == null ? "" : source.title(),
                source.summary() == null ? "" : source.summary(),
                source.content() == null ? "" : source.content()));
        return draft.eventCandidates().stream().map(candidate -> {
            if (candidate == null) throw invalidEventCandidate("candidate", null);
            if (candidate.eventType() == null) throw invalidEventCandidate("eventType", candidate.title());
            if (candidate.status() == null) throw invalidEventCandidate("status", candidate.title());
            if (isBlank(candidate.title())) throw invalidEventCandidate("title", candidate.title());
            if (isBlank(candidate.subject())) throw invalidEventCandidate("subject", candidate.title());
            if (isBlank(candidate.subjectKey())) throw invalidEventCandidate("subjectKey", candidate.title());
            if (isBlank(candidate.eventDate())) throw invalidEventCandidate("eventDate", candidate.title());
            if (candidate.topicKeys() == null) throw invalidEventCandidate("topicKeys", candidate.title());
            if (candidate.topicKeys().size() > 5) {
                throw new DraftIntegrityViolationException("""
                        Previous draft assigned too many Topics to one EventCandidate.
                        Candidate: %s
                        topicKeys count: %d
                        Select only directly relevant existing Topics (normally 0-3, maximum 5).
                        Put a genuinely new reusable topic in newTopicCandidates instead.
                        """.formatted(candidate.title(), candidate.topicKeys().size()).trim());
            }
            if (candidate.newTopicCandidates() == null) {
                throw invalidEventCandidate("newTopicCandidates", candidate.title());
            }
            if (isBlank(candidate.evidenceText())
                    || !sourceText.contains(normalizeText(candidate.evidenceText()))) {
                log.warn("EventCandidate evidence is not present verbatim in source article: candidate={}, evidence={}",
                        candidate.title(), candidate.evidenceText());
            }
            boolean anyNormalized = candidate.candidateKey() != null || candidate.nodeKind() != null
                    || candidate.scopeKey() != null || candidate.slotKey() != null || candidate.valueKey() != null;
            boolean completeNormalized = !isBlank(candidate.candidateKey()) && candidate.nodeKind() != null
                    && !isBlank(candidate.scopeKey())
                    && (candidate.nodeKind() == com.economicbriefing.economicflow.NodeKind.EVENT
                            ? isBlank(candidate.valueKey()) || !isBlank(candidate.slotKey())
                            : !isBlank(candidate.slotKey()) && !isBlank(candidate.valueKey()));
            if (anyNormalized && !completeNormalized) {
                throw new DraftIntegrityViolationException("""
                        Previous draft violated the normalized node contract.
                        Candidate: %s
                        EVENT requires candidateKey, nodeKind and scopeKey; slotKey/valueKey may both be null.
                        STATE requires candidateKey, nodeKind, scopeKey, slotKey and valueKey.
                        """.formatted(candidate.title()).trim());
            }
            return new EventCandidate(source.id(), candidate.eventType(), candidate.title(), candidate.subject(),
                    candidate.subjectKey(), LocalDate.parse(candidate.eventDate()), candidate.previousState(),
                    candidate.newState(), candidate.status(), candidate.region(), candidate.topicKeys(),
                    candidate.newTopicCandidates(), candidate.evidenceText(), candidate.milestoneType(),
                    candidate.milestonePeriodValue(), candidate.milestonePeriodUnit(),
                    candidate.milestoneReferenceDate() == null ? null
                            : LocalDate.parse(candidate.milestoneReferenceDate()),
                    candidate.candidateKey(), candidate.nodeKind(), candidate.scopeKey(),
                    candidate.slotKey(), candidate.valueKey());
        }).toList();
    }

    private static DraftIntegrityViolationException invalidEventCandidate(String field, String title) {
        return new DraftIntegrityViolationException(
                "Invalid EventCandidate field '" + field + "' for candidate: " + title);
    }

    private static ArticleAnalysisResponse.ArticleAnalysis flattenDraftArticle(
            ArticleAnalyzerDraftResponse.DraftArticle draft,
            com.economicbriefing.domain.article.Article source) {
        if (draft == null || !source.id().equals(draft.articleId())
                || draft.issues() == null || draft.issues().isEmpty()) {
            throw new IllegalArgumentException("Invalid Article Analyzer draft article");
        }
        String sourceText = normalizeText(String.join("\n",
                source.title() != null ? source.title() : "",
                source.summary() != null ? source.summary() : "",
                source.content() != null ? source.content() : ""));
        List<ArticleAnalysisResponse.Issue> issues = draft.issues().stream()
                .map(issue -> flattenDraftIssue(issue, sourceText))
                .toList();
        return new ArticleAnalysisResponse.ArticleAnalysis(draft.articleId(), issues);
    }

    private static ArticleAnalysisResponse.Issue flattenDraftIssue(
            ArticleAnalyzerDraftResponse.DraftIssue issue,
            String sourceText) {
        if (issue == null || isBlank(issue.name()) || issue.mainFacts() == null
                || issue.changes() == null || issue.relationCandidates() == null
                || issue.statements() == null || issue.keyTerms() == null) {
            throw new IllegalArgumentException("Invalid Article Analyzer draft issue");
        }

        Map<AtomicRelationKey, ArticleAnalysisResponse.Relation> relations = new LinkedHashMap<>();
        for (var candidate : issue.relationCandidates()) {
            if (candidate == null || isBlank(candidate.evidence())
                    || candidate.atomicRelations() == null || candidate.atomicRelations().isEmpty()) {
                throw new IllegalArgumentException("Invalid relation candidate");
            }
            String evidence = normalizeText(candidate.evidence());
            if (!sourceText.contains(evidence)) {
                throw new DraftIntegrityViolationException("""
                        Previous draft violated the relation evidence contract.

                        Invalid evidence:
                        "%s"

                        The evidence is not present in the source article after whitespace and quote normalization.
                        Copy one complete source sentence exactly. Do not summarize or paraphrase it.
                        """.formatted(candidate.evidence()).trim());
            }
            for (var atomic : candidate.atomicRelations()) {
                if (atomic == null || isBlank(atomic.from()) || isBlank(atomic.to())
                        || atomic.relationType() == null || atomic.evidenceType() == null) {
                    throw new IllegalArgumentException("Invalid atomic relation");
                }
                validateAtomicField("from", atomic.from());
                validateAtomicField("to", atomic.to());
                AtomicRelationKey key = new AtomicRelationKey(
                        atomic.from(), atomic.to(), atomic.relationType(), atomic.evidenceType(), atomic.speaker());
                relations.putIfAbsent(key, new ArticleAnalysisResponse.Relation(
                        atomic.from(), atomic.to(), atomic.relationType(), candidate.evidence(),
                        atomic.evidenceType(), atomic.speaker()));
            }
        }

        return new ArticleAnalysisResponse.Issue(
                issue.name(), issue.mainFacts(), issue.changes(), List.copyOf(relations.values()),
                issue.statements(), issue.keyTerms());
    }

    private static void validateAtomicField(String field, String value) {
        for (String marker : List.of("에 따른", "로 인한", "때문에", "로 인해")) {
            if (value.contains(marker)) {
                throw new AtomicityViolationException("""
                        Previous draft violated the atomic relation contract.

                        Invalid %s:
                        "%s"

                        This contains an embedded causal relation (%s).
                        Split the embedded relation into separate atomic relations.
                        """.formatted(field, value, marker).trim());
            }
        }
    }

    private static String normalizeText(String value) {
        return value.replaceAll("[\\\"'“”‘’]", "").replaceAll("\\s+", " ").trim();
    }

    private record AtomicRelationKey(
            String from,
            String to,
            ArticleAnalysisResponse.RelationType relationType,
            ArticleAnalysisResponse.StatementType evidenceType,
            String speaker) {}

    private static final class AtomicityViolationException extends IllegalArgumentException {
        private AtomicityViolationException(String message) {
            super(message);
        }
    }

    private static final class DraftIntegrityViolationException extends IllegalArgumentException {
        private DraftIntegrityViolationException(String message) { super(message); }
    }

    private static com.economicbriefing.economicflow.EventRelationType flowRelationType(
            ArticleAnalysisResponse.RelationType type) {
        return switch (type) {
            case CAUSE_OR_RESULT, CLAIMED_EFFECT -> com.economicbriefing.economicflow.EventRelationType.DIRECT_CAUSE;
            case CONDITION -> com.economicbriefing.economicflow.EventRelationType.CONDITION;
            case MOTIVATION -> com.economicbriefing.economicflow.EventRelationType.MOTIVATION;
            case EXPECTED_EFFECT -> com.economicbriefing.economicflow.EventRelationType.EXPECTED_EFFECT;
            case PURPOSE -> com.economicbriefing.economicflow.EventRelationType.PURPOSE;
            default -> com.economicbriefing.economicflow.EventRelationType.RELATED_TO;
        };
    }

    private static com.economicbriefing.economicflow.EventRelationType flowRelationType(
            ArticleAnalyzerDraftResponse.FlowRelationType type) {
        return switch (type) {
            case CAUSE -> com.economicbriefing.economicflow.EventRelationType.CAUSE;
            case PURPOSE -> com.economicbriefing.economicflow.EventRelationType.PURPOSE;
            case RESPONSE -> com.economicbriefing.economicflow.EventRelationType.RESPONSE;
            case CONDITION -> com.economicbriefing.economicflow.EventRelationType.CONDITION;
        };
    }

    record AnalyzerDraftBundle(ArticleAnalysisResponse analysis, List<EventCandidate> eventCandidates,
            List<com.economicbriefing.economicflow.EventRelationCandidate> eventRelations,
            List<com.economicbriefing.economicflow.ArticleEconomicFlow> economicFlows) {}

    private static void validateArticleAnalysis(
            ArticleAnalysisResponse response,
            List<com.economicbriefing.domain.article.Article> selectedArticles) {
        if (response.articles() == null || response.articles().size() != selectedArticles.size()) {
            throw new IllegalArgumentException("Article Analyzer result count does not match selection");
        }
        for (int i = 0; i < selectedArticles.size(); i++) {
            ArticleAnalysisResponse.ArticleAnalysis analysis = response.articles().get(i);
            if (analysis == null || !selectedArticles.get(i).id().equals(analysis.articleId())
                    || analysis.issues() == null || analysis.issues().isEmpty()
                    || analysis.issues().stream().anyMatch(issue -> issue == null
                            || issue.name() == null || issue.name().isBlank()
                            || issue.mainFacts() == null || issue.changes() == null
                            || issue.relations() == null || issue.statements() == null
                            || issue.keyTerms() == null)) {
                throw new IllegalArgumentException("Invalid Article Analyzer result at index " + i);
            }
        }
    }

    private String toJson(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR, e);
        }
    }

    static List<Map<String, Object>> sameEvidencePaths(ArticleAnalysisResponse analysis) {
        var result = new ArrayList<Map<String, Object>>();
        for (var article : analysis.articles()) for (var issue : article.issues()) {
            issue.relations().stream().collect(Collectors.groupingBy(
                    ArticleAnalysisResponse.Relation::articleExplanation, LinkedHashMap::new, Collectors.toList()))
                    .values().stream().filter(relations -> relations.size() > 1).forEach(relations -> result.add(Map.of(
                            "articleId", article.articleId(), "issueName", issue.name(),
                            "evidence", relations.getFirst().articleExplanation(),
                            "path", relations.stream().map(relation -> Map.of(
                                    "from", relation.from(), "to", relation.to())).toList())));
        }
        return List.copyOf(result);
    }

    ArticleValidationResult validateWithRetry(
            String systemPrompt,
            String userPrompt,
            List<com.economicbriefing.domain.article.Article> selectedArticles,
            ArticleAnalysisResponse baseline,
            Set<ArticleValidationResult.FindingType> allowedTypes) {
        AtomicReference<String> retryContext = new AtomicReference<>("");
        return RetryExecutor.execute(() -> callAndParseValidation(systemPrompt, userPrompt,
                selectedArticles, baseline, allowedTypes, retryContext), appProperties.retry());
    }

    private ArticleValidationResult callAndParseValidation(
            String systemPrompt,
            String userPrompt,
            List<com.economicbriefing.domain.article.Article> selectedArticles,
            ArticleAnalysisResponse baseline,
            Set<ArticleValidationResult.FindingType> allowedTypes,
            AtomicReference<String> retryContext) {
        String contextualPrompt = retryContext.get().isBlank()
                ? userPrompt : userPrompt + "\n\n## Previous response error\n" + retryContext.get();
        String content = aiClient.complete(systemPrompt, contextualPrompt, 0);
        try {
            return ArticleValidationIntegrity.parseAndValidate(
                    objectMapper, content, selectedArticles, baseline, allowedTypes);
        } catch (Exception e) {
            retryContext.set(validatorRetryContext(e, baseline));
            log.error("Failed to parse or validate Article Validator response", e);
            throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATOR_ERROR, e);
        }
    }

    private static String validatorRetryContext(Exception error, ArticleAnalysisResponse baseline) {
        String allowed = baseline.articles().stream().flatMap(article ->
                java.util.stream.IntStream.range(0, article.issues().size())
                        .mapToObj(i -> article.articleId() + ": issues[" + i + "]="
                                + article.issues().get(i).name()))
                .collect(Collectors.joining("\n"));
        return """
                INVALID_VALIDATOR_RESPONSE
                Reason:
                %s
                Allowed issue references:
                %s
                Regenerate the entire response. Use only an exact allowed issue name and its matching issues[index].
                """.formatted(error.getMessage(), allowed).trim();
    }

    private com.economicbriefing.analyzer.openai.dto.SelectionResponse parseSelection(String content) {
        log.info("Parsing selection response...");
        try {
            com.economicbriefing.analyzer.openai.dto.SelectionResponse response =
                    objectMapper.readValue(content, com.economicbriefing.analyzer.openai.dto.SelectionResponse.class);
            log.info("Selection parsed successfully: {} articles selected", response.selectedArticleIds().size());
            return response;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse selection response", e);
            log.error("Full content for debugging: {}", content);
            throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR, e);
        }
    }

    private AiResponse parseAndValidateAnalysis(String content) {
        log.info("=== PARSING ANALYSIS JSON START ===");
        log.info("Content length: {} characters", content.length());
        log.info("First 500 chars: {}", content.substring(0, Math.min(500, content.length())));
        log.info("=== PARSING ANALYSIS JSON END ===");

        AiResponse response;
        try {
            response = objectMapper.readValue(content, AiResponse.class);
            log.info("=== PARSED SUCCESSFULLY ===");
            log.info("Parsed news count: {}", response.news() != null ? response.news().size() : 0);
            if (response.news() != null && !response.news().isEmpty()) {
                var firstNews = response.news().get(0);
                log.info("First news terms: {}", firstNews.terms());
            }
        } catch (JsonProcessingException e) {
            log.error("=== PARSE FAILED ===");
            log.error("Failed to parse AI response as JSON", e);
            log.error("Full content for debugging:");
            log.error(content);
            throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR, e);
        }

        // overallSummary is optional - null or empty is valid
        if (response.news() == null || response.news().isEmpty()) {
            log.error("Validation failed: news is null or empty");
            throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR);
        }

        for (int i = 0; i < response.news().size(); i++) {
            AiResponse.AiAnalyzedNews news = response.news().get(i);
            String newsPrefix = "News[" + i + "] ";

            if (news.id() == null || news.id().isBlank()) {
                log.error("{}id is null or blank", newsPrefix);
                throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR);
            }
            if (news.easyTitle() == null || news.easyTitle().isBlank()) {
                log.error("{}easyTitle is null or blank", newsPrefix);
                throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR);
            }
            if (news.threeLineSummary() == null || news.threeLineSummary().isEmpty()) {
                log.error("{}threeLineSummary is null or empty", newsPrefix);
                throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR);
            }
            if (news.whatHappened() == null || news.whatHappened().isBlank()) {
                log.error("{}whatHappened is null or blank", newsPrefix);
                throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR);
            }
        }

        return response;
    }

}

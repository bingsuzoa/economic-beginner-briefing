package com.economicbriefing.analyzer.openai;

import com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse;
import com.economicbriefing.analyzer.openai.dto.ArticlePresentationResponse;
import com.economicbriefing.analyzer.openai.prompt.ArticlePresenterPromptBuilder;
import com.economicbriefing.analyzer.openai.util.RetryExecutor;
import com.economicbriefing.classifier.entity.RelationExplanationAssetEntity;
import com.economicbriefing.classifier.repository.RelationExplanationAssetRepository;
import com.economicbriefing.config.AppProperties;
import com.economicbriefing.economicflow.ArticleEconomicFlow;
import com.economicbriefing.economicflow.EconomicPrincipleRetriever;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "briefing.dry-run", havingValue = "false")
public class ArticlePresenter {
    private static final String RESPONSE_SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"articles":{"type":"array","items":
            {"type":"object","additionalProperties":false,"properties":{"articleId":{"type":"string"},"displayTitle":{"type":"string"},"summary":{"type":"array","items":{"type":"string"}},"whatHappened":{"type":"string"},"whyExplanations":{"type":"array","items":
            {"type":"object","additionalProperties":false,"properties":{"requestId":{"type":"string"},"question":{"type":"string"},"explanation":{"type":["string","null"]},"explanationKind":{"type":["string","null"],"enum":["GENERAL_PRINCIPLE","ARTICLE_EVIDENCE",null]},"usedPrincipleChunkIds":{"type":"array","items":{"type":"string"}}},"required":["requestId","question","explanation","explanationKind","usedPrincipleChunkIds"]}}},"required":["articleId","displayTitle","summary","whatHappened","whyExplanations"]}}},"required":["articles"]}
            """;
    private final OpenAiClient client;
    private final ObjectMapper json;
    private final AppProperties app;
    private final RelationExplanationAssetRepository assets;

    @org.springframework.beans.factory.annotation.Autowired
    public ArticlePresenter(OpenAiClient client, ObjectMapper json, AppProperties app, RelationExplanationAssetRepository assets) {
        this.client = client; this.json = json; this.app = app; this.assets = assets;
    }

    public ArticlePresenter(OpenAiClient client, ObjectMapper json, AppProperties app) {
        this(client, json, app, null);
    }

    public List<PresentedArticle> present(ArticleAnalysisResponse analysis, List<ArticleEconomicFlow> flows,
            EconomicPrincipleRetriever.Context principles) {
        return presentDetailed(analysis, flows, principles).presentations();
    }

    public PresentationRun presentDetailed(ArticleAnalysisResponse analysis, List<ArticleEconomicFlow> flows,
            EconomicPrincipleRetriever.Context principles) {
        if (analysis == null || analysis.articles().isEmpty()) return new PresentationRun(List.of(), "", "", null, List.of());
        var input = input(analysis, flows, principles);
        String prompt = ArticlePresenterPromptBuilder.build(new Input(input), json);
        PresentationRun run = RetryExecutor.execute(() -> presentOnce(input, prompt), app.retry());
        saveAssets(run.presentations(), input);
        return run;
    }

    private PresentationRun presentOnce(List<InputArticle> input, String prompt) {
        String raw = client.completeWithSchema(ArticlePresenterPromptBuilder.SYSTEM_PROMPT, prompt, 0,
                "article_presentation", RESPONSE_SCHEMA);
        try {
            ArticlePresentationResponse parsed = cached(json.readValue(raw, ArticlePresentationResponse.class), input);
            var presentations = validate(parsed, input);
            return new PresentationRun(input, prompt, raw, parsed, presentations);
        } catch (Exception e) {
            throw new com.economicbriefing.exception.AnalyzeException(
                    com.economicbriefing.exception.ErrorCode.ANALYZE_VALIDATOR_ERROR, e);
        }
    }

    private List<PresentedArticle> validate(ArticlePresentationResponse response, List<InputArticle> input) {
        if (response == null || response.articles() == null || response.articles().size() != input.size())
            throw new IllegalArgumentException("Presenter must return one article per input");
        var result = new ArrayList<PresentedArticle>();
        for (int i = 0; i < input.size(); i++) {
            InputArticle source = input.get(i); var article = response.articles().get(i);
            if (article == null || !source.articleId().equals(article.articleId()))
                throw new IllegalArgumentException("Unexpected presenter articleId");
            if (blank(article.displayTitle()) || blank(article.whatHappened()) || article.summary() == null
                    || article.summary().size() < 2 || article.summary().size() > 3 || article.summary().stream().anyMatch(this::blank))
                throw new IllegalArgumentException("Presenter required fields invalid: " + source.articleId());
            var requests = source.requests().stream().collect(java.util.stream.Collectors.toMap(RequestInput::id, r -> r));
            var seen = new HashSet<String>(); var why = new ArrayList<ArticlePresentationResponse.WhyExplanation>();
            for (var item : Objects.requireNonNullElse(article.whyExplanations(), List.<ArticlePresentationResponse.WhyExplanation>of())) {
                RequestInput request = requests.get(item.requestId());
                if (request == null || !seen.add(item.requestId()) || !Objects.equals(item.question(), request.query()))
                    throw new IllegalArgumentException("Unknown or duplicate presenter WHY request");
                var ids = Objects.requireNonNullElse(item.usedPrincipleChunkIds(), List.<String>of());
                if ((item.explanation() != null && (blank(item.explanation()) || item.explanationKind() == null))
                        || (item.explanation() == null && item.explanationKind() != null)
                        || !request.chunkIds().containsAll(ids))
                    throw new IllegalArgumentException("Invalid presenter principle chunk selection");
                why.add(item);
            }
            if (why.size() != requests.size()) {
                throw new IllegalArgumentException("Presenter must return one WHY explanation per input request");
            }
            result.add(new PresentedArticle(article.articleId(), article.displayTitle(), List.copyOf(article.summary()),
                    article.whatHappened(), List.copyOf(why), source.flowClaims()));
        }
        return result;
    }

    static List<FlowRequest> flowRequests(ArticleAnalysisResponse analysis) {
        var result = new ArrayList<FlowRequest>();
        var seen = new HashSet<String>();
        for (var article : analysis.articles()) for (int issueIndex = 0; issueIndex < article.issues().size(); issueIndex++) {
            var relations = article.issues().get(issueIndex).relations();
            for (int relationIndex = 0; relationIndex < relations.size(); relationIndex++) {
                var relation = relations.get(relationIndex);
                String relationKey = normalize(relation.from()) + " | " + relation.relationType().name() + " | " + normalize(relation.to());
                if (!seen.add(article.articleId() + " | " + relationKey)) continue;
                String reference = "issues[" + issueIndex + "].relations[" + relationIndex + "].articleExplanation";
                result.add(new FlowRequest(article.articleId() + ":" + result.size(), article.articleId(), reference,
                        "‘" + relation.from() + " → " + relation.to() + "’는 왜 이어졌나요?", relation.from(), relation.to(),
                        relation.relationType().name(), relation.articleExplanation(), relationKey));
            }
        }
        return List.copyOf(result);
    }

    List<EconomicPrincipleRetriever.Query> principleQueries(ArticleAnalysisResponse analysis) {
        return flowRequests(analysis).stream().filter(request -> asset(request) == null).map(request -> new EconomicPrincipleRetriever.Query(
                "ANALYZER_RELATION", request.sourceReference(), request.query())).toList();
    }

    private List<InputArticle> input(ArticleAnalysisResponse analysis, List<ArticleEconomicFlow> flows,
            EconomicPrincipleRetriever.Context principles) {
        var flowById = new HashMap<String, List<?>>();
        if (flows != null) flows.forEach(flow -> flowById.put(flow.article().articleId(), flow.flow().flowClaims()));
        var resultByReference = new HashMap<String, List<EconomicPrincipleRetriever.Chunk>>();
        if (principles != null) principles.queries().forEach(query -> resultByReference.merge(
                query.request().sourceReference(), query.results(), (left, right) -> {
                    var merged = new ArrayList<EconomicPrincipleRetriever.Chunk>(left); merged.addAll(right);
                    return merged.stream().collect(java.util.stream.Collectors.toMap(EconomicPrincipleRetriever.Chunk::chunkId,
                            item -> item, (first, ignored) -> first, LinkedHashMap::new)).values().stream().toList();
                }));
        var result = new ArrayList<InputArticle>();
        var requestsByArticle = flowRequests(analysis).stream().collect(java.util.stream.Collectors.groupingBy(
                FlowRequest::articleId, LinkedHashMap::new, java.util.stream.Collectors.toList()));
        for (var article : analysis.articles()) {
            var requests = new ArrayList<RequestInput>();
            for (var request : requestsByArticle.getOrDefault(article.articleId(), List.of())) {
                var cached = asset(request);
                var chunks = cached == null ? resultByReference.getOrDefault(request.sourceReference(), List.of()).stream().limit(1).toList() : List.<EconomicPrincipleRetriever.Chunk>of();
                requests.add(new RequestInput(request.id(), request.articleId(), request.sourceReference(), request.from(), request.to(),
                        request.relationType(), request.evidence(), request.relationKey(), "WHY", request.query(), "기사 관계 설명",
                        chunks.stream().map(EconomicPrincipleRetriever.Chunk::chunkId).collect(java.util.stream.Collectors.toSet()), chunks, cached));
            }
            result.add(new InputArticle(article.articleId(), article.issues(), flowById.getOrDefault(article.articleId(), List.of()), requests));
        }
        return result;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private static String normalize(String value) { return Objects.requireNonNullElse(value, "").replaceAll("\\s+", " ").trim(); }
    private CachedAsset asset(FlowRequest request) {
        if (assets == null) return null;
        return assets.findFirstByRelationKeyAndExplanationKindOrderByIdDesc(request.relationKey(),
                ArticlePresentationResponse.ExplanationKind.GENERAL_PRINCIPLE.name())
                .map(entity -> new CachedAsset(entity.getExplanation(), ArticlePresentationResponse.ExplanationKind.GENERAL_PRINCIPLE,
                        readIds(entity.getPrincipleChunkIds()))).orElse(null);
    }

    private ArticlePresentationResponse cached(ArticlePresentationResponse response, List<InputArticle> input) {
        if (response == null || response.articles() == null) return response;
        var articles = new ArrayList<ArticlePresentationResponse.ArticlePresentation>();
        for (int i = 0; i < response.articles().size() && i < input.size(); i++) {
            var article = response.articles().get(i); var answers = new LinkedHashMap<String, ArticlePresentationResponse.WhyExplanation>();
            for (var answer : Objects.requireNonNullElse(article.whyExplanations(), List.<ArticlePresentationResponse.WhyExplanation>of())) answers.put(answer.requestId(), answer);
            for (var request : input.get(i).requests()) if (request.cached() != null) answers.put(request.id(),
                    new ArticlePresentationResponse.WhyExplanation(request.id(), request.query(), request.cached().explanation(),
                            request.cached().kind(), request.cached().chunkIds()));
            articles.add(new ArticlePresentationResponse.ArticlePresentation(article.articleId(), article.displayTitle(), article.summary(),
                    article.whatHappened(), List.copyOf(answers.values())));
        }
        return new ArticlePresentationResponse(List.copyOf(articles));
    }

    private void saveAssets(List<PresentedArticle> presentations, List<InputArticle> input) {
        if (assets == null) return;
        for (int i = 0; i < presentations.size(); i++) {
            var requests = input.get(i).requests().stream().collect(java.util.stream.Collectors.toMap(RequestInput::id, item -> item));
            for (var answer : presentations.get(i).whyExplanations()) {
                var request = requests.get(answer.requestId());
                if (request == null || request.cached() != null || answer.explanation() == null
                        || assets.existsByRelationKeyAndExplanationKindAndSourceArticleId(request.relationKey(),
                                answer.explanationKind().name(), request.articleId())) continue;
                var entity = new RelationExplanationAssetEntity();
                entity.setRelationKey(request.relationKey()); entity.setFrom(request.from()); entity.setTo(request.to());
                entity.setRelationType(request.relationType()); entity.setExplanation(answer.explanation());
                entity.setExplanationKind(answer.explanationKind().name()); entity.setSourceArticleId(request.articleId());
                entity.setSourceReference(request.sourceReference()); entity.setSourceEvidence(request.evidence());
                try { entity.setPrincipleChunkIds(json.writeValueAsString(answer.usedPrincipleChunkIds())); }
                catch (Exception ignored) { entity.setPrincipleChunkIds("[]"); }
                entity.setPromptVersion(ArticlePresenterPromptBuilder.PROMPT_VERSION); assets.save(entity);
            }
        }
    }

    private List<String> readIds(String value) {
        try { return json.readValue(Objects.requireNonNullElse(value, "[]"), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}); }
        catch (Exception ignored) { return List.of(); }
    }
    public record PresentedArticle(String articleId, String displayTitle, List<String> summary, String whatHappened,
            List<ArticlePresentationResponse.WhyExplanation> whyExplanations, List<?> flowClaims) {}
    public record PresentationRun(List<InputArticle> input, String prompt, String raw,
                                  ArticlePresentationResponse parsed, List<PresentedArticle> presentations) {}
    record Input(List<InputArticle> articles) {}
    record InputArticle(String articleId, List<ArticleAnalysisResponse.Issue> issues, List<?> flowClaims, List<RequestInput> requests) {}
    record RequestInput(String id, String articleId, String sourceReference, String from, String to, String relationType,
                        String evidence, String relationKey, String gapType, String query, String reason, Set<String> chunkIds,
                        List<EconomicPrincipleRetriever.Chunk> candidates, CachedAsset cached) {}
    record FlowRequest(String id, String articleId, String sourceReference, String query, String from, String to,
                       String relationType, String evidence, String relationKey) {}
    record CachedAsset(String explanation, ArticlePresentationResponse.ExplanationKind kind, List<String> chunkIds) {}
}

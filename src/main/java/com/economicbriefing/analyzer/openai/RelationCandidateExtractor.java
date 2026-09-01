package com.economicbriefing.analyzer.openai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse;
import com.economicbriefing.analyzer.openai.prompt.RelationCandidateExtractorPromptBuilder;
import com.economicbriefing.analyzer.openai.util.RetryExecutor;
import com.economicbriefing.config.AppProperties;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.exception.AnalyzeException;
import com.economicbriefing.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RelationCandidateExtractor {
    private static final Logger log = LoggerFactory.getLogger(RelationCandidateExtractor.class);
    private final OpenAiClient client;
    private final ObjectMapper json;
    private final AppProperties.RetryProperties retry;

    RelationCandidateExtractor(OpenAiClient client, ObjectMapper json, AppProperties app) {
        this.client = client;
        this.json = json;
        this.retry = app.retry();
    }

    ArticleAnalysisResponse extract(List<Article> sources, ArticleAnalysisResponse analysis) {
        ArticleAnalysisResponse first = extractPass(
                RelationCandidateExtractorPromptBuilder.build(sources, analysis), sources, analysis, false);
        return extractPass(RelationCandidateExtractorPromptBuilder.buildCoverage(sources, first),
                sources, first, true);
    }

    private ArticleAnalysisResponse extractPass(String prompt, List<Article> sources,
            ArticleAnalysisResponse analysis, boolean preserveExisting) {
        AtomicReference<String> retryContext = new AtomicReference<>("");
        AtomicReference<Response> lastParsed = new AtomicReference<>();
        try {
            return RetryExecutor.execute(
                    () -> call(prompt, sources, analysis, retryContext, lastParsed, preserveExisting), retry);
        } catch (AnalyzeException e) {
            if (lastParsed.get() == null) throw e;
            return merge(lastParsed.get(), sources, analysis, true, preserveExisting);
        }
    }

    private ArticleAnalysisResponse call(String prompt, List<Article> sources, ArticleAnalysisResponse analysis,
            AtomicReference<String> retryContext, AtomicReference<Response> lastParsed, boolean preserveExisting) {
        String raw = client.completeWithSchema(RelationCandidateExtractorPromptBuilder.SYSTEM_PROMPT,
                prompt + retryContext.get(), 0,
                "relation_candidates", RelationCandidateExtractorPromptBuilder.schemaForArticleCount(sources.size()));
        try {
            Response response = json.readValue(raw, Response.class);
            lastParsed.set(response);
            return merge(response, sources, analysis, false, preserveExisting);
        } catch (IllegalArgumentException e) {
            retryContext.set("\n\nPrevious relation candidate response violated the contract:\n" + e.getMessage()
                    + "\nReturn corrected candidates using exact source evidence.");
            throw new AnalyzeException(ErrorCode.ANALYZE_DRAFT_INTEGRITY_ERROR, e);
        } catch (Exception e) {
            throw new AnalyzeException(ErrorCode.ANALYZE_VALIDATION_ERROR, e);
        }
    }

    static ArticleAnalysisResponse merge(Response response, List<Article> sources, ArticleAnalysisResponse analysis) {
        return merge(response, sources, analysis, false);
    }

    static ArticleAnalysisResponse mergeDroppingInvalidEvidence(Response response, List<Article> sources,
            ArticleAnalysisResponse analysis) {
        return merge(response, sources, analysis, true);
    }

    private static ArticleAnalysisResponse merge(Response response, List<Article> sources,
            ArticleAnalysisResponse analysis, boolean dropInvalidEvidence) {
        return merge(response, sources, analysis, dropInvalidEvidence, false);
    }

    private static ArticleAnalysisResponse merge(Response response, List<Article> sources,
            ArticleAnalysisResponse analysis, boolean dropInvalidEvidence, boolean preserveExisting) {
        if (response.articles() == null || response.articles().size() != sources.size())
            throw new IllegalArgumentException("Expected exactly one relation article per source article");
        var result = new ArrayList<ArticleAnalysisResponse.ArticleAnalysis>();
        int candidateCount = 0;
        int acceptedCount = 0;
        for (int i = 0; i < sources.size(); i++) {
            var extracted = response.articles().get(i);
            var baseline = analysis.articles().get(i);
            if (!sources.get(i).id().equals(extracted.articleId()))
                throw new IllegalArgumentException("Unexpected articleId: " + extracted.articleId());
            String sourceText = normalize(sources.get(i).title() + "\n" + sources.get(i).summary() + "\n"
                    + sources.get(i).content());
            var byIssue = new LinkedHashMap<String, LinkedHashMap<Key, ArticleAnalysisResponse.Relation>>();
            baseline.issues().forEach(issue -> {
                var relations = new LinkedHashMap<Key, ArticleAnalysisResponse.Relation>();
                if (preserveExisting) for (var relation : issue.relations()) relations.put(new Key(relation.from(),
                        relation.to(), relation.relationType(), relation.evidenceType(), relation.speaker()), relation);
                byIssue.put(issue.name(), relations);
            });
            if (extracted.relationCandidates() == null)
                throw new IllegalArgumentException("relationCandidates must not be null");
            for (int candidateIndex = 0; candidateIndex < extracted.relationCandidates().size(); candidateIndex++) {
                var candidate = extracted.relationCandidates().get(candidateIndex);
                var target = byIssue.get(candidate.issueName());
                if (target == null) throw new IllegalArgumentException("Unknown issueName: " + candidate.issueName());
                if (candidate.atomicRelations() == null || candidate.atomicRelations().isEmpty())
                    throw new IllegalArgumentException("atomicRelations must not be empty");
                candidateCount += candidate.atomicRelations().size();
                if (candidate.evidence() == null || !sourceText.contains(normalize(candidate.evidence()))) {
                    if (dropInvalidEvidence) {
                        for (var relation : candidate.atomicRelations()) log.warn(
                                "Relation candidate dropped because evidence was not found in source article: articleId={}, relationIndex={}, from={}, to={}",
                                extracted.articleId(), candidateIndex, relation.from(), relation.to());
                        continue;
                    }
                    throw new IllegalArgumentException("Evidence not found in source: " + candidate.evidence());
                }
                for (var relation : candidate.atomicRelations()) {
                    validateEndpoint("from", relation.from()); validateEndpoint("to", relation.to());
                    var key = new Key(relation.from(), relation.to(), relation.relationType(),
                            relation.evidenceType(), relation.speaker());
                    target.putIfAbsent(key, new ArticleAnalysisResponse.Relation(relation.from(), relation.to(),
                            relation.relationType(), candidate.evidence(), relation.evidenceType(), relation.speaker()));
                    acceptedCount++;
                }
            }
            result.add(new ArticleAnalysisResponse.ArticleAnalysis(baseline.articleId(), baseline.issues().stream()
                    .map(issue -> new ArticleAnalysisResponse.Issue(issue.name(), issue.mainFacts(), issue.changes(),
                            List.copyOf(byIssue.get(issue.name()).values()), issue.statements(), issue.keyTerms())).toList()));
        }
        if (candidateCount > 0 && acceptedCount == 0)
            throw new IllegalArgumentException("All relation evidence failed source integrity validation");
        return new ArticleAnalysisResponse(result);
    }

    private static void validateEndpoint(String field, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        for (String marker : List.of("에 따른", "로 인한", "때문에", "로 인해"))
            if (value.contains(marker)) throw new IllegalArgumentException(
                    "Invalid " + field + " contains embedded causal phrase: " + value);
    }

    private static String normalize(String value) {
        return String.valueOf(value).replaceAll("[\\\"'“”‘’]", "").replaceAll("\\s+", " ").trim();
    }

    record Response(List<RelationArticle> articles) {}
    record RelationArticle(String articleId, List<Candidate> relationCandidates) {}
    record Candidate(String issueName, String evidence, List<AtomicRelation> atomicRelations) {}
    record AtomicRelation(String from, String to, ArticleAnalysisResponse.RelationType relationType,
            ArticleAnalysisResponse.StatementType evidenceType, String speaker) {}
    private record Key(String from, String to, ArticleAnalysisResponse.RelationType relationType,
            ArticleAnalysisResponse.StatementType evidenceType, String speaker) {}
}

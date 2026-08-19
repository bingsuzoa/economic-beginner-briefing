package com.economicbriefing.analyzer.openai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.economicbriefing.analyzer.dto.ArticleValidationResult;
import com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse;
import com.economicbriefing.domain.article.Article;

final class ArticleValidationMerger {

    private ArticleValidationMerger() {}

    static ArticleValidationResult merge(
            List<Article> sourceArticles,
            ArticleAnalysisResponse baseline,
            ArticleValidationResult itemValidation,
            ArticleValidationResult missingReview) {
        return new ArticleValidationResult(java.util.stream.IntStream
                .range(0, itemValidation.articles().size())
                .mapToObj(i -> {
                    var itemArticle = itemValidation.articles().get(i);
                    Set<String> existing = existingText(baseline.articles().get(i));
                    String sourceText = String.join(" ",
                            sourceArticles.get(i).title(),
                            sourceArticles.get(i).summary() != null ? sourceArticles.get(i).summary() : "",
                            sourceArticles.get(i).content() != null ? sourceArticles.get(i).content() : "");
                    List<ArticleValidationResult.Finding> findings = new ArrayList<>();
                    itemArticle.findings().stream()
                            .filter(finding -> isSelfConsistent(finding, sourceText))
                            .forEach(findings::add);
                    missingReview.articles().get(i).findings().stream()
                            .filter(finding -> !existing.contains(normalize(finding.description())))
                            .filter(finding -> !isRepeatedSeriesDetail(
                                    finding, baseline.articles().get(i)))
                            .forEach(findings::add);
                    return new ArticleValidationResult.ArticleValidation(
                            itemArticle.articleId(), List.copyOf(findings));
                })
                .toList());
    }

    private static Set<String> existingText(ArticleAnalysisResponse.ArticleAnalysis article) {
        Set<String> existing = new HashSet<>();
        article.issues().forEach(issue -> {
            issue.mainFacts().forEach(value -> existing.add(normalize(value)));
            issue.statements().forEach(value -> existing.add(normalize(value.content())));
        });
        return existing;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static boolean isSelfConsistent(ArticleValidationResult.Finding finding, String sourceText) {
        if (finding.type() == ArticleValidationResult.FindingType.UNSUPPORTED) {
            if (finding.currentValue() == null
                    && (finding.targetReference() == null || finding.targetReference().isBlank())) {
                return false;
            }
            if (finding.currentValue() != null && finding.currentValue().isTextual()
                    && meaningOverlap(finding.currentValue().asText(), sourceText) >= 0.75) {
                return false;
            }
        }
        if ((finding.type() == ArticleValidationResult.FindingType.WRONG_TYPE
                || finding.type() == ArticleValidationResult.FindingType.WRONG_SPEAKER
                || finding.type() == ArticleValidationResult.FindingType.INACCURATE)
                && finding.currentValue() != null && finding.suggestedValue() != null) {
            if (finding.currentValue().equals(finding.suggestedValue())) return false;
            if (!finding.currentValue().isTextual() || !finding.suggestedValue().isTextual()) return true;
            String current = finding.currentValue().asText();
            String suggested = finding.suggestedValue().asText();
            Set<String> relationTypes = Set.of(
                    "CAUSE_OR_RESULT", "PURPOSE", "CHANGE", "COMPARISON", "CONDITION",
                    "ASSOCIATION", "CLAIMED_EFFECT", "EXPECTED_EFFECT", "NEXT_STEP", "EXPECTED_PROCESS");
            Set<String> evidenceTypes = Set.of(
                    "FACT", "CLAIM", "INTERPRETATION", "PREDICTION", "PROPOSAL", "PLAN");
            Set<String> changeStatuses = Set.of("CONFIRMED", "PROPOSED", "EXPECTED");
            String reference = finding.targetReference() != null ? finding.targetReference() : "";
            if (reference.endsWith(".relationType")) {
                return relationTypes.contains(current) && relationTypes.contains(suggested);
            }
            if (reference.endsWith(".evidenceType") || reference.matches(".*\\.statements\\[\\d+].type$")) {
                return evidenceTypes.contains(current) && evidenceTypes.contains(suggested)
                        && statementTypeMatchesDescription(finding.description(), current, suggested);
            }
            if (reference.endsWith(".status")) {
                return changeStatuses.contains(current) && changeStatuses.contains(suggested);
            }
            if (relationTypes.contains(current) || relationTypes.contains(suggested)) {
                return relationTypes.contains(current) && relationTypes.contains(suggested);
            }
            if (evidenceTypes.contains(current) || evidenceTypes.contains(suggested)) {
                return evidenceTypes.contains(current) && evidenceTypes.contains(suggested);
            }
            if (changeStatuses.contains(current) || changeStatuses.contains(suggested)) {
                return changeStatuses.contains(current) && changeStatuses.contains(suggested);
            }
        }
        return true;
    }

    private static boolean statementTypeMatchesDescription(String description, String current, String suggested) {
        String described = describedStatementType(description);
        return described == null || described.equals(suggested) && !described.equals(current);
    }

    private static String describedStatementType(String description) {
        // ponytail: only catches explicit self-contradictory wording; add an LLM judge if this needs broad NLU.
        if (description.contains("해석된 것이 아니라 사실")
                || description.contains("사실로 제시")
                || description.contains("사실로 보도")
                || description.contains("사실로 서술")
                || description.contains("확정 사실")
                || description.contains("기사에서 사실")) {
            return "FACT";
        }
        if (description.contains("기사 해석")
                || description.contains("해석이므로")
                || description.contains("풀이된다")
                || description.contains("풀이되므로")) {
            return "INTERPRETATION";
        }
        return null;
    }

    private static double meaningOverlap(String candidate, String source) {
        Set<String> words = words(candidate);
        if (words.size() < 2) return 0;
        Set<String> sourceWords = words(source);
        long matched = words.stream().filter(sourceWords::contains).count();
        // ponytail: lexical Korean normalization only; replace with semantic entailment if measured precision needs it.
        return (double) matched / words.size();
    }

    private static boolean isRepeatedSeriesDetail(
            ArticleValidationResult.Finding finding,
            ArticleAnalysisResponse.ArticleAnalysis article) {
        if (finding.type() != ArticleValidationResult.FindingType.MISSING
                || finding.targetType() != ArticleValidationResult.TargetType.MAIN_FACT
                || finding.evidence() == null
                || finding.evidence().matches(".*(특례|예외|한도|요건|기준|이상|이하|최고|최저|최대|최소).*")) {
            return false;
        }
        Set<String> units = measurementUnits(finding.evidence() + " " + finding.description() + " "
                + (finding.suggestedValue() != null ? finding.suggestedValue().toString() : ""));
        if (units.isEmpty()) return false;
        var issue = article.issues().stream()
                .filter(value -> value.name().equals(finding.issue()))
                .findFirst()
                .orElse(null);
        if (issue == null) return false;
        // ponytail: repeated-unit heuristic; replace with a learned importance score if mixed-series errors appear.
        return issue.mainFacts().stream()
                .filter(fact -> measurementUnits(fact).stream().anyMatch(units::contains))
                .count() >= 3;
    }

    private static Set<String> measurementUnits(String value) {
        Set<String> units = new HashSet<>();
        var matcher = java.util.regex.Pattern.compile("\\d(?:[.,]\\d+)?\\s*([A-Za-z%]+)").matcher(value);
        while (matcher.find()) units.add(matcher.group(1).toLowerCase());
        return units;
    }

    private static Set<String> words(String value) {
        Set<String> words = new HashSet<>();
        for (String word : value.replaceAll("[^가-힣A-Za-z0-9%]+", " ").split("\\s+")) {
            String normalized = word.replaceFirst("(하도록|으로|에서|에게|까지|부터|[이가은는을를])$", "");
            if (normalized.length() >= 2) words.add(normalized);
        }
        return words;
    }
}

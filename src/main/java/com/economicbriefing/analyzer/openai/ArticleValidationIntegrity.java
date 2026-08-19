package com.economicbriefing.analyzer.openai;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.economicbriefing.analyzer.dto.ArticleValidationResult;
import com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse;
import com.economicbriefing.domain.article.Article;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class ArticleValidationIntegrity {

    private static final Set<String> ROOT_FIELDS = Set.of("articles");
    private static final Set<String> ARTICLE_FIELDS = Set.of("articleId", "findings");
    private static final Set<String> FINDING_FIELDS = Set.of(
            "type", "issue", "targetType", "targetReference", "description",
            "currentValue", "suggestedValue", "evidence");
    private static final Pattern REFERENCE = Pattern.compile(
            "issues\\[(\\d+)](?:\\.(mainFacts|changes|relations|statements|keyTerms)\\[(\\d+)])?(?:\\.([A-Za-z]+))?");

    private ArticleValidationIntegrity() {}

    static ArticleValidationResult parseAndValidate(
            ObjectMapper mapper,
            String content,
            List<Article> sourceArticles,
            ArticleAnalysisResponse baseline,
            Set<ArticleValidationResult.FindingType> allowedTypes) throws Exception {
        JsonNode root;
        try (JsonParser parser = mapper.createParser(content)) {
            parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            root = mapper.readTree(parser);
        }
        requireObjectFields(root, ROOT_FIELDS, "root");
        requireArray(root.get("articles"), "articles");
        for (int i = 0; i < root.get("articles").size(); i++) {
            JsonNode article = root.get("articles").get(i);
            requireObjectFields(article, ARTICLE_FIELDS, "articles[" + i + "]");
            requireArray(article.get("findings"), "articles[" + i + "].findings");
            for (int j = 0; j < article.get("findings").size(); j++) {
                requireObjectFields(article.get("findings").get(j), FINDING_FIELDS,
                        "articles[" + i + "].findings[" + j + "]");
            }
        }

        ArticleValidationResult result = mapper.treeToValue(root, ArticleValidationResult.class);
        if (result.articles() == null || result.articles().size() != sourceArticles.size()
                || baseline.articles().size() != sourceArticles.size()) {
            throw new IllegalArgumentException("Article Validator result count does not match baseline");
        }

        var validated = java.util.stream.IntStream.range(0, sourceArticles.size())
                .mapToObj(i -> validateArticle(result.articles().get(i), sourceArticles.get(i),
                        baseline.articles().get(i), allowedTypes, mapper))
                .toList();
        return new ArticleValidationResult(validated);
    }

    private static ArticleValidationResult.ArticleValidation validateArticle(
            ArticleValidationResult.ArticleValidation validation,
            Article source,
            ArticleAnalysisResponse.ArticleAnalysis baseline,
            Set<ArticleValidationResult.FindingType> allowedTypes,
            ObjectMapper mapper) {
        if (validation == null || !source.id().equals(validation.articleId())
                || !source.id().equals(baseline.articleId()) || validation.findings() == null) {
            throw new IllegalArgumentException("Invalid Article Validator article");
        }
        LinkedHashSet<ArticleValidationResult.Finding> unique = new LinkedHashSet<>();
        for (var finding : validation.findings()) {
            if (validateFinding(finding, baseline, allowedTypes, mapper)) {
                unique.add(finding);
            }
        }
        return new ArticleValidationResult.ArticleValidation(validation.articleId(), List.copyOf(unique));
    }

    private static boolean validateFinding(
            ArticleValidationResult.Finding finding,
            ArticleAnalysisResponse.ArticleAnalysis baseline,
            Set<ArticleValidationResult.FindingType> allowedTypes,
            ObjectMapper mapper) {
        if (finding == null || finding.type() == null || !allowedTypes.contains(finding.type())
                || finding.targetType() == null || blank(finding.issue()) || blank(finding.description())) {
            throw new IllegalArgumentException("Invalid Article Validator finding");
        }
        if (finding.type() == ArticleValidationResult.FindingType.MISSING) {
            if (finding.targetReference() != null || !isNullish(finding.currentValue())
                    || isNullish(finding.suggestedValue()) || blank(finding.evidence())) {
                throw new IllegalArgumentException("Invalid MISSING finding shape");
            }
            if (baseline.issues().stream().noneMatch(issue -> issue.name().equals(finding.issue()))) {
                throw new IllegalArgumentException("MISSING finding references unknown issue");
            }
            return true;
        }
        if (blank(finding.targetReference()) || blank(finding.evidence())
                && finding.type() != ArticleValidationResult.FindingType.UNSUPPORTED) {
            throw new IllegalArgumentException("Invalid item finding shape");
        }
        if (finding.type() == ArticleValidationResult.FindingType.UNSUPPORTED) {
            if (!isNullish(finding.suggestedValue()) || finding.evidence() != null) {
                throw new IllegalArgumentException("Invalid UNSUPPORTED finding shape");
            }
        } else if (isNullish(finding.suggestedValue())) {
            throw new IllegalArgumentException("Item finding requires suggestedValue");
        }

        ResolvedReference resolved = resolve(baseline, finding.targetReference(), mapper);
        if (resolved.targetType() != finding.targetType() || !resolved.issue().equals(finding.issue())) {
            throw new IllegalArgumentException("Finding target does not match targetReference");
        }
        JsonNode current = !isNullish(finding.currentValue()) ? finding.currentValue() : mapper.nullNode();
        if (!resolved.value().equals(current)) {
            throw new IllegalArgumentException("Finding currentValue does not match baseline");
        }
        validateSuggestedAxis(finding, resolved.field());
        return isSelfConsistent(finding, resolved.field());
    }

    private static boolean isSelfConsistent(ArticleValidationResult.Finding finding, String field) {
        if (finding.type() == ArticleValidationResult.FindingType.UNSUPPORTED
                || isNullish(finding.suggestedValue())) {
            return true;
        }
        if (finding.currentValue().equals(finding.suggestedValue())) return false;
        if (!("evidenceType".equals(field) || "type".equals(field))
                || !finding.currentValue().isTextual() || !finding.suggestedValue().isTextual()) {
            return true;
        }
        String described = describedStatementType(finding.description());
        if (described == null) return true;
        return described.equals(finding.suggestedValue().asText())
                && !described.equals(finding.currentValue().asText());
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

    private static ResolvedReference resolve(
            ArticleAnalysisResponse.ArticleAnalysis article, String reference, ObjectMapper mapper) {
        var match = REFERENCE.matcher(reference);
        if (!match.matches()) throw new IllegalArgumentException("Invalid targetReference");
        int issueIndex = Integer.parseInt(match.group(1));
        if (issueIndex >= article.issues().size()) throw new IllegalArgumentException("Invalid issue index");
        var issue = article.issues().get(issueIndex);
        String collection = match.group(2);
        String field = match.group(4);
        if (collection == null) {
            if (field != null && !field.equals("name")) throw new IllegalArgumentException("Invalid issue field");
            return new ResolvedReference(issue.name(), ArticleValidationResult.TargetType.ISSUE,
                    mapper.valueToTree(field == null ? issue : issue.name()), field);
        }
        int index = Integer.parseInt(match.group(3));
        return switch (collection) {
            case "mainFacts" -> scalar(issue.name(), issue.mainFacts(), index, field,
                    ArticleValidationResult.TargetType.MAIN_FACT, mapper);
            case "keyTerms" -> scalar(issue.name(), issue.keyTerms(), index, field,
                    ArticleValidationResult.TargetType.KEY_TERM, mapper);
            case "changes" -> change(issue.name(), issue.changes(), index, field, mapper);
            case "relations" -> relation(issue.name(), issue.relations(), index, field, mapper);
            case "statements" -> statement(issue.name(), issue.statements(), index, field, mapper);
            default -> throw new IllegalArgumentException("Invalid targetReference collection");
        };
    }

    private static ResolvedReference scalar(String issue, List<String> values, int index, String field,
            ArticleValidationResult.TargetType type, ObjectMapper mapper) {
        if (index >= values.size() || field != null) throw new IllegalArgumentException("Invalid scalar reference");
        return new ResolvedReference(issue, type, mapper.valueToTree(values.get(index)), null);
    }

    private static ResolvedReference change(String issue, List<ArticleAnalysisResponse.Change> values,
            int index, String field, ObjectMapper mapper) {
        if (index >= values.size()) throw new IllegalArgumentException("Invalid change index");
        var value = values.get(index);
        Object resolved = switch (field == null ? "" : field) {
            case "" -> value;
            case "target" -> value.target();
            case "before" -> value.before();
            case "after" -> value.after();
            case "status" -> value.status();
            default -> throw new IllegalArgumentException("Invalid change field");
        };
        return new ResolvedReference(issue, ArticleValidationResult.TargetType.CHANGE,
                mapper.valueToTree(resolved), field);
    }

    private static ResolvedReference relation(String issue, List<ArticleAnalysisResponse.Relation> values,
            int index, String field, ObjectMapper mapper) {
        if (index >= values.size()) throw new IllegalArgumentException("Invalid relation index");
        var value = values.get(index);
        Object resolved = switch (field == null ? "" : field) {
            case "" -> value;
            case "from" -> value.from();
            case "to" -> value.to();
            case "relationType" -> value.relationType();
            case "articleExplanation" -> value.articleExplanation();
            case "evidenceType" -> value.evidenceType();
            case "speaker" -> value.speaker();
            default -> throw new IllegalArgumentException("Invalid relation field");
        };
        var type = "articleExplanation".equals(field)
                ? ArticleValidationResult.TargetType.ARTICLE_EXPLANATION
                : ArticleValidationResult.TargetType.RELATION;
        return new ResolvedReference(issue, type, mapper.valueToTree(resolved), field);
    }

    private static ResolvedReference statement(String issue, List<ArticleAnalysisResponse.Statement> values,
            int index, String field, ObjectMapper mapper) {
        if (index >= values.size()) throw new IllegalArgumentException("Invalid statement index");
        var value = values.get(index);
        Object resolved = switch (field == null ? "" : field) {
            case "" -> value;
            case "type" -> value.type();
            case "speaker" -> value.speaker();
            case "content" -> value.content();
            default -> throw new IllegalArgumentException("Invalid statement field");
        };
        return new ResolvedReference(issue, ArticleValidationResult.TargetType.STATEMENT,
                mapper.valueToTree(resolved), field);
    }

    private static void validateSuggestedAxis(ArticleValidationResult.Finding finding, String field) {
        boolean enumField = "relationType".equals(field) || "evidenceType".equals(field)
                || "type".equals(field) || "status".equals(field);
        if (!enumField) return;
        if (isNullish(finding.suggestedValue()) || !finding.suggestedValue().isTextual()) {
            throw new IllegalArgumentException("suggestedValue must be a target field enum string");
        }
        String value = finding.suggestedValue().asText();
        try {
            if ("relationType".equals(field)) ArticleAnalysisResponse.RelationType.valueOf(value);
            if ("evidenceType".equals(field) || "type".equals(field)) {
                ArticleAnalysisResponse.StatementType.valueOf(value);
            }
            if ("status".equals(field)) ArticleAnalysisResponse.ChangeStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("suggestedValue is outside target field enum", e);
        }
    }

    private static void requireObjectFields(JsonNode node, Set<String> fields, String path) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException(path + " must be an object");
        Set<String> actual = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(fields)) throw new IllegalArgumentException(path + " has unknown or missing fields");
    }

    private static void requireArray(JsonNode node, String path) {
        if (node == null || !node.isArray()) throw new IllegalArgumentException(path + " must be an array");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isNullish(JsonNode value) {
        return value == null || value.isNull();
    }

    private record ResolvedReference(
            String issue, ArticleValidationResult.TargetType targetType, JsonNode value, String field) {}
}

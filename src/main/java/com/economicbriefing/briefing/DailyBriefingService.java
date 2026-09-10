package com.economicbriefing.briefing;

import com.economicbriefing.article.ArticleEntity;
import com.economicbriefing.article.ArticleRepository;
import com.economicbriefing.article.ParagraphSplitter;
import com.economicbriefing.article.YonhapBodyFetcher;
import com.economicbriefing.briefing.EconomicFlowLlm.Call;
import com.economicbriefing.briefing.EconomicFlowLlm.PlanFlow;
import com.economicbriefing.briefing.EconomicFlowLlm.SelectedArticle;
import com.economicbriefing.briefing.EconomicFlowLlm.WrittenFlow;
import com.economicbriefing.briefing.ObservationStore.Observation;
import com.economicbriefing.briefing.PrincipleStore.Principle;
import com.economicbriefing.config.AppProperties;
import com.economicbriefing.config.OpenAiProperties;
import com.economicbriefing.llm.OpenAiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DailyBriefingService {
    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final String PIPELINE_VERSION = "daily-flow-v1";
    private static final Logger log = LoggerFactory.getLogger(DailyBriefingService.class);
    private static final Pattern THIN_BULLETIN = Pattern.compile("^\\[(?:속보|\\d+보)]");
    private static final Pattern HARD_SIGNAL = Pattern.compile(
            "소비자물가|생산자물가|\\bCPI\\b|\\bPPI\\b|기준금리|국채금리|채권금리|대출금리|환율|취업자|실업률|고용률|\\bGDP\\b|경제성장률|가계대출|가계부채|국제유가|브렌트유|\\bWTI\\b|천연가스",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,.]*(?:%|％)?");

    private final ArticleRepository articles;
    private final DailyBriefingRepository briefings;
    private final YonhapBodyFetcher bodyFetcher;
    private final ParagraphSplitter splitter;
    private final ObservationStore observations;
    private final PrincipleStore principles;
    private final EconomicFlowLlm llm;
    private final OpenAiClient openAi;
    private final OpenAiProperties openAiProperties;
    private final AppProperties appProperties;
    private final ObjectMapper json;
    private final AtomicBoolean running = new AtomicBoolean();

    public DailyBriefingService(ArticleRepository articles, DailyBriefingRepository briefings,
            YonhapBodyFetcher bodyFetcher, ParagraphSplitter splitter, ObservationStore observations,
            PrincipleStore principles, EconomicFlowLlm llm, OpenAiClient openAi,
            OpenAiProperties openAiProperties, AppProperties appProperties, ObjectMapper json) {
        this.articles = articles;
        this.briefings = briefings;
        this.bodyFetcher = bodyFetcher;
        this.splitter = splitter;
        this.observations = observations;
        this.principles = principles;
        this.llm = llm;
        this.openAi = openAi;
        this.openAiProperties = openAiProperties;
        this.appProperties = appProperties;
        this.json = json;
    }

    public Optional<DailyBriefingEntity> run(LocalDate targetDate, String triggerType, boolean force) {
        if (!running.compareAndSet(false, true)) return Optional.empty();
        try { return Optional.of(runLocked(targetDate, triggerType, force)); }
        finally { running.set(false); }
    }

    public boolean startAsync(LocalDate targetDate) {
        if (!running.compareAndSet(false, true)) return false;
        Thread.startVirtualThread(() -> {
            try { runLocked(targetDate, "MANUAL", true); }
            catch (RuntimeException e) { log.error("Manual daily briefing failed: targetDate={}", targetDate, e); }
            finally { running.set(false); }
        });
        return true;
    }

    public boolean isRunning() { return running.get(); }

    private DailyBriefingEntity runLocked(LocalDate targetDate, String triggerType, boolean force) {
        if (!force) {
            Optional<DailyBriefingEntity> existing = briefings.findFirstByTargetDateAndStatusOrderByRevisionDesc(targetDate, "SUCCESS");
            if (existing.isPresent()) return existing.get();
        }
        UsageTracker usage = new UsageTracker();
        Trace trace = new Trace();
        DailyBriefingEntity run = startRun(targetDate, triggerType);
        try {
            OffsetDateTime windowStart = targetDate.minusDays(1).atTime(5, 0).atZone(KST).toOffsetDateTime();
            OffsetDateTime windowEnd = targetDate.atTime(5, 0).atZone(KST).toOffsetDateTime();
            List<ArticleEntity> window = canonical(articles
                    .findByPublishedAtGreaterThanEqualAndPublishedAtLessThanOrderByPublishedAtAsc(windowStart, windowEnd));
            trace.windowArticleCount = window.size();
            run.setInputHash(hash(window));
            if (window.isEmpty()) return noData(run, targetDate, trace, usage);

            List<ArticleEntity> prefiltered = prefilter(targetDate, windowStart, window, usage);
            trace.prefilteredArticleIds.addAll(prefiltered.stream().map(ArticleEntity::getId).toList());
            if (prefiltered.isEmpty()) return succeed(run, emptyResult(targetDate), trace, usage);

            Call<List<SelectedArticle>> selection = llm.select(targetDate, prefiltered);
            usage.luna("articleSelection", selection.usage());
            trace.selectedArticleIds.addAll(selection.value().stream().map(item -> item.article().getId()).toList());
            if (selection.value().isEmpty()) return succeed(run, emptyResult(targetDate), trace, usage);

            List<Observation> current = extract(selection.value(), usage, trace);
            if (current.isEmpty()) return succeed(run, emptyResult(targetDate), trace, usage);
            current = embed(current, usage);

            Context context = context(current, windowStart);
            trace.historyCandidateIds.addAll(context.history.stream().map(item -> item.observation.id()).toList());
            trace.historyCandidatePairs.addAll(context.history.stream()
                    .map(item -> item.currentAlias() + "->" + item.observation().id()).toList());
            trace.principleChunkIds.addAll(context.principleAliases.values().stream()
                    .map(item -> item.principle.chunkId()).toList());
            String plannerInput = plannerInput(context);
            ensureInputBudget("Terra", plannerInput, appProperties.budget().synthesisInputTokens());
            double projected = usage.costUsd() + estimateTokens(plannerInput) * 2d / 1_000_000d
                    + 4000d * 12d / 1_000_000d + 6000d * 1.2d / 1_000_000d;
            if (projected > appProperties.budget().dailyCostUsd())
                throw new IllegalStateException("daily cost ceiling would be exceeded: projectedUsd=" + projected);

            Call<List<PlanFlow>> planned = llm.plan(plannerInput);
            usage.terra("planner", planned.usage());
            trace.plan(planned.value(), json);
            List<String> planErrors = validatePlan(planned.value(), context);
            if (!planErrors.isEmpty()) throw new IllegalStateException("invalid Terra plan: " + planErrors);

            String writerInput = writerInput(planned.value(), context);
            Call<List<WrittenFlow>> written = llm.write(writerInput);
            usage.luna("writer", written.usage());
            List<String> writingErrors = validateWriting(written.value(), planned.value(), writerInput);
            if (!writingErrors.isEmpty()) throw new IllegalStateException("invalid Luna writing: " + writingErrors);

            ObjectNode result = publicResult(run.getId(), targetDate, planned.value(), written.value(), context);
            trace.usedHistoryObservationIds.addAll(planned.value().stream().flatMap(flow -> flow.observationIds().stream())
                    .filter(id -> id.startsWith("H")).map(context.aliases::get).map(item -> item.observation.id()).distinct().toList());
            trace.usedPrincipleIds.addAll(planned.value().stream().flatMap(flow -> flow.principleIds().stream())
                    .map(context.principleAliases::get).map(item -> item.principle.chunkId()).distinct().toList());
            return succeed(run, result, trace, usage);
        } catch (RuntimeException e) {
            run.setStatus("FAILED");
            run.setUsageJson(write(usage.json(json)));
            run.setTraceJson(write(trace.json(json)));
            run.setErrorMessage(limit(e.getMessage(), 2000));
            run.setFinishedAt(OffsetDateTime.now(KST));
            briefings.save(run);
            throw e;
        }
    }

    private List<ArticleEntity> prefilter(LocalDate date, OffsetDateTime start, List<ArticleEntity> window, UsageTracker usage) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        Map<String, ArticleEntity> byId = new LinkedHashMap<>();
        window.forEach(item -> byId.put(item.getId(), item));
        Map<Long, List<ArticleEntity>> groups = new LinkedHashMap<>();
        for (ArticleEntity item : window) {
            long index = Math.max(0, java.time.Duration.between(start, item.getPublishedAt()).toHours() / 6);
            groups.computeIfAbsent(index, ignored -> new ArrayList<>()).add(item);
        }
        int estimated = 0;
        for (List<ArticleEntity> group : groups.values()) {
            estimated += estimateTokens(group.stream().map(ArticleEntity::getTitle).reduce("", (a, b) -> a + b));
            if (estimated > appProperties.budget().screeningInputTokens())
                throw new IllegalStateException("screening input token ceiling exceeded");
            Call<List<String>> call = llm.prefilter(date, group, 40);
            usage.luna("titlePrefilter", call.usage());
            ids.addAll(call.value());
        }
        if (ids.size() > 80) {
            Call<List<String>> call = llm.prefilter(date, ids.stream().map(byId::get).toList(), 80);
            usage.luna("titlePrefilter", call.usage());
            ids = new LinkedHashSet<>(call.value());
        }
        for (ArticleEntity item : window) if (HARD_SIGNAL.matcher(item.getTitle()).find()) ids.add(item.getId());
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    private List<Observation> extract(List<SelectedArticle> selected, UsageTracker usage, Trace trace) {
        List<Observation> result = new ArrayList<>();
        int estimated = 0;
        for (SelectedArticle item : selected) {
            ArticleEntity article = item.article();
            if ("FULL_TEXT".equals(article.getBodyStatus())) {
                List<Observation> reusable = observations.findReusable(article.getId(),
                        openAiProperties.extractionModel(), EconomicFlowLlm.EXTRACTION_PROMPT_VERSION);
                if (!reusable.isEmpty()) {
                    reusable.stream().map(value -> value.withPublishedAt(article.getPublishedAt())).forEach(result::add);
                    trace.observationIds.addAll(reusable.stream().map(Observation::id).toList());
                    continue;
                }
            }
            if (!"FULL_TEXT".equals(article.getBodyStatus()) || article.getBody() == null || article.getBody().isBlank()) {
                try {
                    bodyFetcher.markSuccess(article, bodyFetcher.fetch(article.getUrl()));
                    articles.save(article);
                } catch (RuntimeException e) {
                    article.setBodyStatus("FETCH_FAILED");
                    article.setBodyFetchedAt(OffsetDateTime.now(KST));
                    articles.save(article);
                    log.warn("Selected article body skipped: articleId={}, reason={}", article.getId(), e.getMessage());
                    continue;
                }
            }
            Map<String, String> paragraphs = splitter.split(article.getBody());
            estimated += estimateTokens(article.getBody());
            if (estimated > appProperties.budget().extractionInputTokens()) {
                log.warn("Extraction budget reached; remaining lower-priority articles are skipped");
                break;
            }
            Call<List<ObservationStore.Draft>> call = llm.extract(item, paragraphs);
            usage.luna("observationExtraction", call.usage());
            List<Observation> saved = observations.replace(article.getId(), call.value(),
                    openAiProperties.extractionModel(), EconomicFlowLlm.EXTRACTION_PROMPT_VERSION);
            for (Observation observation : saved) result.add(observation.withPublishedAt(article.getPublishedAt()));
            trace.observationIds.addAll(saved.stream().map(Observation::id).toList());
        }
        return List.copyOf(result);
    }

    private List<Observation> embed(List<Observation> current, UsageTracker usage) {
        List<Observation> missing = current.stream().filter(item -> item.vector() == null).toList();
        if (missing.isEmpty()) return current;
        try {
            var embedded = openAi.embed(missing.stream().map(Observation::text).toList());
            usage.embeddingTokens += embedded.inputTokens();
            Map<String, Observation> additions = new HashMap<>();
            for (int index = 0; index < missing.size(); index++) {
                Observation observation = missing.get(index).withVector(normalized(embedded.vectors().get(index)));
                observations.saveEmbedding(observation.id(), observation.vector(), openAiProperties.embeddingModel());
                additions.put(observation.id(), observation);
            }
            return current.stream().map(item -> additions.getOrDefault(item.id(), item)).toList();
        } catch (RuntimeException e) {
            log.warn("Observation embedding failed; current-only synthesis continues: {}", e.getMessage());
            return current;
        }
    }

    private Context context(List<Observation> current, OffsetDateTime windowStart) {
        Map<String, Evidence> currentAliases = new LinkedHashMap<>();
        for (int index = 0; index < current.size(); index++)
            currentAliases.put("C%02d".formatted(index + 1), new Evidence(current.get(index), false, null, 1d));

        Map<String, HistoryChoice> bestPastArticle = new HashMap<>();
        for (var entry : currentAliases.entrySet()) {
            Observation item = entry.getValue().observation;
            if (item.vector() == null) continue;
            for (var match : observations.findPast(item.vector(), windowStart, item.articleId(), entry.getKey(), 5)) {
                HistoryChoice choice = new HistoryChoice(match.currentObservationId(), match.observation(), match.similarity());
                bestPastArticle.merge(match.observation().articleId(), choice,
                        (left, right) -> left.similarity >= right.similarity ? left : right);
            }
        }
        Map<String, Integer> currentArticleCounts = new HashMap<>();
        List<HistoryChoice> history = new ArrayList<>();
        for (HistoryChoice choice : bestPastArticle.values().stream()
                .sorted(Comparator.comparingDouble(HistoryChoice::similarity).reversed()).toList()) {
            String currentArticle = currentAliases.get(choice.currentAlias).observation.articleId();
            if (currentArticleCounts.getOrDefault(currentArticle, 0) >= 2) continue;
            history.add(choice);
            currentArticleCounts.merge(currentArticle, 1, Integer::sum);
            if (history.size() == 12) break;
        }
        Map<String, Evidence> aliases = new LinkedHashMap<>(currentAliases);
        for (int index = 0; index < history.size(); index++) {
            HistoryChoice item = history.get(index);
            aliases.put("H%02d".formatted(index + 1), new Evidence(item.observation, true, item.currentAlias, item.similarity));
        }

        Map<String, PrincipleChoice> bestPrinciples = new HashMap<>();
        for (Evidence evidence : currentAliases.values()) {
            if (evidence.observation().vector() == null) continue;
            for (Principle principle : principles.find(evidence.observation().vector(), openAiProperties.embeddingModel(), 2)) {
                PrincipleChoice choice = new PrincipleChoice(principle, principle.similarity());
                bestPrinciples.merge(principle.chunkId(), choice,
                        (left, right) -> left.principle.similarity() >= right.principle.similarity() ? left : right);
            }
        }
        int chars = 0;
        List<PrincipleChoice> selectedPrinciples = new ArrayList<>();
        for (PrincipleChoice item : bestPrinciples.values().stream()
                .sorted(Comparator.comparingDouble(value -> -value.principle.similarity())).toList()) {
            if (item.principle.similarity() < .42 || chars + item.principle.text().length() > 4000) continue;
            selectedPrinciples.add(item);
            chars += item.principle.text().length();
            if (selectedPrinciples.size() == 4) break;
        }
        Map<String, PrincipleChoice> principleAliases = new LinkedHashMap<>();
        for (int index = 0; index < selectedPrinciples.size(); index++)
            principleAliases.put("K%02d".formatted(index + 1), selectedPrinciples.get(index));
        return new Context(currentAliases, aliases, history, relations(currentAliases), principleAliases);
    }

    private List<Relation> relations(Map<String, Evidence> current) {
        List<Map.Entry<String, Evidence>> values = current.entrySet().stream()
                .filter(item -> item.getValue().observation.vector() != null).toList();
        Map<String, Relation> best = new HashMap<>();
        for (int i = 0; i < values.size(); i++) for (int j = i + 1; j < values.size(); j++) {
            Observation left = values.get(i).getValue().observation;
            Observation right = values.get(j).getValue().observation;
            if (left.articleId().equals(right.articleId())) continue;
            String pair = left.articleId().compareTo(right.articleId()) < 0
                    ? left.articleId() + "\n" + right.articleId() : right.articleId() + "\n" + left.articleId();
            double similarity = cosine(left.vector(), right.vector());
            Relation relation = new Relation(values.get(i).getKey(), values.get(j).getKey(), similarity);
            best.merge(pair, relation, (a, b) -> a.similarity >= b.similarity ? a : b);
        }
        return best.values().stream().sorted(Comparator.comparingDouble(Relation::similarity).reversed()).toList();
    }

    private String plannerInput(Context context) {
        StringBuilder value = new StringBuilder("<requiredArticles>\n");
        context.current.values().stream().map(item -> item.observation.articleId()).distinct()
                .forEach(id -> value.append(id).append('\n'));
        value.append("</requiredArticles>\n<current>\n");
        context.current.forEach((id, item) -> value.append(id).append('\t').append(item.observation.articleId())
                .append('\t').append(EconomicFlowLlm.clean(item.observation.text())).append('\n'));
        value.append("</current>\n<history>\n");
        context.aliases.forEach((id, item) -> {
            if (item.historical) value.append(id).append('\t').append(item.currentAlias).append('\t')
                    .append(item.observation.publishedAt().toLocalDate()).append('\t')
                    .append(EconomicFlowLlm.clean(item.observation.text())).append('\n');
        });
        value.append("</history>\n<connectionCandidates>\n");
        context.relations.stream().filter(item -> item.similarity() >= .30).limit(20)
                .forEach(item -> value.append(item.leftAlias()).append('\t').append(item.rightAlias()).append('\t')
                        .append("%.3f".formatted(item.similarity())).append('\n'));
        value.append("</connectionCandidates>\n<principles>\n");
        context.principleAliases.forEach((id, item) -> value.append(id).append('\t')
                .append(EconomicFlowLlm.clean(item.principle.title())).append('\t')
                .append(EconomicFlowLlm.clean(item.principle.text())).append('\n'));
        return value.append("</principles>").toString();
    }

    private String writerInput(List<PlanFlow> plan, Context context) {
        StringBuilder value = new StringBuilder();
        for (int index = 0; index < plan.size(); index++) {
            String flowId = "F%02d".formatted(index + 1);
            PlanFlow flow = plan.get(index);
            value.append('<').append(flowId).append(">\nconnection\t").append(flow.connection()).append("\n<evidence>\n");
            for (String id : flow.observationIds()) {
                Evidence item = context.aliases.get(id);
                value.append(id).append('\t');
                if (item.historical) value.append(item.observation.publishedAt().toLocalDate()).append('\t');
                value.append(EconomicFlowLlm.clean(item.observation.text())).append('\n');
            }
            value.append("</evidence>\n<principles>\n");
            for (String id : flow.principleIds()) {
                Principle principle = context.principleAliases.get(id).principle;
                value.append(id).append('\t').append(EconomicFlowLlm.clean(principle.title())).append('\t')
                        .append(EconomicFlowLlm.clean(principle.text())).append('\n');
            }
            value.append("</principles>\n</").append(flowId).append(">\n");
        }
        return value.toString();
    }

    static List<String> validatePlan(List<PlanFlow> plan, Context context) {
        List<String> errors = new ArrayList<>();
        Set<String> currentIds = context.current.keySet();
        Set<String> allowed = context.aliases.keySet();
        Set<String> principleIds = context.principleAliases.keySet();
        Set<String> coveredArticles = new HashSet<>();
        for (int index = 0; index < plan.size(); index++) {
            PlanFlow flow = plan.get(index);
            Set<String> ids = new LinkedHashSet<>(flow.observationIds());
            if (flow.connection().isBlank() || ids.size() != flow.observationIds().size()) errors.add("F" + index + ": empty or duplicate");
            if (!allowed.containsAll(ids)) errors.add("F" + index + ": unknown observation");
            Set<String> usedCurrent = new HashSet<>(ids); usedCurrent.retainAll(currentIds);
            if (usedCurrent.isEmpty()) errors.add("F" + index + ": no current observation");
            usedCurrent.forEach(id -> coveredArticles.add(context.current.get(id).observation.articleId()));
            if (!principleIds.containsAll(flow.principleIds())) errors.add("F" + index + ": unknown principle");
        }
        Set<String> required = new HashSet<>();
        context.current.values().forEach(item -> required.add(item.observation.articleId()));
        if (!coveredArticles.equals(required)) errors.add("article coverage mismatch");
        return errors;
    }

    static List<String> validateWriting(List<WrittenFlow> writing, List<PlanFlow> plan, String source) {
        List<String> errors = new ArrayList<>();
        if (writing.size() != plan.size()) errors.add("flow count mismatch");
        Set<String> allowedNumbers = numbers(source);
        for (int index = 0; index < writing.size(); index++) {
            WrittenFlow flow = writing.get(index);
            if (!flow.flowId().equals("F%02d".formatted(index + 1))) errors.add("flow order mismatch");
            if (flow.title().isBlank() || flow.explanation().isBlank()) errors.add(flow.flowId() + ": empty text");
            Set<String> extra = numbers(flow.title() + " " + flow.explanation() + " " + String.join(" ", flow.watchPoints()));
            extra.removeAll(allowedNumbers);
            if (!extra.isEmpty()) errors.add(flow.flowId() + ": numbers outside evidence=" + extra);
        }
        return errors;
    }

    private ObjectNode publicResult(String runId, LocalDate date, List<PlanFlow> plan,
            List<WrittenFlow> writing, Context context) {
        ObjectNode result = emptyResult(date);
        result.put("generatedAt", OffsetDateTime.now(KST).toString());
        ArrayNode flows = (ArrayNode) result.get("flows");
        Map<String, ArticleEntity> articleMap = new HashMap<>();
        articles.findAllById(context.aliases.values().stream().map(item -> item.observation.articleId()).distinct().toList())
                .forEach(item -> articleMap.put(item.getId(), item));
        for (int index = 0; index < writing.size(); index++) {
            WrittenFlow written = writing.get(index);
            PlanFlow planned = plan.get(index);
            ObjectNode flow = flows.addObject();
            flow.put("id", runId + ":F" + (index + 1));
            flow.put("title", written.title());
            flow.put("explanation", written.explanation());
            ArrayNode watch = flow.putArray("watchPoints"); written.watchPoints().forEach(watch::add);
            ArrayNode sources = flow.putArray("sources");
            Map<String, ObjectNode> sourcesByArticle = new LinkedHashMap<>();
            for (String alias : planned.observationIds()) {
                Evidence evidence = context.aliases.get(alias);
                ArticleEntity article = articleMap.get(evidence.observation.articleId());
                if (article == null) continue;
                ObjectNode source = sourcesByArticle.get(article.getId());
                if (source == null) {
                    source = sources.addObject();
                    sourcesByArticle.put(article.getId(), source);
                    source.put("articleId", article.getId());
                    source.put("title", article.getTitle());
                    source.put("source", article.getSource());
                    source.put("url", article.getUrl());
                    if (article.getPublishedAt() != null) source.put("publishedAt", article.getPublishedAt().toString());
                    source.putArray("observations");
                }
                ObjectNode observation = ((ArrayNode) source.get("observations")).addObject();
                observation.put("text", evidence.observation.text());
                ArrayNode spans = observation.putArray("evidence");
                Map<String, String> paragraphs = splitter.split(article.getBody());
                for (String spanId : evidence.observation.spanIds()) {
                    if (paragraphs.containsKey(spanId)) {
                        ObjectNode span = spans.addObject(); span.put("spanId", spanId); span.put("text", paragraphs.get(spanId));
                    }
                }
            }
            ArrayNode principleArray = flow.putArray("principles");
            for (String alias : planned.principleIds()) {
                Principle principle = context.principleAliases.get(alias).principle;
                ObjectNode item = principleArray.addObject();
                item.put("chunkId", principle.chunkId()); item.put("source", principle.source()); item.put("section", principle.title());
            }
        }
        return result;
    }

    private DailyBriefingEntity startRun(LocalDate date, String triggerType) {
        int revision = briefings.findFirstByTargetDateOrderByRevisionDesc(date).map(item -> item.getRevision() + 1).orElse(1);
        DailyBriefingEntity run = new DailyBriefingEntity();
        run.setId(UUID.randomUUID().toString()); run.setTargetDate(date); run.setRevision(revision);
        run.setStatus("RUNNING"); run.setTriggerType(triggerType); run.setPipelineVersion(PIPELINE_VERSION);
        ObjectNode models = json.createObjectNode();
        models.put("screening", openAiProperties.screeningModel()); models.put("extraction", openAiProperties.extractionModel());
        models.put("planner", openAiProperties.synthesisModel()); models.put("writer", openAiProperties.writingModel());
        models.put("embedding", openAiProperties.embeddingModel());
        run.setModelsJson(write(models)); run.setStartedAt(OffsetDateTime.now(KST));
        return briefings.save(run);
    }

    private DailyBriefingEntity succeed(DailyBriefingEntity run, ObjectNode result, Trace trace, UsageTracker usage) {
        run.setStatus("SUCCESS"); run.setResultJson(write(result)); run.setTraceJson(write(trace.json(json)));
        run.setUsageJson(write(usage.json(json))); run.setFinishedAt(OffsetDateTime.now(KST));
        return briefings.save(run);
    }

    private DailyBriefingEntity noData(DailyBriefingEntity run, LocalDate date, Trace trace, UsageTracker usage) {
        run.setStatus("NO_DATA"); run.setResultJson(write(emptyResult(date))); run.setTraceJson(write(trace.json(json)));
        run.setUsageJson(write(usage.json(json))); run.setErrorMessage("no articles in briefing window");
        run.setFinishedAt(OffsetDateTime.now(KST)); return briefings.save(run);
    }

    private ObjectNode emptyResult(LocalDate date) {
        ObjectNode result = json.createObjectNode(); result.put("targetDate", date.toString());
        result.put("generatedAt", OffsetDateTime.now(KST).toString()); result.put("title", date.getMonthValue() + "월 " + date.getDayOfMonth() + "일 경제흐름");
        result.putArray("flows"); return result;
    }

    private List<ArticleEntity> canonical(List<ArticleEntity> source) {
        Map<String, ArticleEntity> latest = new LinkedHashMap<>();
        for (ArticleEntity article : source) {
            if (THIN_BULLETIN.matcher(article.getTitle()).find()) continue;
            String key = article.getSourceArticleId() == null ? article.getUrl() : article.getSourceArticleId();
            latest.merge(key, article, (left, right) -> left.getPublishedAt().isAfter(right.getPublishedAt()) ? left : right);
        }
        return latest.values().stream().sorted(Comparator.comparing(ArticleEntity::getPublishedAt)).toList();
    }

    private static void ensureInputBudget(String stage, String value, int maxTokens) {
        int estimated = estimateTokens(value);
        if (estimated > maxTokens) throw new IllegalStateException(stage + " input token ceiling exceeded: " + estimated + "/" + maxTokens);
    }
    static int estimateTokens(String value) { return (int) Math.ceil(value.codePointCount(0, value.length()) / 1.5d); }
    private String write(JsonNode value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static String limit(String value, int max) { if (value == null) return null; return value.length() <= max ? value : value.substring(0, max); }
    private static String hash(List<ArticleEntity> values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (ArticleEntity item : values) digest.update((item.getId() + "|" + item.getTitle() + "|" + item.getPublishedAt() + "\n").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static Set<String> numbers(String value) {
        Set<String> result = new HashSet<>(); var matcher = NUMBER.matcher(value.replace(",", ""));
        while (matcher.find()) result.add(matcher.group()); return result;
    }
    private static float[] normalized(float[] value) {
        double norm = 0; for (float item : value) norm += item * item; norm = Math.sqrt(norm);
        if (norm == 0) return value; float[] result = new float[value.length];
        for (int i = 0; i < value.length; i++) result[i] = (float) (value[i] / norm); return result;
    }
    private static double cosine(float[] left, float[] right) {
        double value = 0; for (int i = 0; i < left.length; i++) value += left[i] * right[i]; return value;
    }

    static record Evidence(Observation observation, boolean historical, String currentAlias, double similarity) {}
    static record HistoryChoice(String currentAlias, Observation observation, double similarity) {}
    static record PrincipleChoice(Principle principle, double relationSimilarity) {}
    static record Relation(String leftAlias, String rightAlias, double similarity) {}
    static record Context(Map<String, Evidence> current, Map<String, Evidence> aliases,
                          List<HistoryChoice> history, List<Relation> relations,
                          Map<String, PrincipleChoice> principleAliases) {}

    private static final class Trace {
        int windowArticleCount;
        final List<String> prefilteredArticleIds = new ArrayList<>(), selectedArticleIds = new ArrayList<>(),
                observationIds = new ArrayList<>(), historyCandidateIds = new ArrayList<>(), historyCandidatePairs = new ArrayList<>(), principleChunkIds = new ArrayList<>(),
                usedHistoryObservationIds = new ArrayList<>(), usedPrincipleIds = new ArrayList<>();
        ArrayNode plan;
        void plan(List<PlanFlow> flows, ObjectMapper json) {
            plan = json.createArrayNode();
            for (PlanFlow flow : flows) {
                ObjectNode item = plan.addObject();
                ArrayNode observationIds = item.putArray("observationIds");
                flow.observationIds().forEach(observationIds::add);
                ArrayNode principleIds = item.putArray("principleIds");
                flow.principleIds().forEach(principleIds::add);
                item.put("connection", flow.connection());
            }
        }
        ObjectNode json(ObjectMapper json) {
            ObjectNode node = json.createObjectNode(); node.put("windowArticleCount", windowArticleCount);
            add(node, "prefilteredArticleIds", prefilteredArticleIds); add(node, "selectedArticleIds", selectedArticleIds);
            add(node, "observationIds", observationIds); add(node, "historyCandidateIds", historyCandidateIds);
            add(node, "historyCandidatePairs", historyCandidatePairs);
            add(node, "principleChunkIds", principleChunkIds); add(node, "usedHistoryObservationIds", usedHistoryObservationIds);
            add(node, "usedPrincipleIds", usedPrincipleIds);
            if (plan != null) node.set("plan", plan);
            return node;
        }
        private static void add(ObjectNode node, String name, List<String> values) { ArrayNode array = node.putArray(name); values.forEach(array::add); }
    }

    private static final class UsageTracker {
        final Map<String, StageUsage> stages = new LinkedHashMap<>();
        int lunaInput, lunaCacheWrite, lunaCached, lunaOutput, terraInput, terraCacheWrite, terraCached, terraOutput, embeddingTokens;
        void luna(String stage, OpenAiClient.Usage usage) { add(stage, usage); lunaInput += usage.inputTokens(); lunaCacheWrite += usage.cacheWriteInputTokens(); lunaCached += usage.cachedInputTokens(); lunaOutput += usage.outputTokens(); }
        void terra(String stage, OpenAiClient.Usage usage) { add(stage, usage); terraInput += usage.inputTokens(); terraCacheWrite += usage.cacheWriteInputTokens(); terraCached += usage.cachedInputTokens(); terraOutput += usage.outputTokens(); }
        void add(String stage, OpenAiClient.Usage usage) { stages.computeIfAbsent(stage, ignored -> new StageUsage()).add(usage); }
        double costUsd() { return ((lunaInput - lunaCached) * .2 + lunaCached * .02 + lunaOutput * 1.2
                + (terraInput - terraCached) * 2 + terraCached * .2 + terraOutput * 12
                + embeddingTokens * .13) / 1_000_000d; }
        ObjectNode json(ObjectMapper json) {
            ObjectNode root = json.createObjectNode();
            ObjectNode luna = root.putObject("luna"); luna.put("input", lunaInput); luna.put("cacheWriteInput", lunaCacheWrite); luna.put("cachedInput", lunaCached); luna.put("output", lunaOutput);
            ObjectNode terra = root.putObject("terra"); terra.put("input", terraInput); terra.put("cacheWriteInput", terraCacheWrite); terra.put("cachedInput", terraCached); terra.put("output", terraOutput);
            root.putObject("embedding").put("input", embeddingTokens);
            ObjectNode byStage = root.putObject("stages");
            stages.forEach((name, usage) -> byStage.set(name, usage.json(json)));
            root.put("estimatedCostUsd", costUsd()); return root;
        }
    }
    private static final class StageUsage {
        int input, cacheWrite, cached, output, calls;
        void add(OpenAiClient.Usage usage) { input += usage.inputTokens(); cacheWrite += usage.cacheWriteInputTokens(); cached += usage.cachedInputTokens(); output += usage.outputTokens(); calls++; }
        ObjectNode json(ObjectMapper json) { ObjectNode node = json.createObjectNode(); node.put("calls", calls); node.put("input", input); node.put("cacheWriteInput", cacheWrite); node.put("cachedInput", cached); node.put("output", output); return node; }
    }
}

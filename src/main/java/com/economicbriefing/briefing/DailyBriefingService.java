package com.economicbriefing.briefing;

import com.economicbriefing.article.ArticleEntity;
import com.economicbriefing.article.ArticleRepository;
import com.economicbriefing.article.ParagraphSplitter;
import com.economicbriefing.article.YonhapBodyFetcher;
import com.economicbriefing.briefing.EconomicFlowLlm.Call;
import com.economicbriefing.briefing.EconomicFlowLlm.Answer;
import com.economicbriefing.briefing.EconomicFlowLlm.PlanFlow;
import com.economicbriefing.briefing.EconomicFlowLlm.SelectedArticle;
import com.economicbriefing.briefing.EconomicFlowLlm.WrittenFlow;
import com.economicbriefing.briefing.EconomicFlowLlm.Selection;
import com.economicbriefing.briefing.EconomicFlowLlm.Question;
import com.economicbriefing.briefing.EconomicFlowLlm.Writing;
import com.economicbriefing.briefing.EconomicFlowLlm.WritingScope;
import com.economicbriefing.briefing.EconomicFlowLlm.AnswerScope;
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
    static final String PIPELINE_VERSION = "daily-flow-v2.3";
    private static final int PLANNER_SUPPLEMENTAL_CHARS = 2500;
    private static final int PLANNER_EVIDENCE_CHARS = 8500;
    private static final int PLANNER_PRINCIPLE_CHARS = 1500;
    private static final Pattern RELATION_CONTEXT = Pattern.compile(
            "때문|하지만|다만|반면|지만|탓|영향|우려|의존|여전히|조건|제약|한계|대신|선호|희망|요구|반등|부담|유인|이유|만큼|앞두고");
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
        return runLocked(targetDate, triggerType, force, null);
    }

    // Package-private: the opt-in replay test calls the real pipeline against an isolated database.
    DailyBriefingEntity review(LocalDate date, ArticleEntity article) {
        return review(date, article, false);
    }

    DailyBriefingEntity review(LocalDate date, ArticleEntity article, boolean selectedByUser) {
        if (!running.compareAndSet(false, true)) throw new IllegalStateException("already running");
        try { return runLocked(date, selectedByUser ? "USER_SELECTED_REVIEW" : "USER_REVIEW", true, List.of(article)); }
        finally { running.set(false); }
    }

    private DailyBriefingEntity runLocked(LocalDate targetDate, String triggerType, boolean force, List<ArticleEntity> reviewWindow) {
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
            List<ArticleEntity> window = reviewWindow == null ? canonical(articles
                    .findByPublishedAtGreaterThanEqualAndPublishedAtLessThanOrderByPublishedAtAsc(windowStart, windowEnd)) : reviewWindow;
            if (window.stream().anyMatch(a -> a.getPublishedAt().isBefore(windowStart) || !a.getPublishedAt().isBefore(windowEnd)))
                throw new IllegalArgumentException("article outside briefing window");
            trace.windowArticleCount = window.size();
            for (OffsetDateTime hour = windowStart; hour.isBefore(windowEnd); hour = hour.plusHours(1)) {
                OffsetDateTime start = hour, end = hour.plusHours(1);
                trace.articleCountsByHour.put(hour.toString(), window.stream()
                        .filter(a -> !a.getPublishedAt().isBefore(start) && a.getPublishedAt().isBefore(end)).count());
            }
            run.setInputHash(hash(window));
            if (window.isEmpty()) return noData(run, targetDate, trace, usage);

            List<ArticleEntity> prefiltered = reviewWindow == null ? prefilter(targetDate, windowStart, window, usage) : window;
            trace.prefilteredArticleIds.addAll(prefiltered.stream().map(ArticleEntity::getId).toList());
            if (prefiltered.isEmpty()) return succeed(run, emptyResult(targetDate), trace, usage);

            Selection selection;
            if (reviewWindow != null && "USER_SELECTED_REVIEW".equals(triggerType)) {
                selection = new Selection(List.of(new SelectedArticle(reviewWindow.getFirst(),
                        "사용자가 지정한 기사. 원문에 근거한 경제 관측을 검토한다.")), List.of());
                trace.selection = json.createObjectNode().put("mode", "USER_SELECTED_FOR_REVIEW");
            } else {
                ensureCost(usage, windowText(prefiltered), false, 2500, .09);
                Call<Selection> call = llm.select(targetDate, prefiltered);
                selection = call.value();
                usage.luna("articleSelection", call.usage());
                trace.selection = call.raw();
            }
            trace.selectedArticleIds.addAll(selection.briefingArticles().stream().map(item -> item.article().getId()).toList());
            trace.memoryArticleIds.addAll(selection.memoryArticles().stream().map(item -> item.article().getId()).toList());
            if (selection.all().isEmpty()) return succeed(run, emptyResult(targetDate), trace, usage);

            List<Observation> extracted = extract(selection.all(), usage, trace, .09);
            List<Observation> embedded = embed(extracted.stream().filter(o -> o.remember() || trace.selectedArticleIds.contains(o.articleId())).toList(), usage);
            List<Observation> current = embedded.stream().filter(o -> trace.selectedArticleIds.contains(o.articleId())).toList();
            if (current.isEmpty()) return succeed(run, emptyResult(targetDate), trace, usage);

            Context context = context(current, windowStart);
            trace.historyCandidateIds.addAll(context.history.stream().map(item -> item.observation.id()).toList());
            trace.historyCandidatePairs.addAll(context.history.stream()
                    .map(item -> item.currentAlias() + "->" + item.observation().id()).toList());
            trace.principleChunkIds.addAll(context.principleAliases.values().stream()
                    .map(item -> item.principle.chunkId()).toList());
            List<ArticleEntity> briefingArticles = selection.briefingArticles().stream().map(SelectedArticle::article).toList();
            int affordablePlannerInput = Math.min(appProperties.budget().synthesisInputTokens(),
                    Math.max(0, (int) Math.floor((appProperties.budget().dailyCostUsd() - usage.costUsd()
                            - .015 - EconomicFlowLlm.PLAN_MAX_OUTPUT_TOKENS * 12 / 1_000_000d) * 1_000_000d / 2 - 2500) - 1));
            int evidenceChars = PLANNER_EVIDENCE_CHARS;
            String plannerInput = plannerInput(context, briefingArticles, splitter, evidenceChars);
            while (estimateTokens(plannerInput) > affordablePlannerInput && evidenceChars > 0) {
                evidenceChars = Math.max(0, evidenceChars - 500);
                plannerInput = plannerInput(context, briefingArticles, splitter, evidenceChars);
            }
            trace.plannerEvidenceCharsBudget = evidenceChars;
            ensureInputBudget("Terra available cost budget", plannerInput, affordablePlannerInput);
            ensureInputBudget("Terra", plannerInput, appProperties.budget().synthesisInputTokens());
            ensureCost(usage, plannerInput, true, EconomicFlowLlm.PLAN_MAX_OUTPUT_TOKENS, .015);
            Call<List<PlanFlow>> planned = llm.plan(plannerInput);
            usage.terra("planner", planned.usage());
            List<PlanFlow> normalized = includeQuestionEvidence(planned.value(), context);
            for (int f = 0; f < normalized.size(); f++) {
                var original = planned.value().get(f).observationIds();
                for (String id : normalized.get(f).observationIds()) if (!original.contains(id))
                    trace.questionEvidenceAdded.add("F%02d:%s".formatted(f + 1, id));
            }
            planned = new Call<>(normalized, planned.usage(), planned.raw());
            trace.plan(planned.value(), json);
            List<String> planErrors = validatePlan(planned.value(), context);
            if (!planErrors.isEmpty()) throw new IllegalStateException("invalid Terra plan: " + planErrors);

            Map<String, QuestionContext> questions = retrieveQuestions(planned.value(), context, windowStart, windowEnd, usage, trace);
            List<WrittenFlow> writtenFlows = new ArrayList<>(); List<String> conflicts = new ArrayList<>();
            List<WriterBatch> batches = writerBatches(planned.value(), context, questions, appProperties.budget().synthesisInputTokens());
            int remainingOutput = EconomicFlowLlm.WRITE_MAX_OUTPUT_TOKENS;
            int remainingWeight = batches.stream().mapToInt(WriterBatch::weight).sum();
            for (WriterBatch batch : batches) {
                int outputTokens = remainingOutput * batch.weight() / remainingWeight;
                remainingOutput -= outputTokens; remainingWeight -= batch.weight();
                ensureInputBudget("writer", batch.input(), appProperties.budget().synthesisInputTokens());
                ensureCost(usage, batch.input(), false, outputTokens, remainingOutput * 1.2 / 1_000_000d);
                Call<Writing> written = llm.write(batch.input(), batch.scopes(), outputTokens);
                usage.luna("writer", written.usage());
                writtenFlows.addAll(written.value().flows()); conflicts.addAll(written.value().conflicts());
                trace.writing = json.valueToTree(new Writing(List.copyOf(writtenFlows), List.copyOf(conflicts)));
            }
            Writing writing = completeNumberCitations(new Writing(List.copyOf(writtenFlows), List.copyOf(conflicts)), questions);
            List<String> writingErrors = validateWriting(writing, planned.value(), context, questions);
            if (!writingErrors.isEmpty()) throw new IllegalStateException("invalid Luna writing: " + writingErrors);

            ObjectNode result = publicResult(run.getId(), targetDate, planned.value(), writing.flows(), context, questions);
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
            ensureCost(usage, windowText(group), false, 2000, .09);
            Call<List<String>> call = llm.prefilter(date, group, 40);
            usage.luna("titlePrefilter", call.usage());
            ids.addAll(call.value());
        }
        if (ids.size() > 80) {
            ensureCost(usage, windowText(ids.stream().map(byId::get).toList()), false, 2000, .09);
            Call<List<String>> call = llm.prefilter(date, ids.stream().map(byId::get).toList(), 80);
            usage.luna("titlePrefilter", call.usage());
            ids = new LinkedHashSet<>(call.value());
        }
        for (ArticleEntity item : window) if (HARD_SIGNAL.matcher(item.getTitle()).find()) ids.add(item.getId());
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    private List<Observation> extract(List<SelectedArticle> selected, UsageTracker usage, Trace trace, double reserved) {
        List<Observation> result = new ArrayList<>();
        for (SelectedArticle item : selected) {
            ArticleEntity article = item.article();
            if ("FULL_TEXT".equals(article.getBodyStatus())) {
                List<Observation> reusable = observations.findReusable(article,
                        openAiProperties.extractionModel(), EconomicFlowLlm.EXTRACTION_PROMPT_VERSION);
                if (!reusable.isEmpty()) {
                    result.addAll(reusable);
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
            usage.extractionInput += estimateTokens(article.getBody());
            if (usage.extractionInput > appProperties.budget().extractionInputTokens())
                throw new IllegalStateException("extraction input ceiling exceeded");
            ensureCost(usage, article.getBody(), false, EconomicFlowLlm.EXTRACT_MAX_OUTPUT_TOKENS, reserved);
            Call<List<ObservationStore.Draft>> call = llm.extract(item, paragraphs);
            usage.luna("observationExtraction", call.usage());
            ObjectNode decision = trace.memoryDecisions.addObject();
            decision.put("articleId", article.getId()); decision.set("raw", call.raw());
            decision.set("accepted", json.valueToTree(call.value()));
            List<Observation> saved = observations.replace(article, call.value(),
                    openAiProperties.extractionModel(), EconomicFlowLlm.EXTRACTION_PROMPT_VERSION);
            result.addAll(saved);
            trace.observationIds.addAll(saved.stream().map(Observation::id).toList());
        }
        return List.copyOf(result);
    }

    private List<Observation> embed(List<Observation> current, UsageTracker usage) {
        List<Observation> missing = current.stream().filter(item -> item.vector() == null).toList();
        if (missing.isEmpty()) return current;
        ensureEmbeddingCost(usage, missing.stream().map(Observation::text).toList(), .09);
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
            for (var match : observations.findPast(item.vector(), windowStart, item.articleId(), entry.getKey(), openAiProperties.embeddingModel(), 5)) {
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

    static String plannerInput(Context context, List<ArticleEntity> articles, ParagraphSplitter splitter) {
        return plannerInput(context, articles, splitter, PLANNER_EVIDENCE_CHARS);
    }

    static String plannerInput(Context context, List<ArticleEntity> articles, ParagraphSplitter splitter, int evidenceChars) {
        StringBuilder value = new StringBuilder("<requiredArticles>\n");
        Map<String, String> articleAliases = new LinkedHashMap<>();
        context.current.values().stream().map(Evidence::observation).forEach(o -> {
            if (!articleAliases.containsKey(o.articleId())) {
                String alias = "A%02d".formatted(articleAliases.size() + 1);
                articleAliases.put(o.articleId(), alias);
                value.append(alias).append('\t').append(o.publishedAt()).append('\n');
            }
        });
        value.append("</requiredArticles>\n<current>\n");
        context.current.forEach((id, item) -> value.append(id).append('\t').append(articleAliases.get(item.observation.articleId()))
                .append('\t').append(EconomicFlowLlm.clean(item.observation.text())).append('\n'));
        value.append("</current>\n<currentEvidence>\n");
        Set<String> seenSpans = new HashSet<>();
        Map<String, List<String>> direct = new LinkedHashMap<>(), adjacent = new LinkedHashMap<>();
        Map<String, List<String>> spanRefs = new LinkedHashMap<>();
        context.current.forEach((id, item) -> {
            Observation o = item.observation;
            if (o.snapshot() != null) o.snapshot().path("spans").fieldNames().forEachRemaining(span ->
                    spanRefs.computeIfAbsent(o.articleId() + ":" + o.snapshot().path("bodyHash").asText() + ":" + span,
                            ignored -> new ArrayList<>()).add(id));
        });
        context.current.forEach((id, item) -> {
            Observation o = item.observation;
            if (o.snapshot() != null) o.snapshot().path("spans").fields().forEachRemaining(span -> {
                String key = o.articleId() + ":" + o.snapshot().path("bodyHash").asText() + ":" + span.getKey();
                if (seenSpans.add(key)) direct.computeIfAbsent(o.articleId(), ignored -> new ArrayList<>())
                        .add("refs=" + String.join(",", spanRefs.get(key)) + "\t" + articleAliases.get(o.articleId()) + "\t" + span.getKey() + "\t"
                                + EconomicFlowLlm.clean(span.getValue().asText()) + "\n");
            });
        });
        int supplementalChars = 0;
        // ponytail: one neighbor on either side, 2,500 chars total; evaluate distant omissions before widening.
        for (ArticleEntity article : articles) {
            if (!"FULL_TEXT".equals(article.getBodyStatus()) || article.getBody() == null) continue;
            String bodyHash = ObservationStore.bodyHash(article.getBody());
            Set<String> anchors = new HashSet<>();
            context.current.values().stream().map(Evidence::observation)
                    .filter(o -> o.articleId().equals(article.getId()) && o.snapshot() != null
                            && bodyHash.equals(o.snapshot().path("bodyHash").asText()))
                    .forEach(o -> anchors.addAll(o.spanIds()));
            if (anchors.isEmpty()) continue;
            var paragraphs = new ArrayList<>(splitter.split(article.getBody()).entrySet());
            for (int i = 0; i < paragraphs.size(); i++) {
                var paragraph = paragraphs.get(i);
                String key = article.getId() + ":" + bodyHash + ":" + paragraph.getKey();
                boolean neighbor = (i > 0 && anchors.contains(paragraphs.get(i - 1).getKey()))
                        || (i + 1 < paragraphs.size() && anchors.contains(paragraphs.get(i + 1).getKey()));
                if (!neighbor || seenSpans.contains(key) || !splitter.usableEvidence(paragraph.getValue())) continue;
                String line = articleAliases.get(article.getId()) + "\t" + paragraph.getKey() + "\t"
                        + EconomicFlowLlm.clean(paragraph.getValue()) + "\n";
                if (supplementalChars + line.length() > PLANNER_SUPPLEMENTAL_CHARS) continue;
                seenSpans.add(key); supplementalChars += line.length();
                adjacent.computeIfAbsent(article.getId(), ignored -> new ArrayList<>()).add(line);
            }
        }
        direct.replaceAll((id, lines) -> lines.stream()
                .sorted(Comparator.comparingLong(DailyBriefingService::relationContextScore).reversed()).toList());
        adjacent.replaceAll((id, lines) -> lines.stream()
                .sorted(Comparator.comparingLong(DailyBriefingService::relationContextScore).reversed()).toList());
        int directChars = appendBalancedEvidence(value, direct, Math.max(0, evidenceChars - 1000));
        value.append("</currentEvidence>\n<currentAdjacentEvidence>\n");
        appendBalancedEvidence(value, adjacent, evidenceChars - directChars);
        value.append("</currentAdjacentEvidence>\n<history>\n");
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
        int principleChars = 0;
        for (var entry : context.principleAliases.entrySet()) {
            Principle principle = entry.getValue().principle;
            String line = entry.getKey() + "\t" + EconomicFlowLlm.clean(principle.title()) + "\t"
                    + EconomicFlowLlm.clean(principle.text()) + "\n";
            if (principleChars + line.length() > PLANNER_PRINCIPLE_CHARS) continue;
            value.append(line); principleChars += line.length();
        }
        return value.append("</principles>").toString();
    }

    // Keep every compressed observation; share the optional verbatim context across articles.
    static int appendBalancedEvidence(StringBuilder target, Map<String, List<String>> groups, int maxChars) {
        int used = 0, largest = groups.values().stream().mapToInt(List::size).max().orElse(0);
        for (int index = 0; index < largest; index++) for (List<String> lines : groups.values()) {
            if (index >= lines.size()) continue;
            String line = lines.get(index);
            if (used + line.length() > maxChars) continue;
            target.append(line); used += line.length();
        }
        return used;
    }

    static long relationContextScore(String text) {
        return RELATION_CONTEXT.matcher(text).results().count();
    }

    private Map<String, QuestionContext> retrieveQuestions(List<PlanFlow> plan, Context context,
            OffsetDateTime windowStart, OffsetDateTime windowEnd, UsageTracker usage, Trace trace) {
        Map<String, QuestionContext> result = new LinkedHashMap<>();
        Map<String, Question> pending = new LinkedHashMap<>();
        for (int f = 0; f < plan.size(); f++) for (int q = 0; q < plan.get(f).questions().size(); q++) {
            Question question = plan.get(f).questions().get(q);
            String id = "F%02d:Q%02d".formatted(f + 1, q + 1);
            QuestionContext source = questionSources(plan.get(f), question, context);
            result.put(id, source);
            if (question.articleQuery().isBlank() && question.principleQuery().isBlank()) continue;
            if (pending.size() >= 8) { source.status = "BUDGET_NOT_SEARCHED"; continue; }
            pending.put(id, question);
        }
        List<String> queries = pending.values().stream()
                .flatMap(q -> java.util.stream.Stream.of(q.articleQuery(), q.principleQuery()))
                .filter(q -> !q.isBlank()).distinct().toList();
        Map<String, float[]> vectors = new HashMap<>();
        if (!queries.isEmpty()) {
            try {
                ensureEmbeddingCost(usage, queries, .015);
                var embedded = openAi.embed(queries);
                usage.embeddingTokens += embedded.inputTokens();
                for (int i = 0; i < queries.size(); i++) vectors.put(queries.get(i), embedded.vectors().get(i));
            } catch (RuntimeException e) {
                trace.questionSearches.addObject().put("embeddingError", limit(e.getMessage(), 300));
            }
        }
        Map<String, Evidence> additions = new LinkedHashMap<>();
        Map<String, PrincipleChoice> newPrinciples = new LinkedHashMap<>();
        plan.forEach(flow -> flow.principleIds().forEach(id -> newPrinciples.put(id, context.principleAliases.get(id))));
        Set<String> rawArticles = new LinkedHashSet<>(), attempted = new HashSet<>();
        int rawChars = 0;
        for (var entry : pending.entrySet()) {
            Question q = entry.getValue(); QuestionContext source = result.get(entry.getKey());
            source.status = "SEARCHED";
            ObjectNode search = trace.questionSearches.addObject().put("questionId", entry.getKey());
            search.put("articleQuery", q.articleQuery()); search.put("principleQuery", q.principleQuery());
            search.set("articleTerms", json.valueToTree(q.articleTerms()));
            if (!q.principleQuery().isBlank() && vectors.containsKey(q.principleQuery())) {
                List<Principle> candidates = principles.find(vectors.get(q.principleQuery()), openAiProperties.embeddingModel(), 2);
                search.set("principleCandidates", json.valueToTree(candidates));
                for (Principle principle : candidates) {
                    String alias = newPrinciples.entrySet().stream().filter(e -> e.getValue().principle.chunkId().equals(principle.chunkId()))
                            .map(Map.Entry::getKey).findFirst().orElse(null);
                    if (alias == null && newPrinciples.size() < 4
                            && newPrinciples.values().stream().mapToInt(p -> p.principle.text().length()).sum() + principle.text().length() <= 4000) {
                        alias = "K%02d".formatted(context.principleAliases.size() + newPrinciples.size() + 1);
                        newPrinciples.put(alias, new PrincipleChoice(principle, principle.similarity()));
                    }
                    if (alias != null) source.principles.put(alias, newPrinciples.get(alias));
                }
            }
            if (!q.articleQuery().isBlank()) {
                List<Observation> matches = new ArrayList<>(observations.findMatchingMemory(q.articleTerms(), windowStart, 6));
                if (vectors.containsKey(q.articleQuery())) observations.findPast(vectors.get(q.articleQuery()), windowStart,
                        "", entry.getKey(), openAiProperties.embeddingModel(), 6).forEach(m -> matches.add(m.observation()));
                // A question about this article must still find its omitted paragraphs when title search misses.
                List<String> articleIds = new ArrayList<>(q.observationIds().stream()
                        .map(id -> context.current.get(id).observation.articleId()).distinct().toList());
                for (String id : observations.findArticleIds(q.articleTerms(), windowEnd, 3))
                    if (!articleIds.contains(id)) articleIds.add(id);
                for (Observation match : matches) if (!articleIds.contains(match.articleId())) articleIds.add(match.articleId());
                ArrayNode candidates = search.putArray("candidateArticleIds"); articleIds.forEach(candidates::add);
                for (String articleId : articleIds.stream().limit(3).toList()) {
                    // Retain several memories from the same article when one states the decision and another its reason.
                    for (Observation match : matches.stream().filter(o -> o.articleId().equals(articleId)).distinct().toList()) {
                        String alias = additions.entrySet().stream().filter(e -> e.getValue().observation.id().equals(match.id())
                                && e.getValue().observation.snapshot().equals(match.snapshot())).map(Map.Entry::getKey).findFirst().orElse(null);
                        int chars = match.text().length() + match.snapshot().path("spans").toString().length();
                        if (alias == null && rawChars + chars <= 8000 && (rawArticles.contains(articleId) || rawArticles.size() < 8)) {
                            alias = "R%03d".formatted(additions.size() + 1);
                            additions.put(alias, new Evidence(match, true, null, 0)); rawChars += chars; rawArticles.add(articleId);
                        }
                        if (alias != null) source.evidence.put(alias, additions.get(alias));
                    }
                    ArticleEntity article = articles.findById(articleId).orElse(null);
                    if (article == null || !article.getPublishedAt().isBefore(windowEnd)) continue;
                    if (!"FULL_TEXT".equals(article.getBodyStatus()) || article.getBody() == null) {
                        if (attempted.size() >= 3 || !attempted.add(articleId)) continue;
                        // A historical replay must not silently use today's revised web page.
                        if (windowEnd.toLocalDate().isBefore(LocalDate.now(KST))) { source.status = "HISTORICAL_BODY_UNAVAILABLE"; continue; }
                        try {
                            bodyFetcher.markSuccess(article, bodyFetcher.fetch(article.getUrl())); articles.save(article);
                        } catch (RuntimeException e) { source.status = "BODY_UNAVAILABLE"; continue; }
                    }
                    if (!rawArticles.contains(articleId) && rawArticles.size() >= 8) { source.status = "BUDGET_NOT_SEARCHED"; continue; }
                    Map<String, String> paragraphs = splitter.split(article.getBody());
                    String bodyHash = ObservationStore.bodyHash(article.getBody());
                    List<Map.Entry<String, String>> ranked = splitter.questionEvidence(paragraphs, q.articleTerms());
                    for (var paragraph : ranked) {
                        if (source.evidence.values().stream().anyMatch(e -> e.observation.articleId().equals(articleId)
                                && e.observation.snapshot().path("bodyHash").asText().equals(bodyHash)
                                && e.observation.snapshot().path("spans").path(paragraph.getKey()).asText().equals(paragraph.getValue()))) continue;
                        String id = articleId + ":" + paragraph.getKey();
                        String alias = additions.entrySet().stream().filter(e -> e.getValue().observation.id().equals(id))
                                .map(Map.Entry::getKey).findFirst().orElse(null);
                        if (alias == null && rawChars + paragraph.getValue().length() <= 8000) {
                            var draft = new ObservationStore.Draft(paragraph.getValue(), List.of(paragraph.getKey()), false, "");
                            Observation original = observations.fromDraft(article, 0, draft);
                            Observation passage = new Observation(id, articleId, 0, original.text(), original.spanIds(),
                                    original.publishedAt(), null, false, original.snapshot());
                            alias = "R%03d".formatted(additions.size() + 1);
                            additions.put(alias, new Evidence(passage, passage.publishedAt().isBefore(windowStart), null, 0));
                            rawChars += paragraph.getValue().length(); rawArticles.add(articleId);
                        }
                        if (alias != null) source.evidence.put(alias, additions.get(alias));
                    }
                }
            }
            search.put("status", source.status);
            search.set("evidence", json.valueToTree(source.evidence.entrySet().stream().map(e -> Map.of("id", e.getKey(),
                    "articleId", e.getValue().observation.articleId(), "text", e.getValue().observation.text())).toList()));
            search.set("principles", json.valueToTree(source.principles));
        }
        return result;
    }

    static final class QuestionContext {
        final Map<String, Evidence> evidence = new LinkedHashMap<>();
        final Map<String, PrincipleChoice> principles = new LinkedHashMap<>();
        String status = "CURRENT_EVIDENCE_ONLY";
    }

    private static String windowText(List<ArticleEntity> articles) {
        return articles.stream().map(a -> a.getTitle() + " " + a.getSummary()).collect(java.util.stream.Collectors.joining("\n"));
    }

    private void ensureCost(UsageTracker usage, String input, boolean terra, int outputTokens, double reserved) {
        double projected = usage.costUsd() + ((estimateTokens(input) + 2500d) * (terra ? 2 : .2)
                + outputTokens * (terra ? 12 : 1.2)) / 1_000_000d + reserved;
        if (projected > appProperties.budget().dailyCostUsd())
            throw new IllegalStateException("daily cost ceiling would be exceeded: projectedUsd=" + projected);
    }

    private void ensureEmbeddingCost(UsageTracker usage, List<String> input, double reserved) {
        double projected = usage.costUsd() + estimateTokens(String.join("\n", input)) * .13 / 1_000_000d + reserved;
        if (projected > appProperties.budget().dailyCostUsd()) throw new IllegalStateException("embedding cost ceiling exceeded");
    }

    static List<WriterBatch> writerBatches(List<PlanFlow> plan, Context context, Map<String, QuestionContext> questions, int maxInputTokens) {
        List<WriterBatch> result = new ArrayList<>();
        for (int start = 0; start < plan.size();) {
            int end = start + 1;
            String input = writerInput(plan.subList(start, end), context, questions, start);
            ensureInputBudget("single-flow writer", input, maxInputTokens);
            while (end < plan.size()) {
                String expanded = writerInput(plan.subList(start, end + 1), context, questions, start);
                if (estimateTokens(expanded) > maxInputTokens) break;
                input = expanded; end++;
            }
            int weight = plan.subList(start, end).stream().mapToInt(flow -> flow.questions().size() + 2).sum();
            List<WritingScope> scopes = new ArrayList<>();
            for (int index = start; index < end; index++) {
                String flowId = "F%02d".formatted(index + 1); List<AnswerScope> answers = new ArrayList<>();
                for (int q = 0; q < plan.get(index).questions().size(); q++) {
                    String qid = "Q%02d".formatted(q + 1); QuestionContext allowed = questions.get(flowId + ":" + qid);
                    answers.add(new AnswerScope(qid, List.copyOf(allowed.evidence.keySet()), List.copyOf(allowed.principles.keySet())));
                }
                scopes.add(new WritingScope(flowId, List.copyOf(answers)));
            }
            result.add(new WriterBatch(input, end - start, weight, List.copyOf(scopes))); start = end;
        }
        return result;
    }

    private static String writerInput(List<PlanFlow> plan, Context context, Map<String, QuestionContext> allQuestions, int offset) {
        Map<String, QuestionContext> questions = new LinkedHashMap<>();
        for (int i = 0; i < plan.size(); i++) {
            String prefix = "F%02d:".formatted(offset + i + 1);
            allQuestions.forEach((id, q) -> { if (id.startsWith(prefix)) questions.put(id, q); });
        }
        Map<String, Evidence> evidence = new LinkedHashMap<>();
        Map<String, PrincipleChoice> principleCatalog = new LinkedHashMap<>();
        for (PlanFlow flow : plan) {
            QuestionContext base = flowSources(flow, context);
            evidence.putAll(base.evidence); principleCatalog.putAll(base.principles);
        }
        questions.values().forEach(q -> { evidence.putAll(q.evidence); principleCatalog.putAll(q.principles); });
        StringBuilder value = new StringBuilder(evidenceCatalog(evidence));
        value.append("<principleCatalog>\n");
        principleCatalog.forEach((id, item) -> appendPrinciple(value, id, item));
        value.append("</principleCatalog>\n");
        for (int index = 0; index < plan.size(); index++) {
            String flowId = "F%02d".formatted(offset + index + 1);
            PlanFlow flow = plan.get(index);
            value.append('<').append(flowId).append(">\nconnection\t").append(flow.connection())
                    .append("\nflowEvidence\t").append(String.join(",", flow.observationIds()))
                    .append("\nflowPrinciples\t").append(String.join(",", flow.principleIds())).append("\n");
            for (int q = 0; q < flow.questions().size(); q++) {
                String qid = "Q%02d".formatted(q + 1);
                QuestionContext sources = questions.get(flowId + ":" + qid);
                value.append('<').append(qid).append(">\nquestion\t").append(flow.questions().get(q).question())
                        .append("\nsearchStatus\t").append(sources.status)
                        .append("\nquestionEvidence\t").append(String.join(",", sources.evidence.keySet()))
                        .append("\nquestionPrinciples\t").append(String.join(",", sources.principles.keySet()))
                        .append("\n</").append(qid).append(">\n");
            }
            value.append("</").append(flowId).append(">\n");
        }
        return value.toString();
    }

    static String evidenceCatalog(Map<String, Evidence> evidence) {
        StringBuilder value = new StringBuilder("<evidenceCatalog>\n"), sources = new StringBuilder("<sourceCatalog>\n");
        Map<String, String> aliases = new LinkedHashMap<>();
        evidence.forEach((id, item) -> {
            Observation o = item.observation;
            value.append(id).append('\t').append(o.publishedAt()).append('\t').append(EconomicFlowLlm.clean(o.text()));
            List<String> refs = new ArrayList<>();
            if (o.number() != 0 && o.snapshot() != null) o.snapshot().path("spans").fields().forEachRemaining(span -> {
                String key = o.articleId() + ":" + o.snapshot().path("bodyHash").asText() + ":" + span.getKey();
                String alias = aliases.get(key);
                if (alias == null) {
                    alias = "S%03d".formatted(aliases.size() + 1); aliases.put(key, alias);
                    sources.append(alias).append('\t').append(EconomicFlowLlm.clean(span.getValue().asText())).append('\n');
                }
                refs.add(alias);
            });
            value.append("\tsources=").append(String.join(",", refs)).append('\n');
        });
        return value.append("</evidenceCatalog>\n").append(sources).append("</sourceCatalog>\n").toString();
    }

    private static void appendPrinciple(StringBuilder value, String id, PrincipleChoice choice) {
        value.append(id).append('\t').append(EconomicFlowLlm.clean(choice.principle.title())).append('\t')
                .append(EconomicFlowLlm.clean(choice.principle.text())).append('\n');
    }

    static List<PlanFlow> includeQuestionEvidence(List<PlanFlow> plan, Context context) {
        return plan.stream().map(flow -> {
            List<String> ids = new ArrayList<>(flow.observationIds());
            flow.questions().stream().flatMap(q -> q.observationIds().stream())
                    .filter(context.current::containsKey).forEach(id -> { if (!ids.contains(id)) ids.add(id); });
            return new PlanFlow(List.copyOf(ids), flow.principleIds(), flow.connection(), flow.questions());
        }).toList();
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
            Set<String> seenQuestions = new HashSet<>();
            for (Question q : flow.questions()) {
                if (q.question().isBlank() || q.reason().isBlank() || !seenQuestions.add(q.question())) errors.add("invalid question text");
                if (q.observationIds().isEmpty() || !usedCurrent.containsAll(q.observationIds())) errors.add("question outside flow evidence");
                if (q.articleTerms().size() > 4 || q.articleTerms().stream().anyMatch(String::isBlank)) errors.add("invalid search terms");
                if (!q.articleQuery().isBlank() && q.articleTerms().isEmpty()) errors.add("missing article search terms");
            }
        }
        Set<String> required = new HashSet<>();
        context.current.values().forEach(item -> required.add(item.observation.articleId()));
        if (!coveredArticles.equals(required)) errors.add("article coverage mismatch");
        return errors;
    }

    static List<String> validateWriting(Writing writing, List<PlanFlow> plan, Context context,
                                        Map<String, QuestionContext> questions) {
        List<String> errors = new ArrayList<>();
        if (!writing.conflicts().isEmpty()) errors.add("source conflicts: " + writing.conflicts());
        if (writing.flows().size() != plan.size()) errors.add("flow count mismatch");
        for (int index = 0; index < Math.min(writing.flows().size(), plan.size()); index++) {
            WrittenFlow flow = writing.flows().get(index);
            PlanFlow planned = plan.get(index);
            String fid = "F%02d".formatted(index + 1);
            if (!flow.flowId().equals(fid)) errors.add("flow order mismatch");
            if (flow.title().isBlank() || flow.explanation().isBlank()) errors.add(fid + ": empty text");
            QuestionContext base = flowSources(planned, context);
            checkNumbers(fid, flow.title() + " " + flow.explanation(), base, errors);
            if (flow.questions().size() != planned.questions().size()) errors.add(fid + ": question count mismatch");
            for (int q = 0; q < Math.min(flow.questions().size(), planned.questions().size()); q++) {
                String qid = "Q%02d".formatted(q + 1);
                var answer = flow.questions().get(q);
                QuestionContext source = questions.get(fid + ":" + qid);
                if (!answer.questionId().equals(qid) || answer.answer().isBlank()) errors.add(fid + ": invalid answer order/text");
                if (!source.evidence.keySet().containsAll(answer.evidenceIds())) errors.add(fid + ": unknown answer evidence");
                if (!source.principles.keySet().containsAll(answer.principleIds())) errors.add(fid + ": unknown answer principle");
                QuestionContext cited = new QuestionContext();
                answer.evidenceIds().stream().filter(source.evidence::containsKey).forEach(id -> cited.evidence.put(id, source.evidence.get(id)));
                answer.principleIds().stream().filter(source.principles::containsKey).forEach(id -> cited.principles.put(id, source.principles.get(id)));
                checkNumbers(fid + ":" + qid, answer.answer(), cited, errors);
            }
        }
        return errors;
    }

    private static void checkNumbers(String id, String text, QuestionContext source, List<String> errors) {
        StringBuilder allowed = new StringBuilder();
        for (Evidence evidence : source.evidence.values()) {
            Observation o = evidence.observation;
            allowed.append(o.text()).append(' ').append(o.publishedAt()).append(' ');
            if (o.snapshot() != null) o.snapshot().path("spans").forEach(span -> allowed.append(span.asText()).append(' '));
        }
        source.principles.values().forEach(p -> allowed.append(p.principle.title()).append(' ').append(p.principle.text()).append(' '));
        Set<String> extra = numbers(text); extra.removeAll(numbers(allowed.toString()));
        if (!extra.isEmpty()) errors.add(id + ": numbers outside evidence=" + extra);
    }

    static Writing completeNumberCitations(Writing writing, Map<String, QuestionContext> questions) {
        List<WrittenFlow> flows = new ArrayList<>();
        for (int f = 0; f < writing.flows().size(); f++) {
            WrittenFlow flow = writing.flows().get(f);
            List<Answer> answers = new ArrayList<>();
            for (int q = 0; q < flow.questions().size(); q++) {
                Answer answer = flow.questions().get(q);
                QuestionContext allowed = questions.get("F%02d:Q%02d".formatted(f + 1, q + 1));
                LinkedHashSet<String> evidenceIds = new LinkedHashSet<>(answer.evidenceIds());
                LinkedHashSet<String> principleIds = new LinkedHashSet<>(answer.principleIds());
                Set<String> missing = numbers(answer.answer());
                evidenceIds.stream().filter(allowed.evidence::containsKey)
                        .forEach(id -> missing.removeAll(numbers(evidenceText(allowed.evidence.get(id)))));
                principleIds.stream().filter(allowed.principles::containsKey)
                        .forEach(id -> missing.removeAll(numbers(principleText(allowed.principles.get(id)))));
                allowed.evidence.forEach((id, source) -> {
                    if (!java.util.Collections.disjoint(missing, numbers(evidenceText(source)))) {
                        evidenceIds.add(id); missing.removeAll(numbers(evidenceText(source)));
                    }
                });
                allowed.principles.forEach((id, source) -> {
                    if (!java.util.Collections.disjoint(missing, numbers(principleText(source)))) {
                        principleIds.add(id); missing.removeAll(numbers(principleText(source)));
                    }
                });
                answers.add(new Answer(answer.questionId(), answer.answer(), List.copyOf(evidenceIds), List.copyOf(principleIds)));
            }
            flows.add(new WrittenFlow(flow.flowId(), flow.title(), flow.explanation(), List.copyOf(answers)));
        }
        return new Writing(List.copyOf(flows), writing.conflicts());
    }

    private static String evidenceText(Evidence evidence) {
        StringBuilder value = new StringBuilder(evidence.observation.text());
        if (evidence.observation.snapshot() != null) evidence.observation.snapshot().path("spans")
                .forEach(span -> value.append(' ').append(span.asText()));
        return value.toString();
    }

    private static String principleText(PrincipleChoice choice) {
        return choice.principle.title() + " " + choice.principle.text();
    }

    private static QuestionContext flowSources(PlanFlow flow, Context context) {
        QuestionContext result = new QuestionContext();
        flow.observationIds().forEach(id -> result.evidence.put(id, context.aliases.get(id)));
        flow.principleIds().forEach(id -> result.principles.put(id, context.principleAliases.get(id)));
        return result;
    }

    static QuestionContext questionSources(PlanFlow flow, Question question, Context context) {
        QuestionContext result = new QuestionContext();
        question.observationIds().forEach(id -> result.evidence.put(id, context.current.get(id)));
        flow.observationIds().forEach(id -> {
            Evidence evidence = context.aliases.get(id);
            if (evidence.historical && question.observationIds().contains(evidence.currentAlias))
                result.evidence.put(id, evidence);
        });
        flow.principleIds().forEach(id -> result.principles.put(id, context.principleAliases.get(id)));
        return result;
    }

    private ObjectNode publicResult(String runId, LocalDate date, List<PlanFlow> plan,
            List<WrittenFlow> writing, Context context, Map<String, QuestionContext> questions) {
        ObjectNode result = emptyResult(date);
        ArrayNode flows = (ArrayNode) result.get("flows");
        for (int index = 0; index < writing.size(); index++) {
            WrittenFlow written = writing.get(index);
            PlanFlow planned = plan.get(index);
            ObjectNode flow = flows.addObject();
            flow.put("id", runId + ":F" + (index + 1));
            flow.put("title", written.title()); flow.put("explanation", written.explanation());
            QuestionContext base = flowSources(planned, context);
            flow.set("sources", publicSources(base.evidence));
            flow.set("principles", publicPrinciples(base.principles));
            ArrayNode qa = flow.putArray("questions");
            for (int q = 0; q < written.questions().size(); q++) {
                var answer = written.questions().get(q);
                QuestionContext source = questions.get("F%02d:Q%02d".formatted(index + 1, q + 1));
                ObjectNode item = qa.addObject();
                item.put("id", answer.questionId()); item.put("question", planned.questions().get(q).question());
                item.put("answer", answer.answer());
                Map<String, Evidence> cited = new LinkedHashMap<>();
                answer.evidenceIds().forEach(id -> cited.put(id, source.evidence.get(id)));
                item.set("sources", publicSources(cited));
                Map<String, PrincipleChoice> citedPrinciples = new LinkedHashMap<>();
                answer.principleIds().forEach(id -> citedPrinciples.put(id, source.principles.get(id)));
                item.set("principles", publicPrinciples(citedPrinciples));
            }
        }
        return result;
    }

    private ArrayNode publicSources(Map<String, Evidence> evidence) {
        ArrayNode result = json.createArrayNode();
        Map<String, ObjectNode> sources = new LinkedHashMap<>();
        for (Evidence item : evidence.values()) {
            Observation o = item.observation;
            JsonNode snapshot = o.snapshot();
            if (snapshot == null) throw new IllegalStateException("missing source snapshot");
            String key = o.articleId() + ":" + snapshot.path("bodyHash").asText();
            ObjectNode source = sources.get(key);
            if (source == null) {
                source = result.addObject(); sources.put(key, source);
                source.put("articleId", o.articleId());
                for (String field : List.of("title", "url", "source", "publishedAt")) source.set(field, snapshot.path(field));
                source.putArray("observations");
            }
            ObjectNode observation = ((ArrayNode) source.get("observations")).addObject();
            observation.put("text", o.text());
            ArrayNode spans = observation.putArray("evidence");
            snapshot.path("spans").fields().forEachRemaining(span -> spans.addObject().put("spanId", span.getKey()).put("text", span.getValue().asText()));
        }
        return result;
    }

    private ArrayNode publicPrinciples(Map<String, PrincipleChoice> principles) {
        ArrayNode result = json.createArrayNode();
        for (var choice : principles.values()) {
            Principle p = choice.principle;
            result.addObject().put("chunkId", p.chunkId()).put("source", p.source()).put("section", p.title());
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
        if (usage.costUsd() > appProperties.budget().dailyCostUsd())
            throw new IllegalStateException("daily cost ceiling exceeded by actual usage");
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
    static record WriterBatch(String input, int flowCount, int weight, List<WritingScope> scopes) {}
    static record Context(Map<String, Evidence> current, Map<String, Evidence> aliases,
                          List<HistoryChoice> history, List<Relation> relations,
                          Map<String, PrincipleChoice> principleAliases) {}

    private final class Trace {
        int windowArticleCount;
        int plannerEvidenceCharsBudget;
        final Map<String, Long> articleCountsByHour = new LinkedHashMap<>();
        final List<String> questionEvidenceAdded = new ArrayList<>();
        final List<String> prefilteredArticleIds = new ArrayList<>(), selectedArticleIds = new ArrayList<>(),
                observationIds = new ArrayList<>(), historyCandidateIds = new ArrayList<>(), historyCandidatePairs = new ArrayList<>(), principleChunkIds = new ArrayList<>(),
                usedHistoryObservationIds = new ArrayList<>(), usedPrincipleIds = new ArrayList<>();
        final List<String> memoryArticleIds = new ArrayList<>();
        final ArrayNode memoryDecisions = json.createArrayNode(), questionSearches = json.createArrayNode();
        JsonNode selection, writing;
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
                item.set("questions", json.valueToTree(flow.questions()));
            }
        }
        ObjectNode json(ObjectMapper json) {
            ObjectNode node = json.createObjectNode(); node.put("windowArticleCount", windowArticleCount);
            node.put("plannerEvidenceCharsBudget", plannerEvidenceCharsBudget);
            node.set("articleCountsByHour", json.valueToTree(articleCountsByHour));
            add(node, "questionEvidenceAdded", questionEvidenceAdded);
            add(node, "prefilteredArticleIds", prefilteredArticleIds); add(node, "selectedArticleIds", selectedArticleIds);
            add(node, "observationIds", observationIds); add(node, "historyCandidateIds", historyCandidateIds);
            add(node, "historyCandidatePairs", historyCandidatePairs);
            add(node, "principleChunkIds", principleChunkIds); add(node, "usedHistoryObservationIds", usedHistoryObservationIds);
            add(node, "usedPrincipleIds", usedPrincipleIds);
            if (plan != null) node.set("plan", plan);
            if (selection != null) node.set("selection", selection);
            if (writing != null) node.set("writing", writing);
            add(node, "memoryArticleIds", memoryArticleIds);
            node.set("memoryDecisions", memoryDecisions); node.set("questionSearches", questionSearches);
            return node;
        }
        private static void add(ObjectNode node, String name, List<String> values) { ArrayNode array = node.putArray(name); values.forEach(array::add); }
    }

    private static final class UsageTracker {
        final Map<String, StageUsage> stages = new LinkedHashMap<>();
        int extractionInput;
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

package com.economicbriefing.analyzer.openai;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.economicbriefing.analyzer.dto.ArticleValidationResult;
import com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse;
import com.economicbriefing.analyzer.openai.dto.AiResponse;
import com.economicbriefing.analyzer.openai.prompt.ArticleAnalyzerPromptBuilder;
import com.economicbriefing.analyzer.openai.prompt.ArticleValidatorPromptBuilder;
import com.economicbriefing.analyzer.openai.prompt.RetrievalRouterPromptBuilder;
import com.economicbriefing.analyzer.openai.prompt.RelationCandidateExtractorPromptBuilder;
import com.economicbriefing.config.AppProperties;
import com.economicbriefing.config.OpenAiProperties;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArticleAnalyzerTest {

    @Test
    void shouldAcceptEmptyNumericFlowAndRequireClaimEndpoints() throws Exception {
        Article article = new Article("article-1", "국고채 등락", "", "연합뉴스",
                ArticleSourceType.NEWS_MEDIA, OffsetDateTime.now(), OffsetDateTime.now(),
                "https://example.com/1", List.of(), "ko", "국고채 3년물은 3.3bp 상승했다.");
        String emptyFlow = """
                {"articles":[{"articleId":"article-1","issues":[{"name":"등락","mainFacts":[],
                "changes":[],"relationCandidates":[],"statements":[],"keyTerms":[]}],
                "flowClaims":[]}]}
                """;
        var bundle = OpenAiNewsAnalyzer.parseDraftBundle(new ObjectMapper(), emptyFlow, List.of(article));
        assertTrue(bundle.economicFlows().getFirst().flow().nodes().isEmpty());
        assertTrue(bundle.economicFlows().getFirst().flow().flowClaims().isEmpty());

        String invalid = emptyFlow.replace("\"flowClaims\":[]",
                "\"flowClaims\":[{\"from\":\"국고채 금리\",\"to\":\"국고채 금리\",\"relationType\":\"CAUSE\"}]");
        assertThrows(IllegalArgumentException.class,
                () -> OpenAiNewsAnalyzer.parseDraftBundle(new ObjectMapper(), invalid, List.of(article)));
    }

    @Test
    void shouldCreateDeterministicFlowNodesFromClaimText() throws Exception {
        Article article = new Article("article-1", "크레딧", "", "연합뉴스",
                ArticleSourceType.NEWS_MEDIA, OffsetDateTime.now(), OffsetDateTime.now(),
                "https://example.com/1", List.of(), "ko", "회사채 금리가 높아 은행대출을 선호한다.");
        String json = """
                {"articles":[{"articleId":"article-1","issues":[{"name":"조달","mainFacts":[],
                "changes":[],"relationCandidates":[],"statements":[],"keyTerms":[]}],
                "flowClaims":[{"from":"회사채의 높은 조달비용","to":"은행대출 조달 선호","relationType":"CAUSE"}]}]}
                """;

        var first = OpenAiNewsAnalyzer.parseDraftBundle(new ObjectMapper(), json, List.of(article));
        var second = OpenAiNewsAnalyzer.parseDraftBundle(new ObjectMapper(), json, List.of(article));

        assertEquals(2, first.economicFlows().getFirst().flow().nodes().size());
        assertEquals(first.economicFlows().getFirst().flow(), second.economicFlows().getFirst().flow());
        assertEquals("회사채의 높은 조달비용",
                first.economicFlows().getFirst().flow().flowClaims().getFirst().from());
        assertEquals("은행대출 조달 선호",
                first.economicFlows().getFirst().flow().flowClaims().getFirst().to());
    }

    @Test
    void shouldRetryAtomicityViolationWithOffendingValue() {
        Article article = new Article(
                "article-1", "금리 전망", "", "연합뉴스",
                ArticleSourceType.NEWS_MEDIA, OffsetDateTime.now(), OffsetDateTime.now(),
                "https://example.com/1", List.of(NewsCategory.INTEREST_RATE), "ko",
                "수출 호조에 따른 성장세를 고려해 금리 인상 전망이 나왔다.");
        String validDraft = """
                {"articles":[{"articleId":"article-1","issues":[{
                  "name":"금리 전망","mainFacts":[],"changes":[],
                  "relationCandidates":[{
                    "evidence":"수출 호조에 따른 성장세를 고려해 금리 인상 전망이 나왔다.",
                    "atomicRelations":[
                      {"from":"수출 호조","to":"성장세","relationType":"CAUSE_OR_RESULT","evidenceType":"PREDICTION","speaker":null},
                      {"from":"성장세","to":"금리 인상 전망","relationType":"CAUSE_OR_RESULT","evidenceType":"PREDICTION","speaker":null}
                    ]
                  }],"statements":[],"keyTerms":[]
                }],"eventCandidates":[]}]}
                """;
        String invalidDraft = validDraft.replace(
                "\"from\":\"수출 호조\"", "\"from\":\"수출 호조에 따른 성장세\"");
        ObjectMapper mapper = new ObjectMapper();
        AtomicInteger attempts = new AtomicInteger();
        List<String> prompts = new ArrayList<>();
        OpenAiClient client = new OpenAiClient(
                new OpenAiProperties("test", "gpt-4o", 0, Duration.ofSeconds(1), 1), mapper) {
            @Override
            String completeWithSchema(
                    String systemPrompt, String userPrompt, double temperature,
                    String schemaName, String schema) {
                prompts.add(userPrompt);
                return attempts.getAndIncrement() == 0 ? invalidDraft : validDraft;
            }
        };

        var response = new OpenAiNewsAnalyzer(client, mapper, null, null, null)
                .analyzeArticleDraftWithRetry(
                        "base prompt", List.of(article),
                        new AppProperties.RetryProperties(2, Duration.ZERO, Duration.ZERO));

        assertEquals(2, attempts.get());
        assertEquals(2, response.articles().get(0).issues().get(0).relations().size());
        assertTrue(prompts.get(1).contains("Previous draft violated the atomic relation contract."));
        assertTrue(prompts.get(1).contains("Invalid from:"));
        assertTrue(prompts.get(1).contains("수출 호조에 따른 성장세"));
    }

    @Test
    void shouldRetryWhenRelationEvidenceIsParaphrased() {
        Article article = new Article(
                "article-1", "금리 전망", "", "연합뉴스",
                ArticleSourceType.NEWS_MEDIA, OffsetDateTime.now(), OffsetDateTime.now(),
                "https://example.com/1", List.of(NewsCategory.INTEREST_RATE), "ko",
                "기준금리 인상 가능성이 부각되며 증시를 짓눌렀다.");
        String validDraft = """
                {"articles":[{"articleId":"article-1","issues":[{
                  "name":"증시 하락","mainFacts":[],"changes":[],
                  "relationCandidates":[{"evidence":"기준금리 인상 가능성이 부각되며 증시를 짓눌렀다.",
                    "atomicRelations":[{"from":"기준금리 인상 가능성","to":"증시 하방 압력",
                    "relationType":"CAUSE_OR_RESULT","evidenceType":"FACT","speaker":null}]}],
                  "statements":[],"keyTerms":[]}],"eventCandidates":[],"flowClaims":[]}]}
                """;
        String invalidDraft = validDraft.replace(
                "기준금리 인상 가능성이 부각되며 증시를 짓눌렀다.",
                "기준금리 인상 가능성이 증시에 영향을 미쳤다.");
        ObjectMapper mapper = new ObjectMapper(); AtomicInteger attempts = new AtomicInteger();
        List<String> prompts = new ArrayList<>();
        OpenAiClient client = new OpenAiClient(
                new OpenAiProperties("test", "gpt-4o", 0, Duration.ofSeconds(1), 1), mapper) {
            @Override String completeWithSchema(String systemPrompt, String userPrompt, double temperature,
                    String schemaName, String schema) {
                prompts.add(userPrompt);
                return attempts.getAndIncrement() == 0 ? invalidDraft : validDraft;
            }
        };

        var response = new OpenAiNewsAnalyzer(client, mapper, null, null, null).analyzeArticleDraftWithRetry(
                "base prompt", List.of(article),
                new AppProperties.RetryProperties(2, Duration.ZERO, Duration.ZERO));

        assertEquals(2, attempts.get());
        assertEquals("기준금리 인상 가능성이 부각되며 증시를 짓눌렀다.",
                response.articles().getFirst().issues().getFirst().relations().getFirst().articleExplanation());
        assertTrue(prompts.get(1).contains("relation evidence contract"));
        assertTrue(prompts.get(1).contains("기준금리 인상 가능성이 증시에 영향을 미쳤다."));
    }

    @Test
    void shouldRetryUnknownValidatorIssueWithAllowedReferences() {
        Article article = new Article("article-1", "금리", "", "연합뉴스",
                ArticleSourceType.NEWS_MEDIA, OffsetDateTime.now(), OffsetDateTime.now(),
                "https://example.com/validator", List.of(NewsCategory.INTEREST_RATE), "ko", "금리가 오른다.");
        var baseline = new ArticleAnalysisResponse(List.of(new ArticleAnalysisResponse.ArticleAnalysis(
                "article-1", List.of(new ArticleAnalysisResponse.Issue(
                        "금리", List.of("금리가 오른다."), List.of(), List.of(), List.of(), List.of())))));
        String invalid = """
                {"articles":[{"articleId":"article-1","findings":[{"type":"MISSING",
                "issue":"없는 쟁점","targetType":"MAIN_FACT","targetReference":"issues[1]",
                "description":"누락","currentValue":null,"suggestedValue":"금리 상승",
                "evidence":"금리가 오른다."}]}]}
                """;
        String valid = """
                {"articles":[{"articleId":"article-1","findings":[{"type":"MISSING",
                "issue":"금리","targetType":"MAIN_FACT","targetReference":"issues[0]",
                "description":"누락","currentValue":null,"suggestedValue":"금리 상승",
                "evidence":"금리가 오른다."}]}]}
                """;
        ObjectMapper mapper = new ObjectMapper();
        AtomicInteger attempts = new AtomicInteger();
        List<String> prompts = new ArrayList<>();
        OpenAiClient client = new OpenAiClient(
                new OpenAiProperties("test", "gpt-4o", 0, Duration.ofSeconds(1), 1), mapper) {
            @Override public String complete(String systemPrompt, String userPrompt, double temperature) {
                prompts.add(userPrompt);
                return attempts.getAndIncrement() == 0 ? invalid : valid;
            }
        };
        AppProperties app = new AppProperties(true, null,
                new AppProperties.RetryProperties(2, Duration.ZERO, Duration.ZERO),
                null, null, null, null, null);

        var result = new OpenAiNewsAnalyzer(client, mapper, null, app, null).validateWithRetry(
                ArticleValidatorPromptBuilder.MISSING_REVIEW_SYSTEM_PROMPT, "base prompt",
                List.of(article), baseline, Set.of(ArticleValidationResult.FindingType.MISSING));

        assertEquals(2, attempts.get());
        assertEquals("금리", result.articles().getFirst().findings().getFirst().issue());
        assertTrue(prompts.get(1).contains("INVALID_VALIDATOR_REFERENCE"));
        assertTrue(prompts.get(1).contains("Returned issue: 없는 쟁점"));
        assertTrue(prompts.get(1).contains("issues[0]=금리"));
    }

    @Test
    void shouldRemoveUngroundedMechanismWhenPrincipleContextIsEmpty() {
        var response = new AiResponse(List.of(), List.of(new AiResponse.AiAnalyzedNews(
                "article-1", "제목", "exchange_rate", 1, List.of(), "사실", "원인",
                "모델이 만든 공급 수요 원리", "모델이 만든 수입물가 영향", List.of(), "confirmed")), List.of());
        var analysis = new ArticleAnalysisResponse(List.of(new ArticleAnalysisResponse.ArticleAnalysis(
                "article-1", List.of(new ArticleAnalysisResponse.Issue("환율", List.of(), List.of(),
                        List.of(new ArticleAnalysisResponse.Relation("네고 물량", "환율 하락",
                                ArticleAnalysisResponse.RelationType.CAUSE_OR_RESULT, "기사 근거",
                                ArticleAnalysisResponse.StatementType.INTERPRETATION, null)),
                        List.of(), List.of())))));

        var bounded = OpenAiNewsAnalyzer.applyPrincipleBoundary(response, analysis, false).news().getFirst();

        assertEquals("기사에서는 '네고 물량 → 환율 하락' 관계가 제시됐습니다.", bounded.beginnerExplanation());
        assertEquals("기사에서 확인된 영향 없음", bounded.economicImpact());
        assertSame(response, OpenAiNewsAnalyzer.applyPrincipleBoundary(response, analysis, true));
    }

    @Test
    void shouldValidateAndFlattenAtomicRelationDraft() throws Exception {
        Article article = new Article(
                "article-1", "금리 전망", "", "연합뉴스",
                ArticleSourceType.NEWS_MEDIA, OffsetDateTime.now(), OffsetDateTime.now(),
                "https://example.com/1", List.of(NewsCategory.INTEREST_RATE), "ko",
                "수출 호조에 따른 성장세를 고려해 금리 인상 전망이 나왔다.");
        String draft = """
                {"articles":[{"articleId":"article-1","issues":[{
                  "name":"금리 전망","mainFacts":[],"changes":[],
                  "relationCandidates":[{
                    "evidence":"수출 호조에 따른 성장세를 고려해 금리 인상 전망이 나왔다.",
                    "atomicRelations":[
                      {"from":"수출 호조","to":"성장세","relationType":"CAUSE_OR_RESULT","evidenceType":"PREDICTION","speaker":null},
                      {"from":"수출 호조","to":"성장세","relationType":"CAUSE_OR_RESULT","evidenceType":"PREDICTION","speaker":null},
                      {"from":"성장세","to":"금리 인상 전망","relationType":"CAUSE_OR_RESULT","evidenceType":"PREDICTION","speaker":null}
                    ]
                  }],"statements":[],"keyTerms":[]
                }],"eventCandidates":[]}]}
                """;

        var response = OpenAiNewsAnalyzer.parseAndFlattenDraft(
                new ObjectMapper(), draft, List.of(article));
        var relations = response.articles().get(0).issues().get(0).relations();
        assertEquals(2, relations.size());
        assertEquals("수출 호조", relations.get(0).from());
        assertEquals("성장세", relations.get(1).from());
        assertEquals("수출 호조에 따른 성장세를 고려해 금리 인상 전망이 나왔다.",
                relations.get(0).articleExplanation());
        assertThrows(IllegalArgumentException.class, () -> OpenAiNewsAnalyzer.parseAndFlattenDraft(
                new ObjectMapper(), draft.replace("수출 호조에 따른", "원문에 없는"), List.of(article)));

        var schema = new ObjectMapper().readTree(ArticleAnalyzerPromptBuilder.JSON_SCHEMA);
        assertFalse(schema.path("additionalProperties").asBoolean());
        assertTrue(ArticleAnalyzerPromptBuilder.JSON_SCHEMA.contains("relationCandidates"));
        assertTrue(ArticleAnalyzerPromptBuilder.JSON_SCHEMA.contains("atomicRelations"));
        assertFalse(ArticleAnalyzerPromptBuilder.JSON_SCHEMA.contains("flowNodes"));
        assertTrue(ArticleAnalyzerPromptBuilder.JSON_SCHEMA.contains("flowClaims"));
        assertTrue(ArticleAnalyzerPromptBuilder.JSON_SCHEMA.contains("RELATED_BUT_DISTINCT") == false);
        var boundedSchema = new ObjectMapper().readTree(ArticleAnalyzerPromptBuilder.schemaForArticleCount(2));
        assertEquals(2, boundedSchema.path("properties").path("articles").path("minItems").asInt());
        assertEquals(2, boundedSchema.path("properties").path("articles").path("maxItems").asInt());
    }

    @Test
    void shouldIgnoreWhitespaceAndQuoteStyleWhenMatchingRelationEvidence() throws Exception {
        Article article = new Article(
                "article-1", "금리", "", "연합뉴스", ArticleSourceType.NEWS_MEDIA,
                OffsetDateTime.now(), OffsetDateTime.now(), "https://example.com/1",
                List.of(NewsCategory.INTEREST_RATE), "ko", "그는 “금리가  오른다”고 말했다.");
        String draft = """
                {"articles":[{"articleId":"article-1","issues":[{
                  "name":"금리","mainFacts":[],"changes":[],
                  "relationCandidates":[{"evidence":"그는 '금리가 오른다'고 말했다.",
                    "atomicRelations":[{"from":"금리","to":"상승","relationType":"CLAIMED_EFFECT","evidenceType":"CLAIM","speaker":"그"}]}],
                  "statements":[],"keyTerms":[]}],"eventCandidates":[]}]}
                """;

        assertEquals(1, OpenAiNewsAnalyzer.parseAndFlattenDraft(
                new ObjectMapper(), draft, List.of(article))
                .articles().getFirst().issues().getFirst().relations().size());
    }

    @Test
    void shouldParseMultipleEventCandidatesWithoutInventingPreviousState() throws Exception {
        Article article = new Article(
                "article-1", "정책 변경", "", "연합뉴스", ArticleSourceType.NEWS_MEDIA,
                OffsetDateTime.now(), OffsetDateTime.now(), "https://example.com/1",
                List.of(NewsCategory.LOAN), "ko",
                "주담대 한도를 5억원에서 4억원으로 낮췄다. 지방 한도는 3억원으로 낮췄다. "
                        + "추가 규제는 검토 중이다. 성장률 전망은 1.8%에서 1.5%로 낮췄다.");
        String json = """
                {"articles":[{"articleId":"article-1","issues":[{"name":"정책","mainFacts":[],"changes":[],"relationCandidates":[],"statements":[],"keyTerms":[]}],
                "eventCandidates":[
                  {"eventType":"POLICY_CHANGE","title":"주담대 한도 축소","subject":"주담대 한도","subjectKey":"MORTGAGE_LIMIT","eventDate":"2026-08-24","previousState":"5억원","newState":"4억원","status":"CONFIRMED","region":"수도권","topicKeys":["MORTGAGE"],"newTopicCandidates":[],"evidenceText":"주담대 한도를 5억원에서 4억원으로 낮췄다."},
                  {"eventType":"POLICY_CHANGE","title":"지방 한도 축소","subject":"지방 한도","subjectKey":"REGIONAL_LIMIT","eventDate":"2026-08-24","previousState":null,"newState":"3억원","status":"CONFIRMED","region":"지방","topicKeys":["MORTGAGE"],"newTopicCandidates":[],"evidenceText":"지방 한도는 3억원으로 낮췄다."},
                  {"eventType":"POLICY_REVIEW","title":"추가 규제 검토","subject":"추가 규제","subjectKey":"ADDITIONAL_REGULATION","eventDate":"2026-08-24","previousState":null,"newState":null,"status":"UNDER_REVIEW","region":null,"topicKeys":["MORTGAGE"],"newTopicCandidates":[],"evidenceText":"추가 규제는 검토 중이다."},
                  {"eventType":"FORECAST_CHANGE","title":"성장률 전망 하향","subject":"성장률 전망","subjectKey":"GROWTH_FORECAST","eventDate":"2026-08-24","previousState":"1.8%","newState":"1.5%","status":"CONFIRMED","region":null,"topicKeys":["ECONOMIC_GROWTH"],"newTopicCandidates":[],"evidenceText":"성장률 전망은 1.8%에서 1.5%로 낮췄다."}
                ]}]}
                """;

        var candidates = OpenAiNewsAnalyzer.parseDraftBundle(new ObjectMapper(), json, List.of(article)).eventCandidates();
        assertEquals(4, candidates.size());
        assertNull(candidates.get(1).previousState());
        assertEquals(com.economicbriefing.economicflow.EventStatus.UNDER_REVIEW, candidates.get(2).status());
        assertEquals(com.economicbriefing.economicflow.EventType.POLICY_REVIEW, candidates.get(2).eventType());
        assertEquals("요약된 근거", OpenAiNewsAnalyzer.parseDraftBundle(new ObjectMapper(),
                json.replace("주담대 한도를 5억원에서 4억원으로 낮췄다.\"}", "요약된 근거\"}"),
                List.of(article)).eventCandidates().getFirst().evidenceText());
    }

    @Test
    void shouldAcceptEventNodeWithoutStateSlot() throws Exception {
        Article article = new Article(
                "article-1", "집단대출 재개", "", "연합뉴스", ArticleSourceType.NEWS_MEDIA,
                OffsetDateTime.now(), OffsetDateTime.now(), "https://example.com/1",
                List.of(NewsCategory.LOAN), "ko", "새마을금고가 집단대출을 재개했다.");
        String json = """
                {"articles":[{"articleId":"article-1","issues":[{"name":"집단대출 재개","mainFacts":[],
                  "changes":[],"relationCandidates":[],"statements":[],"keyTerms":[]}],"eventCandidates":[{
                  "eventType":"POLICY_CHANGE","title":"새마을금고 집단대출 재개","subject":"새마을금고",
                  "subjectKey":"SAEMAUL_GEUMGO","eventDate":"2026-08-27","previousState":"중단","newState":"재개",
                  "status":"CONFIRMED","region":"KR","topicKeys":["HOUSEHOLD_DEBT"],"newTopicCandidates":[],
                  "evidenceText":"새마을금고가 집단대출을 재개했다.","candidateKey":"event1","nodeKind":"EVENT",
                  "scopeKey":"KR","slotKey":null,"valueKey":null}]}]}
                """;

        var candidate = OpenAiNewsAnalyzer.parseDraftBundle(new ObjectMapper(), json, List.of(article))
                .eventCandidates().getFirst();
        assertEquals(com.economicbriefing.economicflow.NodeKind.EVENT, candidate.nodeKind());
        assertNull(candidate.slotKey());
    }

    @Test
    void shouldRejectRelationWithOnlyOneCandidateEndpoint() {
        Article article = new Article(
                "article-1", "금리", "", "연합뉴스", ArticleSourceType.NEWS_MEDIA,
                OffsetDateTime.now(), OffsetDateTime.now(), "https://example.com/1",
                List.of(NewsCategory.INTEREST_RATE), "ko", "금리인상으로 채권금리가 올랐다.");
        String json = """
                {"articles":[{"articleId":"article-1","issues":[{"name":"금리","mainFacts":[],"changes":[],
                  "relationCandidates":[{"evidence":"금리인상으로 채권금리가 올랐다.","atomicRelations":[{
                    "from":"금리인상","to":"채권금리 상승","relationType":"CAUSE_OR_RESULT","evidenceType":"FACT",
                    "speaker":null,"fromCandidateKey":"event1","toCandidateKey":null}]}],"statements":[],"keyTerms":[]}],
                  "eventCandidates":[]}]}
                """;

        assertThrows(IllegalArgumentException.class,
                () -> OpenAiNewsAnalyzer.parseDraftBundle(new ObjectMapper(), json, List.of(article)));
    }

    @Test
    void shouldPreserveArticleEvidenceInPromptAndResponse() throws Exception {
        Article article = new Article(
                "article-1", "대출 관리 목표 상향", "신규 대출여력이 늘어난다.", "연합뉴스",
                ArticleSourceType.NEWS_MEDIA, OffsetDateTime.now(), OffsetDateTime.now(),
                "https://example.com/1", List.of(NewsCategory.LOAN), "ko",
                "총량 증가율 목표를 1.5%에서 3.0%로 높여 신규 대출여력 30조원이 확보됐다.");

        String prompt = ArticleAnalyzerPromptBuilder.build(List.of(article));
        assertTrue(prompt.contains(article.content()));
        assertTrue(ArticleAnalyzerPromptBuilder.SYSTEM_PROMPT.contains("외부 지식"));
        assertFalse(ArticleAnalyzerPromptBuilder.SYSTEM_PROMPT.contains("Economic Flow 판단:"));
        assertTrue(ArticleAnalyzerPromptBuilder.SYSTEM_PROMPT.contains("관계 추출은 별도"));
        assertTrue(ArticleAnalyzerPromptBuilder.SYSTEM_PROMPT.contains("relationCandidates와 flowClaims는 항상 빈 배열"));
        assertTrue(RelationCandidateExtractorPromptBuilder.SYSTEM_PROMPT.contains("기사 원문을 처음부터 끝까지 직접 읽고"));
        assertTrue(RelationCandidateExtractorPromptBuilder.SYSTEM_PROMPT.contains("A→B와 B→C"));
        assertTrue(RelationCandidateExtractorPromptBuilder.SYSTEM_PROMPT.contains("원문의 완전한 문장 또는 최소 원문 span"));
        assertFalse(ArticleAnalyzerPromptBuilder.SYSTEM_PROMPT.contains("relations, statements, keyTerms를 빠짐없이"));
        assertTrue(ArticleAnalyzerPromptBuilder.schemaForArticleCount(1).contains("CONDITION"));
        assertTrue(RetrievalRouterPromptBuilder.SYSTEM_PROMPT.contains("인물 프로필 TERM"));
        assertTrue(RetrievalRouterPromptBuilder.SYSTEM_PROMPT.contains("핵심 A→B의 WHY"));
        String validatorPrompt = ArticleValidatorPromptBuilder.build(List.of(article), "{\"articles\":[]}");
        assertTrue(validatorPrompt.contains("읽기 전용 Article Analyzer 결과"));
        assertTrue(validatorPrompt.contains("findings만 출력"));
        assertTrue(ArticleValidatorPromptBuilder.ITEM_VALIDATION_SYSTEM_PROMPT.contains("WRONG_SPEAKER"));
        assertTrue(ArticleValidatorPromptBuilder.ITEM_VALIDATION_SYSTEM_PROMPT.contains("MISSING을 찾거나 출력하지 마세요"));
        assertTrue(ArticleValidatorPromptBuilder.ITEM_VALIDATION_SYSTEM_PROMPT.contains("INACCURATE"));
        assertTrue(ArticleValidatorPromptBuilder.ITEM_VALIDATION_SYSTEM_PROMPT.contains("두 축을 바꾸어 제안하지 마세요"));
        assertTrue(ArticleValidatorPromptBuilder.ITEM_VALIDATION_SYSTEM_PROMPT.contains("정확히 보존했다면"));
        assertTrue(ArticleValidatorPromptBuilder.ITEM_VALIDATION_SYSTEM_PROMPT.contains("UNDER_CONSIDERATION 같은 새 값을 제안하지 마세요"));
        assertTrue(ArticleValidatorPromptBuilder.ITEM_VALIDATION_SYSTEM_PROMPT.contains("표현만 바꾸어 CLAIM으로 수정하지 마세요"));
        assertTrue(ArticleValidatorPromptBuilder.ITEM_VALIDATION_SYSTEM_PROMPT.contains("같은 집단이나 인물을 가리키면"));
        assertTrue(ArticleValidatorPromptBuilder.ITEM_VALIDATION_SYSTEM_PROMPT.contains("Finding Self-Consistency 검수"));
        assertTrue(ArticleValidatorPromptBuilder.MISSING_REVIEW_SYSTEM_PROMPT.contains("MISSING finding만 출력"));
        assertTrue(ArticleValidatorPromptBuilder.MISSING_REVIEW_SYSTEM_PROMPT.contains("절대 MISSING으로 보고하지 마세요"));
        assertTrue(ArticleValidatorPromptBuilder.MISSING_REVIEW_SYSTEM_PROMPT.contains("적용 대상→특례/예외→적용 결과"));
        assertTrue(ArticleValidatorPromptBuilder.MISSING_REVIEW_SYSTEM_PROMPT.contains("DETAIL OMISSION"));
        assertTrue(ArticleValidatorPromptBuilder.MISSING_REVIEW_SYSTEM_PROMPT.contains("필드 중복을 요구하지 마세요"));
        assertTrue(ArticleValidatorPromptBuilder.MISSING_REVIEW_SYSTEM_PROMPT.contains("개별 구성원을 하나씩 MISSING으로 열거하지 마세요"));

        String validationJson = """
                {"articles":[{"articleId":"article-1","findings":[{
                  "type":"MISSING","issue":"가계대출","targetType":"RELATION",
                  "targetReference":null,"description":"관계 누락","currentValue":null,
                  "suggestedValue":{"from":"A","to":"B"},"evidence":"A 때문에 B"
                }]}]}
                """;
        ArticleValidationResult validation = new ObjectMapper()
                .readValue(validationJson, ArticleValidationResult.class);
        assertEquals("A", validation.articles().get(0).findings().get(0)
                .suggestedValue().get("from").asText());
        assertEquals(ArticleValidationResult.FindingType.INACCURATE,
                ArticleValidationResult.FindingType.valueOf("INACCURATE"));

        String json = """
                {"articles":[{
                  "articleId":"article-1",
                  "issues":[{
                    "name":"가계대출",
                    "mainFacts":["총량 증가율 목표가 상향됐다."],
                    "changes":[{"target":"총량 증가율 목표","before":"1.5%","after":"3.0%","status":"CONFIRMED"}],
                    "relations":[{"from":"목표 상향","to":"대출여력 증가","relationType":"CAUSE_OR_RESULT","articleExplanation":"기사에 제시된 산식에 따른다.","evidenceType":"FACT","speaker":null}],
                    "statements":[{"type":"PREDICTION","speaker":"금융당국","content":"대출난이 누그러질 것으로 봤다."}],
                    "keyTerms":["가계부채 총량관리"]
                  }]
                }]}
                """;

        ArticleAnalysisResponse response = new ObjectMapper().readValue(json, ArticleAnalysisResponse.class);
        var issue = response.articles().get(0).issues().get(0);
        assertEquals("기사에 제시된 산식에 따른다.", issue.relations().get(0).articleExplanation());
        assertEquals(ArticleAnalysisResponse.StatementType.FACT, issue.relations().get(0).evidenceType());
        assertEquals("금융당국", issue.statements().get(0).speaker());
        assertEquals(ArticleAnalysisResponse.StatementType.PREDICTION,
                issue.statements().get(0).type());
        assertEquals(List.of("가계부채 총량관리"), issue.keyTerms());

        var duplicate = new ArticleValidationResult.Finding(
                ArticleValidationResult.FindingType.MISSING, "가계대출",
                ArticleValidationResult.TargetType.MAIN_FACT, null,
                "총량 증가율 목표가 상향됐다.", null, null, "원문");
        var emptyValidation = new ArticleValidationResult(List.of(
                new ArticleValidationResult.ArticleValidation("article-1", List.of())));
        var duplicateValidation = new ArticleValidationResult(List.of(
                new ArticleValidationResult.ArticleValidation("article-1", List.of(duplicate))));
        assertTrue(ArticleValidationMerger.merge(List.of(article), response, emptyValidation, duplicateValidation)
                .articles().get(0).findings().isEmpty());

        var nodes = new ObjectMapper().getNodeFactory();
        var unidentifiedUnsupported = new ArticleValidationResult.Finding(
                ArticleValidationResult.FindingType.UNSUPPORTED, "가계대출",
                ArticleValidationResult.TargetType.CHANGE, null,
                "근거 없음", null, null, null);
        var mixedAxes = new ArticleValidationResult.Finding(
                ArticleValidationResult.FindingType.INACCURATE, "가계대출",
                ArticleValidationResult.TargetType.RELATION, null,
                "축 혼동", nodes.textNode("CLAIMED_EFFECT"), nodes.textNode("CLAIM"), "원문");
        var sourceSupported = new ArticleValidationResult.Finding(
                ArticleValidationResult.FindingType.UNSUPPORTED, "가계대출",
                ArticleValidationResult.TargetType.MAIN_FACT, "issues[0].mainFacts[0]",
                "원문 근거 없음", nodes.textNode(article.content()), null, null);
        var validType = new ArticleValidationResult.Finding(
                ArticleValidationResult.FindingType.WRONG_TYPE, "가계대출",
                ArticleValidationResult.TargetType.RELATION, "issues[0].relations[0].evidenceType",
                "근거 성격 오류", nodes.textNode("FACT"), nodes.textNode("INTERPRETATION"), "원문");
        var itemValidation = new ArticleValidationResult(List.of(
                new ArticleValidationResult.ArticleValidation(
                        "article-1", List.of(unidentifiedUnsupported, mixedAxes, sourceSupported, validType))));
        var filtered = ArticleValidationMerger.merge(List.of(article), response, itemValidation, emptyValidation)
                .articles().get(0).findings();
        assertEquals(List.of(validType), filtered);

        var marketIssue = new ArticleAnalysisResponse.Issue(
                "시장 금리", List.of("대표 A +1.0bp", "대표 B +2.0bp", "대표 C +3.0bp"),
                List.of(), List.of(), List.of(), List.of());
        var marketResponse = new ArticleAnalysisResponse(List.of(
                new ArticleAnalysisResponse.ArticleAnalysis("article-1", List.of(marketIssue))));
        var repeatedDetail = new ArticleValidationResult.Finding(
                ArticleValidationResult.FindingType.MISSING, "시장 금리",
                ArticleValidationResult.TargetType.MAIN_FACT, null,
                "추가 지표 누락", null, null, "추가 지표 +1.5bp");
        var structuralNumber = new ArticleValidationResult.Finding(
                ArticleValidationResult.FindingType.MISSING, "시장 금리",
                ArticleValidationResult.TargetType.MAIN_FACT, null,
                "적용 기준 누락", null, null, "적용 기준은 +1.5bp 이상");
        var seriesValidation = new ArticleValidationResult(List.of(
                new ArticleValidationResult.ArticleValidation(
                        "article-1", List.of(repeatedDetail, structuralNumber))));
        assertEquals(List.of(structuralNumber), ArticleValidationMerger.merge(
                List.of(article), marketResponse, emptyValidation, seriesValidation)
                .articles().get(0).findings());

        var unchangedType = new ArticleValidationResult.Finding(
                ArticleValidationResult.FindingType.WRONG_TYPE, "가계대출",
                ArticleValidationResult.TargetType.RELATION, "issues[0].relations[0].evidenceType",
                "FACT가 적절하다", nodes.textNode("FACT"), nodes.textNode("FACT"), "원문");
        var wrongTargetAxis = new ArticleValidationResult.Finding(
                ArticleValidationResult.FindingType.WRONG_TYPE, "가계대출",
                ArticleValidationResult.TargetType.RELATION, "issues[0].relations[0].evidenceType",
                "관계 유형 변경", nodes.textNode("FACT"), nodes.textNode("PURPOSE"), "원문");
        var inconsistentValidation = new ArticleValidationResult(List.of(
                new ArticleValidationResult.ArticleValidation(
                        "article-1", List.of(unchangedType, wrongTargetAxis))));
        assertTrue(ArticleValidationMerger.merge(
                List.of(article), response, inconsistentValidation, emptyValidation)
                .articles().get(0).findings().isEmpty());

        var descriptionContradictsSuggestion = new ArticleValidationResult.Finding(
                ArticleValidationResult.FindingType.WRONG_TYPE, "가계대출",
                ArticleValidationResult.TargetType.RELATION, "issues[0].relations[0].evidenceType",
                "기사에서는 '~로 풀이된다'고 하여 INTERPRETATION임을 명시했으나 CLAIM으로 잘못 설정됨.",
                nodes.textNode("INTERPRETATION"), nodes.textNode("CLAIM"), "원문");
        var contradictoryValidation = new ArticleValidationResult(List.of(
                new ArticleValidationResult.ArticleValidation(
                        "article-1", List.of(descriptionContradictsSuggestion))));
        assertTrue(ArticleValidationMerger.merge(
                List.of(article), response, contradictoryValidation, emptyValidation)
                .articles().get(0).findings().isEmpty());
    }
}

package com.economicbriefing.e2e;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.economicbriefing.classifier.ArticlePersistenceService;
import com.economicbriefing.classifier.EmbeddingService;
import com.economicbriefing.classifier.TeacherClassifier;
import com.economicbriefing.classifier.TeacherLabelResponse;
import com.economicbriefing.classifier.entity.ArticleEmbeddingEntity;
import com.economicbriefing.classifier.entity.ArticleEntity;
import com.economicbriefing.classifier.entity.TeacherLabelEntity;
import com.economicbriefing.classifier.repository.ArticleEmbeddingRepository;
import com.economicbriefing.classifier.repository.ArticleRepository;
import com.economicbriefing.classifier.repository.TeacherLabelRepository;
import com.economicbriefing.collector.NewsCollector;
import com.economicbriefing.collector.dto.CollectNewsRequest;
import com.economicbriefing.collector.dto.CollectNewsResult;
import com.economicbriefing.config.AppProperties;
import com.economicbriefing.config.OpenAiProperties;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.util.KstDateTimeUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 실제 RSS + OpenAI API를 사용하는 E2E 테스트.
 *
 * 조건: OPENAI_API_KEY 환경변수 필수.
 * DRY_RUN=false로 실제 구현체 사용.
 * 분석(Analyzer)/발행(Publisher)은 범위 밖 → 수집→저장→Teacher→Embedding만 테스트.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "briefing.dry-run=false",
    "briefing.embedding.enabled=true",
    "briefing.teacher.enabled=true",
    "briefing.teacher.prompt-version=teacher-v1",
    "admin.token=test-admin-token",
})
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = "sk-.+", disabledReason = "OPENAI_API_KEY not set")
class RealTeacherPipelineE2ETest {

    private static final Logger log = LoggerFactory.getLogger(RealTeacherPipelineE2ETest.class);

    @Autowired private NewsCollector collector;
    @Autowired private TeacherClassifier teacherClassifier;
    @Autowired private ArticlePersistenceService persistenceService;
    @Autowired private EmbeddingService embeddingService;
    @Autowired private ArticleRepository articleRepository;
    @Autowired private TeacherLabelRepository teacherLabelRepository;
    @Autowired private ArticleEmbeddingRepository embeddingRepository;
    @Autowired private AppProperties appProperties;
    @Autowired private OpenAiProperties openAiProperties;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        embeddingRepository.deleteAll();
        teacherLabelRepository.deleteAll();
        articleRepository.deleteAll();
    }

    @Test
    void realEndToEndTest() throws Exception {
        StringBuilder report = new StringBuilder();
        report.append("\n╔══════════════════════════════════════════════════════════════╗\n");
        report.append("║         REAL E2E Test Report (실제 API 사용)                ║\n");
        report.append("╠══════════════════════════════════════════════════════════════╣\n");

        // === Phase 1: 실제 RSS 수집 ===
        OffsetDateTime t0 = OffsetDateTime.now();

        KstDateTimeUtil.TimeRange timeRange = KstDateTimeUtil.getHourlyTimeRange();
        LocalDate targetDate = timeRange.targetDate();
        report.append(String.format("║ 대상 시간 범위: %s ~ %s%n", timeRange.start(), timeRange.end()));
        report.append(String.format("║ 대상 날짜: %s%n", targetDate));

        OffsetDateTime collectStart = OffsetDateTime.now();
        CollectNewsResult collectResult = collector.collect(
                CollectNewsRequest.of(targetDate, timeRange.start(), timeRange.end()));
        Duration collectDuration = Duration.between(collectStart, OffsetDateTime.now());

        int rawCollected = collectResult.totalCollected();
        int afterFilter = collectResult.totalAccepted();
        int rejected = collectResult.totalRejected();
        List<Article> articles = collectResult.articles();

        report.append("╠══════════════════════════════════════════════════════════════╣\n");
        report.append(String.format("║ [1] RSS 수집 결과%n"));
        report.append(String.format("║     총 수집: %d건%n", rawCollected));
        report.append(String.format("║     필터 통과: %d건%n", afterFilter));
        report.append(String.format("║     제거 (날짜/품질/중복): %d건%n", rejected));
        report.append(String.format("║     수집 소요 시간: %dms%n", collectDuration.toMillis()));

        // 소스별 리포트
        report.append("║     소스별 현황:%n");
        for (var sr : collectResult.sourceReports()) {
            report.append(String.format("║       - %s: %s (수집=%d, 통과=%d%s)%n",
                    sr.sourceName(), sr.status(),
                    sr.collectedCount(), sr.acceptedCount(),
                    sr.durationMs() != null ? ", " + sr.durationMs() + "ms" : ""));
        }

        if (articles.isEmpty()) {
            report.append("║%n║ ⚠ 해당 시간대에 수집된 기사가 없습니다.%n");
            report.append("║   직전 1시간 범위에 RSS 기사가 없을 수 있습니다.%n");
            report.append("╚══════════════════════════════════════════════════════════════╝%n");
            log.info(report.toString());
            return; // 기사 없으면 이후 단계 skip
        }

        // === Phase 2: DB 저장 ===
        OffsetDateTime persistStart = OffsetDateTime.now();
        int savedCount = persistenceService.saveAll(articles);
        Duration persistDuration = Duration.between(persistStart, OffsetDateTime.now());
        int duplicateSkipped = articles.size() - savedCount;

        report.append("╠══════════════════════════════════════════════════════════════╣\n");
        report.append(String.format("║ [2] DB 저장 (중복 제거)%n"));
        report.append(String.format("║     신규 저장: %d건%n", savedCount));
        report.append(String.format("║     중복 스킵: %d건%n", duplicateSkipped));
        report.append(String.format("║     저장 소요 시간: %dms%n", persistDuration.toMillis()));

        // === Phase 3: Teacher 분류 ===
        OffsetDateTime teacherStart = OffsetDateTime.now();
        String promptVersion = appProperties.teacher().promptVersion();
        int teacherSuccess = 0;
        int teacherFail = 0;
        long relevantCount = 0;
        long irrelevantCount = 0;
        long uncertainCount = 0;
        List<TeacherResult> teacherResults = new ArrayList<>();

        for (Article article : articles) {
            try {
                OffsetDateTime callStart = OffsetDateTime.now();
                TeacherLabelResponse response = teacherClassifier.classify(article);
                long callMs = Duration.between(callStart, OffsetDateTime.now()).toMillis();

                // DB 저장
                TeacherLabelEntity entity = new TeacherLabelEntity();
                entity.setArticleId(article.id());
                entity.setLabel(response.label());
                entity.setConfidence(response.confidence());
                entity.setReason(response.reason());
                entity.setAffectedAreas(response.affectedAreas() != null
                        ? toJsonArray(response.affectedAreas()) : null);
                entity.setSeverity(response.severity());
                entity.setNeedsFollowUp(response.needsFollowUp());
                entity.setUsableForTraining(response.usableForTraining());
                entity.setTeacherModelProvider("openai");
                entity.setTeacherModelName(openAiProperties.model());
                entity.setTeacherPromptVersion(promptVersion);
                entity.setTeacherTemperature(openAiProperties.temperature());
                entity.setLabeledAt(OffsetDateTime.now());
                teacherLabelRepository.save(entity);

                teacherResults.add(new TeacherResult(article, response, callMs, null));
                teacherSuccess++;

                if ("RELEVANT".equals(response.label())) relevantCount++;
                else if ("IRRELEVANT".equals(response.label())) irrelevantCount++;
                else uncertainCount++;

            } catch (Exception e) {
                teacherFail++;
                teacherResults.add(new TeacherResult(article, null, 0, e.getMessage()));
                log.error("Teacher 분류 실패: article={}, error={}", article.id(), e.getMessage());
            }
        }
        Duration teacherDuration = Duration.between(teacherStart, OffsetDateTime.now());

        report.append("╠══════════════════════════════════════════════════════════════╣\n");
        report.append(String.format("║ [3] Teacher LLM 분류%n"));
        report.append(String.format("║     성공: %d건, 실패: %d건%n", teacherSuccess, teacherFail));
        report.append(String.format("║     RELEVANT: %d건%n", relevantCount));
        report.append(String.format("║     IRRELEVANT: %d건%n", irrelevantCount));
        report.append(String.format("║     UNCERTAIN: %d건%n", uncertainCount));
        report.append(String.format("║     분류 소요 시간: %dms (평균 %dms/건)%n",
                teacherDuration.toMillis(),
                articles.isEmpty() ? 0 : teacherDuration.toMillis() / articles.size()));

        // === Phase 4: Embedding ===
        OffsetDateTime embedStart = OffsetDateTime.now();
        int embedSuccess = 0;
        int embedFail = 0;
        List<String> embedErrors = new ArrayList<>();

        for (Article article : articles) {
            try {
                embeddingService.embedAndSave(article);
                embedSuccess++;
            } catch (Exception e) {
                embedFail++;
                embedErrors.add(article.id() + ": " + e.getMessage());
                log.error("Embedding 실패: article={}, error={}", article.id(), e.getMessage());
            }
        }
        Duration embedDuration = Duration.between(embedStart, OffsetDateTime.now());

        report.append("╠══════════════════════════════════════════════════════════════╣\n");
        report.append(String.format("║ [4] Embedding 생성%n"));
        report.append(String.format("║     성공: %d건, 실패: %d건%n", embedSuccess, embedFail));
        report.append(String.format("║     성공률: %.1f%%%n",
                articles.isEmpty() ? 0 : (embedSuccess * 100.0 / articles.size())));
        report.append(String.format("║     소요 시간: %dms (평균 %dms/건)%n",
                embedDuration.toMillis(),
                articles.isEmpty() ? 0 : embedDuration.toMillis() / articles.size()));
        for (String err : embedErrors) {
            report.append(String.format("║     ⚠ %s%n", err));
        }

        // === Phase 5: DB 검증 ===
        List<ArticleEntity> dbArticles = articleRepository.findAll();
        List<TeacherLabelEntity> dbLabels = teacherLabelRepository.findAll();
        List<ArticleEmbeddingEntity> dbEmbeddings = embeddingRepository.findAll();

        report.append("╠══════════════════════════════════════════════════════════════╣\n");
        report.append(String.format("║ [5] DB 저장 검증%n"));
        report.append(String.format("║     articles 테이블: %d건%n", dbArticles.size()));
        report.append(String.format("║     teacher_labels 테이블: %d건%n", dbLabels.size()));
        report.append(String.format("║     article_embeddings 테이블: %d건%n", dbEmbeddings.size()));

        // === Phase 6: 오류/실패 ===
        report.append("╠══════════════════════════════════════════════════════════════╣\n");
        report.append(String.format("║ [6] 실패 기사%n"));
        List<TeacherResult> failures = teacherResults.stream()
                .filter(r -> r.error != null).toList();
        if (failures.isEmpty() && embedErrors.isEmpty()) {
            report.append("║     없음 (모든 단계 성공)%n");
        } else {
            for (TeacherResult f : failures) {
                report.append(String.format("║     ⚠ Teacher 실패: %s - %s%n",
                        f.article.title().substring(0, Math.min(30, f.article.title().length())),
                        f.error));
            }
        }

        // === Phase 7: 처리 시간 + 비용 추정 ===
        Duration totalDuration = Duration.between(t0, OffsetDateTime.now());
        // gpt-4o: ~$2.5/1M input, ~$10/1M output (approx)
        // embedding-3-small: ~$0.02/1M tokens
        int teacherCalls = teacherSuccess;
        int embedCalls = embedSuccess;

        report.append("╠══════════════════════════════════════════════════════════════╣\n");
        report.append(String.format("║ [7] 처리 시간 & 비용 추정%n"));
        report.append(String.format("║     전체 소요: %dms (%.1f초)%n",
                totalDuration.toMillis(), totalDuration.toMillis() / 1000.0));
        report.append(String.format("║     ├ 수집: %dms%n", collectDuration.toMillis()));
        report.append(String.format("║     ├ DB 저장: %dms%n", persistDuration.toMillis()));
        report.append(String.format("║     ├ Teacher 분류: %dms%n", teacherDuration.toMillis()));
        report.append(String.format("║     └ Embedding: %dms%n", embedDuration.toMillis()));
        report.append(String.format("║     API 호출: Teacher %d회, Embedding %d회%n",
                teacherCalls, embedCalls));
        report.append(String.format("║     비용 추정: Teacher ~$%.4f, Embedding ~$%.4f%n",
                teacherCalls * 0.003, embedCalls * 0.0001)); // rough estimate

        // === Phase 8: 샘플 기사 5건 ===
        report.append("╠══════════════════════════════════════════════════════════════╣\n");
        report.append("║ [8] 실제 저장 기사 샘플 (최대 5건)                           ║\n");
        report.append("╠══════════════════════════════════════════════════════════════╣\n");

        int sampleCount = Math.min(5, teacherResults.size());
        for (int i = 0; i < sampleCount; i++) {
            TeacherResult tr = teacherResults.get(i);
            boolean hasEmbedding = embeddingRepository
                    .findByArticleIdAndEmbeddingModel(tr.article.id(),
                            appProperties.embedding().model())
                    .isPresent();

            report.append(String.format("║ [%d] 제목: %s%n", i + 1, tr.article.title()));
            report.append(String.format("║     출처: %s | 발행: %s%n",
                    tr.article.sourceName(),
                    tr.article.publishedAt() != null ? tr.article.publishedAt().toString() : "N/A"));
            if (tr.response != null) {
                report.append(String.format("║     Label: %s (confidence=%.2f)%n",
                        tr.response.label(), tr.response.confidence()));
                report.append(String.format("║     사유: %s%n", tr.response.reason()));
                report.append(String.format("║     영향 분야: %s | 심각도: %s%n",
                        tr.response.affectedAreas(), tr.response.severity()));
            } else {
                report.append(String.format("║     Label: 실패 (%s)%n", tr.error));
            }
            report.append(String.format("║     Embedding: %s%n", hasEmbedding ? "생성됨" : "미생성"));
            report.append("║────────────────────────────────────────────────────────────\n");
        }

        report.append("╚══════════════════════════════════════════════════════════════╝\n");
        log.info(report.toString());

        // === Assertions ===
        assertTrue(rawCollected > 0, "RSS에서 최소 1건 이상 수집되어야 함");
        assertEquals(dbArticles.size(), savedCount, "DB 기사 수 = 저장 수");
        assertEquals(dbLabels.size(), teacherSuccess, "DB 라벨 수 = Teacher 성공 수");
        assertEquals(dbEmbeddings.size(), embedSuccess, "DB 임베딩 수 = Embedding 성공 수");
        assertEquals(0, teacherFail, "Teacher 분류 실패 0건");
        assertEquals(0, embedFail, "Embedding 실패 0건");
    }

    private static String toJsonArray(java.util.List<String> items) {
        if (items == null || items.isEmpty()) return "[]";
        return "[" + items.stream()
                .map(s -> "\"" + s.replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",")) + "]";
    }

    private record TeacherResult(
            Article article,
            TeacherLabelResponse response,
            long callMs,
            String error
    ) {}
}

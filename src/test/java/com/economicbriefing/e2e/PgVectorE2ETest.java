package com.economicbriefing.e2e;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostgreSQL + pgvector 운영 환경 E2E 테스트.
 *
 * 연합뉴스 RSS → DB 저장 → Teacher LLM → Embedding → pgvector 저장.
 * 실제 PostgreSQL과 OpenAI API를 사용한다.
 */
@SpringBootTest
@ActiveProfiles("pgtest")
@TestPropertySource(properties = {
    "briefing.dry-run=false",
    "briefing.embedding.enabled=true",
    "briefing.teacher.enabled=true",
    "briefing.teacher.prompt-version=teacher-v1",
    "admin.token=test-admin-token",
})
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = "sk-.+", disabledReason = "OPENAI_API_KEY not set")
class PgVectorE2ETest {

    private static final Logger log = LoggerFactory.getLogger(PgVectorE2ETest.class);

    @Autowired private NewsCollector collector;
    @Autowired private TeacherClassifier teacherClassifier;
    @Autowired private ArticlePersistenceService persistenceService;
    @Autowired private EmbeddingService embeddingService;
    @Autowired private ArticleRepository articleRepository;
    @Autowired private TeacherLabelRepository teacherLabelRepository;
    @Autowired private ArticleEmbeddingRepository embeddingRepository;
    @Autowired private AppProperties appProperties;
    @Autowired private OpenAiProperties openAiProperties;

    @Test
    void realPostgresE2ETest() {
        StringBuilder report = new StringBuilder();
        report.append("\n╔══════════════════════════════════════════════════════════════╗\n");
        report.append("║   PostgreSQL + pgvector 운영 환경 E2E Test Report            ║\n");
        report.append("╠══════════════════════════════════════════════════════════════╣\n");

        // === Phase 1: 연합뉴스 RSS 수집 (20:00~21:00) ===
        OffsetDateTime t0 = OffsetDateTime.now();

        LocalDate targetDate = LocalDate.of(2026, 7, 23);
        OffsetDateTime startTime = targetDate.atTime(20, 0).atOffset(ZoneOffset.ofHours(9));
        OffsetDateTime endTime = targetDate.atTime(20, 59, 59).atOffset(ZoneOffset.ofHours(9));

        report.append(String.format("║ 대상 시간 범위: %s ~ %s%n", startTime, endTime));
        report.append(String.format("║ 대상 날짜: %s%n", targetDate));
        report.append(String.format("║ DB: PostgreSQL (pgvector)%n"));

        OffsetDateTime collectStart = OffsetDateTime.now();
        CollectNewsResult collectResult = collector.collect(
                CollectNewsRequest.of(targetDate, startTime, endTime));
        Duration collectDuration = Duration.between(collectStart, OffsetDateTime.now());

        // 연합뉴스만 필터링
        List<Article> allArticles = collectResult.articles();
        List<Article> yonhapArticles = allArticles.stream()
                .filter(a -> "연합뉴스".equals(a.sourceName()))
                .toList();

        report.append("╠══════════════════════════════════════════════════════════════╣\n");
        report.append(String.format("║ [1] 기사 수집%n"));
        report.append(String.format("║     총 RSS 수집: %d건 (전 소스)%n", collectResult.totalCollected()));
        report.append(String.format("║     필터 통과: %d건 (전 소스)%n", collectResult.totalAccepted()));
        report.append(String.format("║     연합뉴스 기사: %d건%n", yonhapArticles.size()));
        report.append(String.format("║     수집 소요 시간: %dms%n", collectDuration.toMillis()));

        // 소스별 현황
        report.append("║     소스별 현황:%n");
        for (var sr : collectResult.sourceReports()) {
            report.append(String.format("║       - %s: %s (수집=%d, 통과=%d)%n",
                    sr.sourceName(), sr.status(), sr.collectedCount(), sr.acceptedCount()));
        }

        // article_id 목록
        report.append("║     연합뉴스 article_id 목록:%n");
        for (Article a : yonhapArticles) {
            report.append(String.format("║       - %s%n", a.id()));
        }

        if (yonhapArticles.isEmpty()) {
            report.append("║%n║ ⚠ 20:00~21:00에 연합뉴스 기사가 없습니다.%n");
            report.append("║   전체 수집 기사로 대체합니다.%n");
            yonhapArticles = allArticles;
            if (yonhapArticles.isEmpty()) {
                report.append("║   전체 수집 기사도 없습니다. 테스트 종료.%n");
                report.append("╚══════════════════════════════════════════════════════════════╝\n");
                log.info(report.toString());
                return;
            }
        }

        // === Phase 2: articles 테이블 저장 ===
        OffsetDateTime persistStart = OffsetDateTime.now();
        int savedCount = persistenceService.saveAll(yonhapArticles);
        Duration persistDuration = Duration.between(persistStart, OffsetDateTime.now());

        report.append("╠══════════════════════════════════════════════════════════════╣\n");
        report.append(String.format("║ [2] articles 테이블 저장%n"));
        report.append(String.format("║     신규 저장: %d건%n", savedCount));
        report.append(String.format("║     중복 스킵: %d건%n", yonhapArticles.size() - savedCount));
        report.append(String.format("║     소요 시간: %dms%n", persistDuration.toMillis()));

        // === Phase 3: Teacher LLM 분류 ===
        OffsetDateTime teacherStart = OffsetDateTime.now();
        String promptVersion = appProperties.teacher().promptVersion();
        int teacherSuccess = 0, teacherFail = 0;
        long relevantCount = 0, irrelevantCount = 0, uncertainCount = 0;
        List<TeacherResult> teacherResults = new ArrayList<>();

        for (Article article : yonhapArticles) {
            try {
                OffsetDateTime callStart = OffsetDateTime.now();
                TeacherLabelResponse response = teacherClassifier.classify(article);
                long callMs = Duration.between(callStart, OffsetDateTime.now()).toMillis();

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
        report.append(String.format("║     소요 시간: %dms (평균 %dms/건)%n",
                teacherDuration.toMillis(),
                yonhapArticles.isEmpty() ? 0 : teacherDuration.toMillis() / yonhapArticles.size()));

        // 기사별 Teacher 결과
        report.append("║     기사별 결과:%n");
        for (TeacherResult tr : teacherResults) {
            String title = tr.article.title();
            if (title.length() > 40) title = title.substring(0, 40) + "...";
            if (tr.response != null) {
                report.append(String.format("║       - [%s] %s (conf=%.2f)%n",
                        tr.response.label(), title, tr.response.confidence()));
                report.append(String.format("║         사유: %s%n", tr.response.reason()));
            } else {
                report.append(String.format("║       - [실패] %s: %s%n", title, tr.error));
            }
        }

        // === Phase 4: Embedding 생성 + pgvector 저장 ===
        OffsetDateTime embedStart = OffsetDateTime.now();
        int embedSuccess = 0, embedFail = 0;
        List<String> embedErrors = new ArrayList<>();

        for (Article article : yonhapArticles) {
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
        report.append(String.format("║ [4] Embedding 생성 + pgvector 저장%n"));
        report.append(String.format("║     성공: %d건, 실패: %d건%n", embedSuccess, embedFail));
        report.append(String.format("║     소요 시간: %dms (평균 %dms/건)%n",
                embedDuration.toMillis(),
                yonhapArticles.isEmpty() ? 0 : embedDuration.toMillis() / yonhapArticles.size()));

        // 기사별 Embedding 결과
        report.append("║     기사별 결과:%n");
        for (Article article : yonhapArticles) {
            var emb = embeddingRepository.findByArticleIdAndEmbeddingModel(
                    article.id(), appProperties.embedding().model());
            if (emb.isPresent()) {
                report.append(String.format("║       - %s: dim=%d, pgvector=✅%n",
                        article.id(), emb.get().getDimensions()));
            } else {
                report.append(String.format("║       - %s: pgvector=❌%n", article.id()));
            }
        }
        for (String err : embedErrors) {
            report.append(String.format("║     ⚠ %s%n", err));
        }

        // === Phase 5: PostgreSQL 데이터 검증 ===
        // 이 테스트에서 저장한 article_id 목록
        List<String> testArticleIds = yonhapArticles.stream().map(Article::id).toList();

        long dbArticleCount = testArticleIds.stream()
                .filter(id -> articleRepository.findById(id).isPresent()).count();
        long dbLabelCount = teacherLabelRepository.findAll().stream()
                .filter(l -> testArticleIds.contains(l.getArticleId())).count();
        long dbEmbeddingCount = embeddingRepository.findAll().stream()
                .filter(e -> testArticleIds.contains(e.getArticleId())).count();

        report.append("╠══════════════════════════════════════════════════════════════╣\n");
        report.append(String.format("║ [5] PostgreSQL 데이터 검증%n"));
        report.append(String.format("║     articles 테이블: %d건%n", dbArticleCount));
        report.append(String.format("║     teacher_labels 테이블: %d건%n", dbLabelCount));
        report.append(String.format("║     article_embeddings (pgvector): %d건%n", dbEmbeddingCount));

        // JOIN 검증
        long joinCount = 0;
        for (String articleId : testArticleIds) {
            boolean hasArticle = articleRepository.findById(articleId).isPresent();
            boolean hasLabel = teacherLabelRepository.findAll().stream()
                    .anyMatch(l -> l.getArticleId().equals(articleId));
            boolean hasEmbed = embeddingRepository.findAll().stream()
                    .anyMatch(e -> e.getArticleId().equals(articleId));
            if (hasArticle && hasLabel && hasEmbed) joinCount++;
        }
        report.append(String.format("║     3테이블 JOIN 완전 일치: %d/%d건%n", joinCount, testArticleIds.size()));

        // === Phase 6: 샘플 데이터 5건 ===
        report.append("╠══════════════════════════════════════════════════════════════╣\n");
        report.append("║ [6] 샘플 데이터 (최대 5건)                                   ║\n");
        report.append("╠══════════════════════════════════════════════════════════════╣\n");

        int sampleCount = Math.min(5, teacherResults.size());
        for (int i = 0; i < sampleCount; i++) {
            TeacherResult tr = teacherResults.get(i);
            boolean hasEmbed = embeddingRepository
                    .findByArticleIdAndEmbeddingModel(tr.article.id(), appProperties.embedding().model())
                    .isPresent();

            report.append(String.format("║ [%d] 제목: %s%n", i + 1, tr.article.title()));
            report.append(String.format("║     출처: %s | article_id: %s%n",
                    tr.article.sourceName(), tr.article.id()));
            if (tr.response != null) {
                report.append(String.format("║     Teacher: %s (confidence=%.2f)%n",
                        tr.response.label(), tr.response.confidence()));
                report.append(String.format("║     사유: %s%n", tr.response.reason()));
            } else {
                report.append(String.format("║     Teacher: 실패 (%s)%n", tr.error));
            }
            report.append(String.format("║     Embedding: %s%n", hasEmbed ? "✅ 생성됨 (pgvector)" : "❌ 미생성"));
            report.append("║────────────────────────────────────────────────────────────\n");
        }

        // === Phase 7: 성능 + 비용 ===
        Duration totalDuration = Duration.between(t0, OffsetDateTime.now());

        report.append("╠══════════════════════════════════════════════════════════════╣\n");
        report.append(String.format("║ [7] 성능 & 비용 추정%n"));
        report.append(String.format("║     전체 소요: %.1f초%n", totalDuration.toMillis() / 1000.0));
        report.append(String.format("║     ├ RSS 수집: %dms%n", collectDuration.toMillis()));
        report.append(String.format("║     ├ DB 저장: %dms%n", persistDuration.toMillis()));
        report.append(String.format("║     ├ Teacher 분류: %dms%n", teacherDuration.toMillis()));
        report.append(String.format("║     └ Embedding: %dms%n", embedDuration.toMillis()));
        report.append(String.format("║     모델: Teacher=%s, Embedding=%s%n",
                openAiProperties.model(), appProperties.embedding().model()));
        report.append(String.format("║     API 호출: Teacher %d회, Embedding %d회%n",
                teacherSuccess, embedSuccess));
        report.append(String.format("║     비용 추정: Teacher ~$%.4f, Embedding ~$%.4f%n",
                teacherSuccess * 0.003, embedSuccess * 0.0001));

        // === 최종 체크리스트 ===
        report.append("╠══════════════════════════════════════════════════════════════╣\n");
        report.append(String.format("║ 최종 체크리스트%n"));
        report.append(String.format("║   %s 기사 수집%n", yonhapArticles.size() > 0 ? "✅" : "❌"));
        report.append(String.format("║   %s Teacher 라벨링%n", teacherSuccess > 0 ? "✅" : "❌"));
        report.append(String.format("║   %s Embedding 생성%n", embedSuccess > 0 ? "✅" : "❌"));
        report.append(String.format("║   %s PostgreSQL 저장%n", dbArticleCount > 0 ? "✅" : "❌"));
        report.append(String.format("║   %s pgvector 저장%n", dbEmbeddingCount > 0 ? "✅" : "❌"));
        report.append(String.format("║   %s article_id 연결 (3테이블 JOIN)%n",
                joinCount == testArticleIds.size() ? "✅" : "❌"));
        if (teacherFail > 0) {
            report.append(String.format("║   ❌ Teacher 실패: %d건%n", teacherFail));
        }
        if (embedFail > 0) {
            report.append(String.format("║   ❌ Embedding 실패: %d건%n", embedFail));
        }
        report.append("╚══════════════════════════════════════════════════════════════╝\n");

        log.info(report.toString());

        // === Assertions ===
        assertTrue(yonhapArticles.size() > 0, "기사가 수집되어야 함");
        assertEquals(savedCount, dbArticleCount, "저장 수 = DB 기사 수");
        assertEquals(teacherSuccess, dbLabelCount, "Teacher 성공 수 = DB 라벨 수");
        assertEquals(embedSuccess, dbEmbeddingCount, "Embedding 성공 수 = DB 임베딩 수");
        assertEquals(testArticleIds.size(), joinCount, "모든 기사가 3테이블에 존재");
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

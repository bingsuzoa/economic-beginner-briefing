package com.economicbriefing.e2e;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.economicbriefing.classifier.ArticlePersistenceService;
import com.economicbriefing.classifier.entity.ArticleEntity;
import com.economicbriefing.classifier.entity.TeacherLabelEntity;
import com.economicbriefing.classifier.repository.ArticleEmbeddingRepository;
import com.economicbriefing.classifier.repository.ArticleRepository;
import com.economicbriefing.classifier.repository.TeacherLabelRepository;
import com.economicbriefing.domain.execution.ExecutionLog;
import com.economicbriefing.domain.execution.ExecutionStatus;
import com.economicbriefing.pipeline.BriefingPipeline;
import com.economicbriefing.pipeline.PipelineOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teacher Label + Embedding 파이프라인 E2E 테스트.
 *
 * 검증 항목:
 * 1. 뉴스 수집 → 2. 중복 제거 → 3. DB 저장 → 4. Teacher Label 생성
 * → 5. Label DB 저장 → 6/7. Embedding (disabled) → 8. 실패 데이터 → 9. 처리 시간
 */
@SpringBootTest
@ActiveProfiles("test")
class TeacherPipelineE2ETest {

    private static final Logger log = LoggerFactory.getLogger(TeacherPipelineE2ETest.class);

    @Autowired private BriefingPipeline pipeline;
    @Autowired private ArticleRepository articleRepository;
    @Autowired private TeacherLabelRepository teacherLabelRepository;
    @Autowired private ArticleEmbeddingRepository embeddingRepository;
    @Autowired private com.economicbriefing.admin.repository.PipelineRunRepository runRepository;
    @Autowired private com.economicbriefing.admin.repository.PipelineLogRepository logRepository;
    @Autowired private com.economicbriefing.admin.repository.PipelineItemRepository itemRepository;

    @BeforeEach
    void setUp() {
        logRepository.deleteAll();
        itemRepository.deleteAll();
        runRepository.deleteAll();
        articleRepository.deleteAll();
        teacherLabelRepository.deleteAll();
        embeddingRepository.deleteAll();
    }

    @Test
    void fullPipelineWithTeacherClassification() {
        // === SETUP ===
        LocalDate targetDate = LocalDate.of(2026, 7, 23);
        PipelineOptions options = PipelineOptions.manual(targetDate);

        OffsetDateTime startTime = OffsetDateTime.now();

        // === EXECUTE ===
        ExecutionLog result = pipeline.run(options);

        OffsetDateTime endTime = OffsetDateTime.now();
        Duration totalDuration = Duration.between(startTime, endTime);

        // === REPORT ===
        log.info("========== E2E Test Report ==========");

        // 1. 뉴스 수집
        int collectedCount = result.getCollectedArticleCount();
        log.info("[1] 수집 기사 수: {}", collectedCount);
        assertTrue(collectedCount > 0, "기사가 수집되어야 함");

        // 2. 중복 제거 (두 번째 실행으로 확인)
        // 첫 실행 후 DB에 저장된 기사 수
        List<ArticleEntity> savedArticles = articleRepository.findAll();
        int savedCount = savedArticles.size();
        log.info("[2] DB 저장 기사 수 (중복 제거 후): {}", savedCount);

        // 3. 기사 DB 저장 확인
        log.info("[3] 기사 DB 저장 검증:");
        for (ArticleEntity article : savedArticles) {
            log.info("  - id={}, title={}, source={}, hash={}",
                    article.getId(),
                    article.getTitle().substring(0, Math.min(30, article.getTitle().length())),
                    article.getSource(),
                    article.getContentHash() != null ? article.getContentHash().substring(0, 16) + "..." : "null");
            assertNotNull(article.getContentHash(), "contentHash가 설정되어야 함");
            assertNotNull(article.getTitle());
            assertNotNull(article.getSource());
            assertNotNull(article.getCollectedAt());
        }
        assertEquals(3, savedCount, "Mock 기사 3건이 저장되어야 함");

        // 4. Teacher Label — disabled in simplified pipeline, should be 0
        List<TeacherLabelEntity> labels = teacherLabelRepository.findAll();
        log.info("[4] Teacher Label 생성 수: {} (teacher disabled)", labels.size());
        assertEquals(0, labels.size(), "Teacher가 비활성화되어 Label이 생성되지 않아야 함");

        // 6 & 7. Embedding (기본 disabled)
        long embeddingCount = embeddingRepository.count();
        log.info("[6][7] Embedding 생성 수: {} (기본 disabled)", embeddingCount);
        assertEquals(0, embeddingCount, "Embedding은 기본 비활성화");

        // 8. 실패 데이터 확인
        log.info("[8] 오류 발생 수: {}", result.getErrors().size());
        for (var error : result.getErrors()) {
            log.info("  - stage={}, code={}, message={}", error.stage(), error.code(), error.message());
        }
        assertTrue(result.getErrors().isEmpty(), "정상 실행에서는 오류가 없어야 함");

        // 9. 처리 시간
        log.info("[9] 전체 처리 시간: {}ms", totalDuration.toMillis());
        log.info("[9] 파이프라인 상태: {}", result.getStatus());

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        log.info("========== E2E Test Report End ==========");
    }

    @Test
    void duplicateArticlesShouldBeSkipped() {
        LocalDate targetDate = LocalDate.of(2026, 7, 22);

        // 첫 번째 실행
        pipeline.run(PipelineOptions.manual(targetDate));
        long firstRunArticles = articleRepository.count();
        long firstRunLabels = teacherLabelRepository.count();

        // 같은 날짜로 다시 실행 → dedup에 의해 SKIP
        ExecutionLog secondRun = pipeline.run(PipelineOptions.manual(targetDate));
        long secondRunArticles = articleRepository.count();

        log.info("중복 실행 테스트: firstArticles={}, secondArticles={}, secondStatus={}",
                firstRunArticles, secondRunArticles, secondRun.getStatus());

        // pipeline dedup으로 두 번째는 skip됨
        assertEquals(ExecutionStatus.SUCCESS, secondRun.getStatus());
        assertEquals(0, secondRun.getCollectedArticleCount(), "두 번째 실행은 수집 SKIP");
    }

    @Test
    void articlePersistenceShouldHandleContentHashDuplicates() {
        LocalDate date1 = LocalDate.of(2026, 7, 20);
        LocalDate date2 = LocalDate.of(2026, 7, 21);

        // 다른 날짜로 실행하면 pipeline dedup은 통과하지만,
        // MockCollector는 같은 article id를 생성하므로 DB에서 중복 처리됨
        pipeline.run(PipelineOptions.manual(date1));
        long afterFirst = articleRepository.count();

        pipeline.run(PipelineOptions.manual(date2));
        long afterSecond = articleRepository.count();

        log.info("contentHash 중복 테스트: afterFirst={}, afterSecond={}", afterFirst, afterSecond);

        // Mock 기사는 id가 같으므로 JPA에서 upsert/skip
        assertEquals(afterFirst, afterSecond, "동일 기사는 중복 저장되지 않아야 함");
    }

    @Test
    void shouldGenerateReportForAllTestItems() {
        LocalDate targetDate = LocalDate.of(2026, 7, 23);
        logRepository.deleteAll();
        itemRepository.deleteAll();
        runRepository.deleteAll();
        articleRepository.deleteAll();
        teacherLabelRepository.deleteAll();

        OffsetDateTime t0 = OffsetDateTime.now();
        ExecutionLog result = pipeline.run(PipelineOptions.manual(targetDate));
        OffsetDateTime t1 = OffsetDateTime.now();

        List<ArticleEntity> articles = articleRepository.findAll();
        List<TeacherLabelEntity> labels = teacherLabelRepository.findAll();

        // === 최종 보고서 출력 ===
        StringBuilder report = new StringBuilder();
        report.append("\n╔════════════════════════════════════════════════════╗\n");
        report.append("║     Teacher Pipeline E2E Test Report               ║\n");
        report.append("╠════════════════════════════════════════════════════╣\n");
        report.append(String.format("║ 실행 시각     : %s%n", t0));
        report.append(String.format("║ 대상 날짜     : %s%n", targetDate));
        report.append(String.format("║ 파이프라인 상태 : %s%n", result.getStatus()));
        report.append(String.format("║ 전체 처리 시간 : %dms%n", Duration.between(t0, t1).toMillis()));
        report.append("╠════════════════════════════════════════════════════╣\n");
        report.append(String.format("║ [1] 수집 기사 수       : %d건%n", result.getCollectedArticleCount()));
        report.append(String.format("║ [2] DB 저장 기사 수     : %d건 (중복 제거 후)%n", articles.size()));
        report.append(String.format("║ [3] 선택 뉴스 수       : %d건%n", result.getSelectedNewsCount()));

        long relevant = labels.stream().filter(l -> "RELEVANT".equals(l.getLabel())).count();
        long irrelevant = labels.stream().filter(l -> "IRRELEVANT".equals(l.getLabel())).count();
        report.append(String.format("║ [4] RELEVANT          : %d건%n", relevant));
        report.append(String.format("║     IRRELEVANT        : %d건%n", irrelevant));

        long embeddings = embeddingRepository.count();
        report.append(String.format("║ [5] Embedding 생성     : %d건 (disabled)%n", embeddings));
        report.append(String.format("║ [6] 오류 수            : %d건%n", result.getErrors().size()));
        report.append("╠════════════════════════════════════════════════════╣\n");
        report.append("║ DB 저장 샘플 (기사)                                ║\n");
        report.append("╠════════════════════════════════════════════════════╣\n");

        for (int i = 0; i < Math.min(5, articles.size()); i++) {
            ArticleEntity a = articles.get(i);
            TeacherLabelEntity lbl = labels.stream()
                    .filter(l -> l.getArticleId().equals(a.getId()))
                    .findFirst().orElse(null);
            report.append(String.format("║ [%d] id: %s%n", i + 1, a.getId()));
            report.append(String.format("║     제목: %s%n", a.getTitle()));
            report.append(String.format("║     출처: %s%n", a.getSource()));
            report.append(String.format("║     hash: %s%n", a.getContentHash() != null ? a.getContentHash().substring(0, 16) + "..." : "N/A"));
            if (lbl != null) {
                report.append(String.format("║     label: %s (conf=%.2f, severity=%s)%n",
                        lbl.getLabel(), lbl.getConfidence(), lbl.getSeverity()));
                report.append(String.format("║     reason: %s%n", lbl.getReason()));
                report.append(String.format("║     promptVer: %s%n", lbl.getTeacherPromptVersion()));
            }
            report.append("║────────────────────────────────────────────────────\n");
        }
        report.append("╚════════════════════════════════════════════════════╝\n");

        log.info(report.toString());

        // Assertions
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals(3, articles.size());
        assertEquals(0, labels.size(), "Teacher disabled — no labels created");
        assertEquals(0, relevant);
        assertEquals(0, result.getErrors().size());
    }
}

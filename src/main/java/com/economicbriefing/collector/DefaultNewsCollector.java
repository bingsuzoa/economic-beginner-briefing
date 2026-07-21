package com.economicbriefing.collector;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.economicbriefing.collector.dto.CollectNewsRequest;
import com.economicbriefing.collector.dto.CollectNewsResult;
import com.economicbriefing.collector.filter.DateFilter;
import com.economicbriefing.collector.filter.DuplicateRemover;
import com.economicbriefing.collector.filter.QualityValidator;
import com.economicbriefing.collector.source.SourceAdapter;
import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.SourceCollectionReport;
import com.economicbriefing.util.KstDateTimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "briefing.dry-run", havingValue = "false")
public class DefaultNewsCollector implements NewsCollector {

    private static final Logger log = LoggerFactory.getLogger(DefaultNewsCollector.class);

    private final List<SourceAdapter> sourceAdapters;
    private final DateFilter dateFilter;
    private final QualityValidator qualityValidator;
    private final DuplicateRemover duplicateRemover;
    private final ExecutorService executor;

    public DefaultNewsCollector(
            List<SourceAdapter> sourceAdapters,
            DateFilter dateFilter,
            QualityValidator qualityValidator,
            DuplicateRemover duplicateRemover
    ) {
        this.sourceAdapters = sourceAdapters;
        this.dateFilter = dateFilter;
        this.qualityValidator = qualityValidator;
        this.duplicateRemover = duplicateRemover;
        this.executor = Executors.newFixedThreadPool(10);
    }

    @Override
    public CollectNewsResult collect(CollectNewsRequest request) {
        OffsetDateTime startTime = request.startTime() != null
                ? request.startTime()
                : KstDateTimeUtil.getTargetDateStart(request.targetDate());
        OffsetDateTime endTime = request.endTime() != null
                ? request.endTime()
                : KstDateTimeUtil.getTargetDateEnd(request.targetDate());

        List<SourceCollectionReport> sourceReports = new ArrayList<>();
        List<Article> allArticles = new ArrayList<>();
        int totalCollected = 0;

        // Collect from all sources in parallel
        List<CompletableFuture<SourceResult>> futures = sourceAdapters.stream()
                .map(adapter -> CompletableFuture.supplyAsync(() -> {
                    long startMs = System.currentTimeMillis();
                    try {
                        SourceAdapter.SourceCollectionResult result = adapter.collect(startTime, endTime);
                        long durationMs = System.currentTimeMillis() - startMs;
                        return new SourceResult(adapter, result, durationMs, null);
                    } catch (Exception e) {
                        long durationMs = System.currentTimeMillis() - startMs;
                        return new SourceResult(adapter, null, durationMs, e);
                    }
                }, executor))
                .toList();

        for (CompletableFuture<SourceResult> future : futures) {
            try {
                SourceResult sr = future.join();
                if (sr.error() == null && sr.result() != null) {
                    totalCollected += sr.result().collectedCount();
                    allArticles.addAll(sr.result().articles());

                    sourceReports.add(new SourceCollectionReport(
                            sr.adapter().getSourceName(),
                            SourceCollectionReport.CollectionStatus.SUCCESS,
                            sr.result().collectedCount(),
                            sr.result().acceptedCount(),
                            null, null,
                            sr.result().collectedCount(), null, null, null,
                            sr.durationMs()
                    ));
                    log.info("Collected {} articles from {} ({}ms)",
                            sr.result().acceptedCount(), sr.adapter().getSourceName(), sr.durationMs());
                } else {
                    String errorMsg = sr.error() != null ? sr.error().getMessage() : "Unknown error";
                    sourceReports.add(new SourceCollectionReport(
                            sr.adapter().getSourceName(),
                            SourceCollectionReport.CollectionStatus.FAILED,
                            0, 0,
                            "COLLECT_SOURCE_UNAVAILABLE", errorMsg,
                            null, null, null, null, sr.durationMs()
                    ));
                    log.warn("Failed to collect from {}: {}", sr.adapter().getSourceName(), errorMsg);
                }
            } catch (Exception e) {
                log.error("Unexpected error processing source result", e);
            }
        }

        // Apply date filter (secondary check)
        DateFilter.DateFilterResult dateResult = dateFilter.filter(allArticles, startTime, endTime);
        allArticles = new ArrayList<>(dateResult.accepted());
        int dateRejected = dateResult.rejected().size();

        // Apply quality validation
        QualityValidator.QualityValidationResult qualityResult = qualityValidator.validate(allArticles);
        allArticles = new ArrayList<>(qualityResult.valid());
        int qualityRejected = qualityResult.invalid().size();

        // Remove duplicates
        DuplicateRemover.DeduplicationResult dedupeResult = duplicateRemover.removeDuplicates(allArticles);
        allArticles = new ArrayList<>(dedupeResult.unique());
        int duplicateRejected = dedupeResult.duplicates().size();

        // Apply maxArticles limit
        if (request.maxArticles() != null && allArticles.size() > request.maxArticles()) {
            allArticles = allArticles.subList(0, request.maxArticles());
        }

        int totalRejected = dateRejected + qualityRejected + duplicateRejected;

        return new CollectNewsResult(
                request.targetDate(),
                allArticles,
                sourceReports,
                totalCollected,
                allArticles.size(),
                totalRejected
        );
    }

    private record SourceResult(
            SourceAdapter adapter,
            SourceAdapter.SourceCollectionResult result,
            long durationMs,
            Exception error
    ) {}
}

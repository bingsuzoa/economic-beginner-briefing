package com.economicbriefing.collector.dto;

import java.time.LocalDate;
import java.util.List;

import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.SourceCollectionReport;

public record CollectNewsResult(
    LocalDate targetDate,
    List<Article> articles,
    List<SourceCollectionReport> sourceReports,
    int totalCollected,
    int totalAccepted,
    int totalRejected
) {}

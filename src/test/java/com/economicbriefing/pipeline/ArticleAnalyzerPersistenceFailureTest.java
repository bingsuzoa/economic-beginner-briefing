package com.economicbriefing.pipeline;

import java.time.LocalDate;

import com.economicbriefing.analyzer.NewsAnalyzer;
import com.economicbriefing.classifier.repository.ArticleAnalyzerResultRepository;
import com.economicbriefing.domain.execution.ExecutionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ArticleAnalyzerPersistenceFailureTest {

    @Autowired private BriefingPipeline pipeline;
    @Autowired private ArticleAnalyzerResultRepository repository;
    @MockitoBean private NewsAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void analyzerFailureDoesNotCreateResultRows() {
        when(analyzer.analyze(any())).thenThrow(new IllegalStateException("analyzer failed"));

        var result = pipeline.run(PipelineOptions.manual(LocalDate.of(2025, 4, 1)));

        assertEquals(ExecutionStatus.FAILED, result.getStatus());
        assertEquals(0, repository.count());
    }
}

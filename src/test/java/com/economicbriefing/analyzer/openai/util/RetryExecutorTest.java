package com.economicbriefing.analyzer.openai.util;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.economicbriefing.config.AppProperties;
import com.economicbriefing.exception.AnalyzeException;
import com.economicbriefing.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RetryExecutorTest {

    private final AppProperties.RetryProperties retryConfig = new AppProperties.RetryProperties(
            3, Duration.ofMillis(10), Duration.ofMillis(20));

    @Test
    void shouldReturnResultOnSuccess() {
        String result = RetryExecutor.execute(() -> "success", retryConfig);
        assertEquals("success", result);
    }

    @Test
    void shouldRetryOnRetryableError() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = RetryExecutor.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new AnalyzeException(ErrorCode.ANALYZE_API_ERROR);
            }
            return "success after retry";
        }, retryConfig);

        assertEquals("success after retry", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void shouldNotRetryOnNonRetryableError() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(AnalyzeException.class, () ->
                RetryExecutor.execute(() -> {
                    attempts.incrementAndGet();
                    throw new AnalyzeException(ErrorCode.ANALYZE_EMPTY_INPUT);
                }, retryConfig)
        );

        assertEquals(1, attempts.get());
    }

    @Test
    void shouldThrowAfterMaxAttempts() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(AnalyzeException.class, () ->
                RetryExecutor.execute(() -> {
                    attempts.incrementAndGet();
                    throw new AnalyzeException(ErrorCode.ANALYZE_API_ERROR);
                }, retryConfig)
        );

        assertEquals(3, attempts.get());
    }

    @Test
    void shouldNotRetryNonBriefingExceptions() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(RuntimeException.class, () ->
                RetryExecutor.execute(() -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("unexpected");
                }, retryConfig)
        );

        assertEquals(1, attempts.get());
    }
}

package com.economicbriefing.analyzer.openai.util;

import java.time.Duration;
import java.util.function.Supplier;

import com.economicbriefing.config.AppProperties;
import com.economicbriefing.exception.BriefingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetryExecutor.class);

    private RetryExecutor() {}

    public static <T> T execute(Supplier<T> fn, AppProperties.RetryProperties retryConfig) {
        int maxAttempts = retryConfig.maxAttempts();
        Duration initialDelay = retryConfig.initialDelay();
        Duration nextDelay = retryConfig.nextDelay();

        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return fn.get();
            } catch (RuntimeException e) {
                lastError = e;

                boolean isLastAttempt = attempt >= maxAttempts;
                if (isLastAttempt) {
                    break;
                }

                boolean isRetryable = e instanceof BriefingException be && be.getErrorCode().isRetryable();
                if (!isRetryable) {
                    break;
                }

                Duration delay = attempt == 1 ? initialDelay : nextDelay;
                log.warn("Attempt {}/{} failed, retrying after {}ms: {}",
                        attempt, maxAttempts, delay.toMillis(), e.getMessage());
                sleep(delay);
            }
        }

        throw lastError;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry interrupted", e);
        }
    }
}

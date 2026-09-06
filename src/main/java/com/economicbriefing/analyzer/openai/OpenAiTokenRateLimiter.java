package com.economicbriefing.analyzer.openai;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Keeps the application's own OpenAI traffic below a model's rolling TPM window.
 * A request reserves a conservative number of tokens before it is sent; successful
 * responses replace that reservation with the API's actual usage figure.
 */
@Component
public class OpenAiTokenRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiTokenRateLimiter.class);

    private final int safeTokensPerMinute;
    private final Duration window;
    private final Map<String, Deque<TokenEntry>> entriesByModel = new HashMap<>();
    private final Map<String, Long> blockedUntilByModel = new HashMap<>();

    public OpenAiTokenRateLimiter(
            @Value("${openai.rate-limit.safe-tokens-per-minute:24000}") int safeTokensPerMinute,
            @Value("${openai.rate-limit.window:60s}") Duration window) {
        if (safeTokensPerMinute <= 0 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("OpenAI rate-limit settings must be positive");
        }
        this.safeTokensPerMinute = safeTokensPerMinute;
        this.window = window;
    }

    /** Compatibility constructor used by focused unit tests outside Spring. */
    OpenAiTokenRateLimiter() {
        this(24_000, Duration.ofSeconds(60));
    }

    TokenReservation acquire(String model, int estimatedTokens) throws InterruptedException {
        int reservationTokens = Math.min(Math.max(1, estimatedTokens), safeTokensPerMinute);
        while (true) {
            long waitMillis;
            synchronized (this) {
                long now = System.currentTimeMillis();
                Deque<TokenEntry> entries = entriesByModel.computeIfAbsent(model, ignored -> new ArrayDeque<>());
                discardExpired(entries, now);

                long cooldownMillis = Math.max(0, blockedUntilByModel.getOrDefault(model, 0L) - now);
                int used = entries.stream().mapToInt(entry -> entry.tokens).sum();
                if (cooldownMillis == 0 && used + reservationTokens <= safeTokensPerMinute) {
                    TokenEntry entry = new TokenEntry(now, reservationTokens);
                    entries.addLast(entry);
                    return new TokenReservation(model, entry);
                }

                long capacityWaitMillis = entries.isEmpty()
                        ? 0
                        : Math.max(1, entries.peekFirst().recordedAtMillis + window.toMillis() - now);
                waitMillis = Math.max(cooldownMillis, capacityWaitMillis);
                // A single over-sized prompt must not spin forever. It gets the whole safe window.
                if (waitMillis == 0) {
                    waitMillis = window.toMillis();
                }
                log.info("OpenAI TPM limiter waiting {}ms for model={} (reserved={}, used={}, safeLimit={})",
                        waitMillis, model, reservationTokens, used, safeTokensPerMinute);
            }
            Thread.sleep(waitMillis);
        }
    }

    synchronized void recordSuccess(TokenReservation reservation, int actualTokens) {
        reservation.entry.tokens = Math.max(1, actualTokens);
    }

    synchronized void recordRateLimited(String model, Duration retryAfter) {
        if (retryAfter == null || retryAfter.isNegative() || retryAfter.isZero()) {
            return;
        }
        long until = System.currentTimeMillis() + retryAfter.toMillis();
        blockedUntilByModel.merge(model, until, Math::max);
    }

    static int estimateTokens(String systemPrompt, String userPrompt, String schema) {
        int characters = systemPrompt.length() + userPrompt.length() + (schema == null ? 0 : schema.length());
        // Korean and JSON generally tokenize more densely than English words.  Keeping nearly one
        // token per character plus an output reserve biases toward waiting instead of receiving 429.
        return Math.max(1_024, (int) Math.ceil(characters * 0.9d) + 2_048);
    }

    private void discardExpired(Deque<TokenEntry> entries, long now) {
        long cutoff = now - window.toMillis();
        while (!entries.isEmpty() && entries.peekFirst().recordedAtMillis <= cutoff) {
            entries.removeFirst();
        }
    }

    static final class TokenReservation {
        private final String model;
        private final TokenEntry entry;

        private TokenReservation(String model, TokenEntry entry) {
            this.model = model;
            this.entry = entry;
        }
    }

    private static final class TokenEntry {
        private final long recordedAtMillis;
        private int tokens;

        private TokenEntry(long recordedAtMillis, int tokens) {
            this.recordedAtMillis = recordedAtMillis;
            this.tokens = tokens;
        }
    }
}

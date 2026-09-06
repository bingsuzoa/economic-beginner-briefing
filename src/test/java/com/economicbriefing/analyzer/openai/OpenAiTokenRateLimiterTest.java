package com.economicbriefing.analyzer.openai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OpenAiTokenRateLimiterTest {

    @Test
    void shouldReserveMoreThanPromptCharactersAlone() {
        int estimate = OpenAiTokenRateLimiter.estimateTokens("system", "가나다라마바사", null);

        assertTrue(estimate >= 2_048, "completion reserve must be included in every request");
    }

    @Test
    void shouldIncludeSchemaInTokenEstimate() {
        int withoutSchema = OpenAiTokenRateLimiter.estimateTokens("system", "user", null);
        int withSchema = OpenAiTokenRateLimiter.estimateTokens("system", "user", "x".repeat(5_000));

        assertTrue(withSchema > withoutSchema);
    }
}

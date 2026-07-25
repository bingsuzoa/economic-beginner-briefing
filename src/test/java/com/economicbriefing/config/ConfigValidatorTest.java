package com.economicbriefing.config;

import com.economicbriefing.exception.BriefingException;
import com.economicbriefing.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigValidatorTest {

    private final OpenAiProperties validOpenAi = new OpenAiProperties("key", "gpt-4o", 0.3, Duration.ofSeconds(60), 10);
    private final AdminProperties validAdmin = new AdminProperties("admin-token", 20);

    private AppProperties createAppProperties(boolean dryRun) {
        return new AppProperties(
                dryRun,
                new AppProperties.TimeoutProperties(Duration.ofSeconds(10), Duration.ofSeconds(60)),
                new AppProperties.RetryProperties(2, Duration.ofSeconds(1), Duration.ofSeconds(2)),
                new AppProperties.DiversityProperties(3, 3, 3, 5),
                new AppProperties.AudienceProperties("beginner", List.of("interest_rate"), List.of("신혼부부")),
                new AppProperties.SchedulerProperties(false, "0 0 * * * *"),
                new AppProperties.TeacherProperties(true, "teacher-v1", "gpt-4o-mini", 6),
                new AppProperties.EmbeddingProperties(false, "text-embedding-3-small", 1536)
        );
    }

    @Test
    void shouldPassInDryRunMode() {
        ConfigValidator validator = new ConfigValidator(
                createAppProperties(true),
                new OpenAiProperties("", "gpt-4o", 0.3, Duration.ofSeconds(60), 10),
                new AdminProperties("", 20)
        );

        assertDoesNotThrow(validator::validate);
    }

    @Test
    void shouldPassWhenAllKeysPresent() {
        ConfigValidator validator = new ConfigValidator(
                createAppProperties(false), validOpenAi, validAdmin);

        assertDoesNotThrow(validator::validate);
    }

    @Test
    void shouldFailWhenOpenAiKeyMissing() {
        ConfigValidator validator = new ConfigValidator(
                createAppProperties(false),
                new OpenAiProperties("", "gpt-4o", 0.3, Duration.ofSeconds(60), 10),
                validAdmin
        );

        BriefingException ex = assertThrows(BriefingException.class, validator::validate);
        assertEquals(ErrorCode.SYSTEM_CONFIG_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("OPENAI_API_KEY"));
    }

    // The admin API can trigger runs, so an unset token must stop startup rather than
    // silently leave the endpoints open.
    @Test
    void shouldFailWhenAdminTokenMissing() {
        ConfigValidator validator = new ConfigValidator(
                createAppProperties(false), validOpenAi, new AdminProperties("", 20));

        BriefingException ex = assertThrows(BriefingException.class, validator::validate);
        assertEquals(ErrorCode.SYSTEM_CONFIG_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("ADMIN_TOKEN"));
    }

    @Test
    void shouldFailWhenAdminTokenIsBlank() {
        ConfigValidator validator = new ConfigValidator(
                createAppProperties(false), validOpenAi, new AdminProperties("   ", 20));

        assertThrows(BriefingException.class, validator::validate);
    }
}

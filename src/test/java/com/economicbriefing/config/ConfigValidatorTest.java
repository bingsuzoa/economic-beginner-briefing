package com.economicbriefing.config;

import com.economicbriefing.exception.BriefingException;
import com.economicbriefing.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigValidatorTest {

    private final NotionProperties validNotion = new NotionProperties("key", "db-id", Duration.ofSeconds(15));
    private final OpenAiProperties validOpenAi = new OpenAiProperties("key", "gpt-4o", 0.3, Duration.ofSeconds(60), 10);

    private AppProperties createAppProperties(boolean dryRun) {
        return new AppProperties(
                dryRun,
                new AppProperties.TimeoutProperties(Duration.ofSeconds(10), Duration.ofSeconds(60), Duration.ofSeconds(15), Duration.ofSeconds(15)),
                new AppProperties.RetryProperties(2, Duration.ofSeconds(1), Duration.ofSeconds(2)),
                new AppProperties.DiversityProperties(3, 3, 3, 5),
                new AppProperties.AudienceProperties("beginner", List.of("interest_rate"), List.of("신혼부부")),
                new AppProperties.SchedulerProperties(false, "0 0 * * * *")
        );
    }

    @Test
    void shouldPassInDryRunMode() {
        ConfigValidator validator = new ConfigValidator(
                createAppProperties(true),
                new OpenAiProperties("", "gpt-4o", 0.3, Duration.ofSeconds(60), 10),
                new NotionProperties("", "", Duration.ofSeconds(15))
        );

        assertDoesNotThrow(validator::validate);
    }

    @Test
    void shouldPassWhenAllKeysPresent() {
        ConfigValidator validator = new ConfigValidator(
                createAppProperties(false), validOpenAi, validNotion);

        assertDoesNotThrow(validator::validate);
    }

    @Test
    void shouldFailWhenOpenAiKeyMissing() {
        ConfigValidator validator = new ConfigValidator(
                createAppProperties(false),
                new OpenAiProperties("", "gpt-4o", 0.3, Duration.ofSeconds(60), 10),
                validNotion
        );

        BriefingException ex = assertThrows(BriefingException.class, validator::validate);
        assertEquals(ErrorCode.SYSTEM_CONFIG_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("OPENAI_API_KEY"));
    }

    @Test
    void shouldFailWhenNotionKeyMissing() {
        ConfigValidator validator = new ConfigValidator(
                createAppProperties(false),
                validOpenAi,
                new NotionProperties("", "db-id", Duration.ofSeconds(15))
        );

        BriefingException ex = assertThrows(BriefingException.class, validator::validate);
        assertEquals(ErrorCode.SYSTEM_CONFIG_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("NOTION_API_KEY"));
    }

    @Test
    void shouldFailWhenNotionDbIdMissing() {
        ConfigValidator validator = new ConfigValidator(
                createAppProperties(false),
                validOpenAi,
                new NotionProperties("key", "", Duration.ofSeconds(15))
        );

        BriefingException ex = assertThrows(BriefingException.class, validator::validate);
        assertEquals(ErrorCode.SYSTEM_CONFIG_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("NOTION_DATABASE_ID"));
    }
}

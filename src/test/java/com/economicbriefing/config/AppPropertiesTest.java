package com.economicbriefing.config;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AppPropertiesTest {

    @Autowired
    private AppProperties appProperties;

    @Test
    void shouldBindDryRun() {
        assertTrue(appProperties.dryRun());
    }

    @Test
    void shouldBindTimeouts() {
        AppProperties.TimeoutProperties timeouts = appProperties.timeouts();
        assertNotNull(timeouts);
        assertEquals(Duration.ofSeconds(10), timeouts.rssHttp());
        assertEquals(Duration.ofSeconds(60), timeouts.aiApi());
        assertEquals(Duration.ofSeconds(15), timeouts.notionApi());
        assertEquals(Duration.ofSeconds(15), timeouts.emailApi());
    }

    @Test
    void shouldBindRetry() {
        AppProperties.RetryProperties retry = appProperties.retry();
        assertNotNull(retry);
        assertEquals(2, retry.maxAttempts());
        assertEquals(Duration.ofSeconds(1), retry.initialDelay());
        assertEquals(Duration.ofSeconds(2), retry.nextDelay());
    }

    @Test
    void shouldBindDiversity() {
        AppProperties.DiversityProperties diversity = appProperties.diversity();
        assertNotNull(diversity);
        assertEquals(3, diversity.maxArticlesPerSource());
        assertEquals(3, diversity.maxArticlesPerCategory());
        assertEquals(3, diversity.minPersonalFinanceRelevance());
        assertEquals(5, diversity.softMaxOverrideScore());
    }

    @Test
    void shouldBindAudience() {
        AppProperties.AudienceProperties audience = appProperties.audience();
        assertNotNull(audience);
        assertEquals("beginner", audience.economicKnowledgeLevel());
        assertFalse(audience.interests().isEmpty());
        assertTrue(audience.interests().contains("interest_rate"));
        assertTrue(audience.interests().contains("housing"));
    }

    @Test
    void shouldBindScheduler() {
        AppProperties.SchedulerProperties scheduler = appProperties.scheduler();
        assertNotNull(scheduler);
        assertFalse(scheduler.enabled());
        assertEquals("0 0 * * * *", scheduler.cron());
    }
}

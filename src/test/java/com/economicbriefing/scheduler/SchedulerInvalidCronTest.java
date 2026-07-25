package com.economicbriefing.scheduler;

import com.economicbriefing.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A typo in briefing.scheduler.cron used to abort the application context, which took the
 * public briefing API down with it. The scheduler is enabled here with a 4-field cron.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "briefing.scheduler.enabled=true",
    "briefing.scheduler.cron=0 0 * *"
})
class SchedulerInvalidCronTest {

    @Autowired private AppProperties appProperties;
    @Autowired private ScheduledAnnotationBeanPostProcessor postProcessor;

    /** Reaching an @Autowired field at all proves the context started. */
    @Test
    void shouldStartTheApplicationDespiteAnInvalidCron() {
        assertNotNull(appProperties);
    }

    @Test
    void shouldNotArmAnyTrigger() {
        assertTrue(postProcessor.getScheduledTasks().isEmpty(),
                "an invalid cron must leave the hourly trigger unarmed");
    }

    @Test
    void shouldReportMisconfiguredState() {
        AppProperties.SchedulerProperties scheduler = appProperties.scheduler();

        assertFalse(scheduler.cronValid());
        assertFalse(scheduler.active(), "enabled but unusable is not active");
        assertEquals("MISCONFIGURED", scheduler.state());
    }
}

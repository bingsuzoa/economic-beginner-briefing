package com.economicbriefing.scheduler;

import java.util.List;
import java.util.Set;

import com.economicbriefing.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The desktop runs application.yml with no profile, so the shipped defaults alone must arm
 * the hourly trigger. Deliberately no @ActiveProfiles and no property overrides — this test
 * fails if the scheduler ever goes back to needing a profile to switch on.
 *
 * <p>Datasource credentials are supplied because application.yml requires them; nothing here
 * touches scheduling.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:defaultcfg;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "openai.api-key=test-key",
    "admin.token=test-admin-token",
    "auth.email-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "auth.email-hash-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
    // stops the real pipeline from firing if a tick lands mid-test
    "briefing.dry-run=true",
    "spring.mail.host=localhost",
    "spring.mail.port=25"
})
class DefaultConfigSchedulerTest {

    @Autowired private ApplicationContext context;
    @Autowired private AppProperties appProperties;
    @Autowired private ScheduledAnnotationBeanPostProcessor postProcessor;

    @Test
    void shouldEnableSchedulerWithoutAnyProfile() {
        assertTrue(appProperties.scheduler().enabled(),
                "application.yml alone must enable the scheduler");
        assertEquals("0 0 * * * *", appProperties.scheduler().cron());
        assertEquals("ENABLED", appProperties.scheduler().state());
        assertTrue(appProperties.scheduler().active());
    }

    @Test
    void shouldRegisterSchedulerBeans() {
        assertTrue(context.containsBean("schedulingConfig"));
        assertEquals(1, context.getBeansOfType(BriefingScheduler.class).size());
    }

    @Test
    void shouldArmTheHourlyTrigger() {
        List<String> crons = postProcessor.getScheduledTasks().stream()
                .map(ScheduledTask::getTask)
                .filter(CronTask.class::isInstance)
                .map(task -> ((CronTask) task).getExpression())
                .toList();

        assertEquals(Set.of("0 0 * * * *", "0 0 9,18 * * *", "0 30 18 * * *"), Set.copyOf(crons));
    }
}

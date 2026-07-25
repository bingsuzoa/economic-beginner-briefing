package com.economicbriefing.scheduler;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Requirement 4 + "Scheduler가 정상 등록되는지": with the scheduler on, the cron from
 * configuration must actually reach the registered task.
 *
 * <p>The cron here is 03:00 on 1 January, so enabling scheduling inside a test can never
 * fire a real pipeline run.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "briefing.scheduler.enabled=true",
    "briefing.scheduler.cron=0 0 3 1 1 *"
})
class SchedulerRegistrationTest {

    @Autowired private ApplicationContext context;
    @Autowired private ScheduledAnnotationBeanPostProcessor postProcessor;

    @Test
    void shouldRegisterSchedulingWhenEnabled() {
        assertTrue(context.containsBean("schedulingConfig"));
        assertEquals(1, context.getBeansOfType(BriefingScheduler.class).size());
    }

    @Test
    void shouldRegisterTheConfiguredCronExpression() {
        List<String> crons = postProcessor.getScheduledTasks().stream()
                .map(ScheduledTask::getTask)
                .filter(CronTask.class::isInstance)
                .map(task -> ((CronTask) task).getExpression())
                .toList();

        assertTrue(crons.contains("0 0 3 1 1 *"),
                "configured cron should be registered, but found: " + crons);
    }
}

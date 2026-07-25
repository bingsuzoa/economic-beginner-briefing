package com.economicbriefing.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Requirement 5: enabled=false must mean nothing can fire.
 * The test profile sets briefing.scheduler.enabled=false.
 */
@SpringBootTest
@ActiveProfiles("test")
class SchedulingConfigTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void shouldNotEnableSchedulingWhenDisabled() {
        assertFalse(context.containsBean("schedulingConfig"));
    }

    /**
     * The scheduler bean itself must be absent too. Relying only on @EnableScheduling being
     * off is fragile: anything else switching scheduling on would silently start firing
     * an hourly pipeline run.
     */
    @Test
    void shouldNotCreateSchedulerBeanWhenDisabled() {
        assertTrue(context.getBeansOfType(BriefingScheduler.class).isEmpty(),
                "BriefingScheduler must not exist while the scheduler is disabled");
    }
}

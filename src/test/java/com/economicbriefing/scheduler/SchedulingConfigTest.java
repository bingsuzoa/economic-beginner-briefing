package com.economicbriefing.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SchedulingConfigTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void shouldNotEnableSchedulingWhenDisabled() {
        // In test profile, scheduler.enabled=false, so SchedulingConfig should not be loaded
        assertFalse(context.containsBean("schedulingConfig"));
    }

    @Test
    void shouldHaveBriefingSchedulerBean() {
        // BriefingScheduler itself is a @Component so it should exist
        assertTrue(context.containsBean("briefingScheduler"));
    }
}

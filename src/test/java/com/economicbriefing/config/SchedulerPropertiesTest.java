package com.economicbriefing.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchedulerPropertiesTest {

    private static AppProperties.SchedulerProperties props(boolean enabled, String cron) {
        return new AppProperties.SchedulerProperties(enabled, cron);
    }

    @Test
    void shouldAcceptSixFieldCron() {
        assertTrue(props(true, "0 0 * * * *").cronValid());
        assertTrue(props(true, "0 */30 * * * *").cronValid());
        assertEquals("ENABLED", props(true, "0 0 * * * *").state());
        assertTrue(props(true, "0 0 * * * *").active());
    }

    /** Five fields is the classic mistake: valid in unix crontab, rejected by Spring. */
    @Test
    void shouldRejectUnixStyleFiveFieldCron() {
        assertFalse(props(true, "0 * * * *").cronValid());
        assertEquals("MISCONFIGURED", props(true, "0 * * * *").state());
        assertFalse(props(true, "0 * * * *").active());
    }

    @Test
    void shouldRejectGarbageAndNull() {
        assertFalse(props(true, "not-a-cron").cronValid());
        assertFalse(props(true, "").cronValid());
        assertFalse(props(true, null).cronValid());
        assertFalse(props(true, "99 0 * * * *").cronValid());
    }

    @Test
    void shouldReportDisabledRegardlessOfCron() {
        assertEquals("DISABLED", props(false, "0 0 * * * *").state());
        assertEquals("DISABLED", props(false, "garbage").state());
        assertFalse(props(false, "0 0 * * * *").active());
    }
}

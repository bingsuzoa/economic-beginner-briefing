package com.economicbriefing.cli;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class BriefingCliRunnerTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void shouldNotLoadCliRunnerWhenDisabled() {
        // CLI runner requires briefing.cli.enabled=true, which is not set in test profile
        assertFalse(context.containsBean("briefingCliRunner"));
    }
}

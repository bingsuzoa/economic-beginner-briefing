package com.economicbriefing.admin.controller;

import java.time.Duration;
import java.util.List;

import com.economicbriefing.config.AdminProperties;
import com.economicbriefing.config.AppProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class AdminTestConfig {

    @Bean
    public AdminProperties adminProperties() {
        return new AdminProperties("test-admin-token", 20);
    }

    @Bean
    public AppProperties appProperties() {
        return new AppProperties(
                true,
                new AppProperties.TimeoutProperties(Duration.ofSeconds(10), Duration.ofSeconds(60)),
                new AppProperties.RetryProperties(3, Duration.ofSeconds(1), Duration.ofSeconds(2)),
                new AppProperties.DiversityProperties(3, 3, 3, 5),
                new AppProperties.AudienceProperties("beginner", List.of("interest_rate"), List.of()),
                new AppProperties.SchedulerProperties(false, "0 0 * * * *"),
                new AppProperties.TeacherProperties(true, "teacher-v1", "gpt-4o-mini", 6),
                new AppProperties.EmbeddingProperties(false, "text-embedding-3-small", 1536)
        );
    }
}

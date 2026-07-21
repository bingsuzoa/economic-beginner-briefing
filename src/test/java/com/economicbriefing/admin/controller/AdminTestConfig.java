package com.economicbriefing.admin.controller;

import com.economicbriefing.config.AdminProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class AdminTestConfig {

    @Bean
    public AdminProperties adminProperties() {
        return new AdminProperties("test-admin-token", 20);
    }
}

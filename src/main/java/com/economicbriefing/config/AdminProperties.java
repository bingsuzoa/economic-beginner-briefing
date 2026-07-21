package com.economicbriefing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "admin")
public record AdminProperties(
    String token,
    int defaultPageSize
) {}

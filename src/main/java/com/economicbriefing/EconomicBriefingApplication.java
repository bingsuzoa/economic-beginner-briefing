package com.economicbriefing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EconomicBriefingApplication {

    public static void main(String[] args) {
        SpringApplication.run(EconomicBriefingApplication.class, args);
    }
}

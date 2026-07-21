package com.economicbriefing.config;

import com.economicbriefing.exception.BriefingException;
import com.economicbriefing.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(ConfigValidator.class);

    private final AppProperties appProperties;
    private final OpenAiProperties openAiProperties;
    private final NotionProperties notionProperties;

    public ConfigValidator(AppProperties appProperties,
                           OpenAiProperties openAiProperties,
                           NotionProperties notionProperties) {
        this.appProperties = appProperties;
        this.openAiProperties = openAiProperties;
        this.notionProperties = notionProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        if (appProperties.dryRun()) {
            log.info("Dry-run mode: skipping external API config validation");
            return;
        }

        if (isBlank(openAiProperties.apiKey())) {
            throw new BriefingException(ErrorCode.SYSTEM_CONFIG_ERROR, "system",
                    "OPENAI_API_KEY is required when dry-run is disabled");
        }

        if (isBlank(notionProperties.apiKey())) {
            throw new BriefingException(ErrorCode.SYSTEM_CONFIG_ERROR, "system",
                    "NOTION_API_KEY is required when dry-run is disabled");
        }

        if (isBlank(notionProperties.databaseId())) {
            throw new BriefingException(ErrorCode.SYSTEM_CONFIG_ERROR, "system",
                    "NOTION_DATABASE_ID is required when dry-run is disabled");
        }

        log.info("Configuration validated: all required API keys present");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

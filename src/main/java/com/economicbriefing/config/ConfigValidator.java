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
    private final AdminProperties adminProperties;

    public ConfigValidator(AppProperties appProperties,
                           OpenAiProperties openAiProperties,
                           AdminProperties adminProperties) {
        this.appProperties = appProperties;
        this.openAiProperties = openAiProperties;
        this.adminProperties = adminProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        logSchedulerState();

        if (appProperties.dryRun()) {
            log.info("Dry-run mode: skipping external API config validation");
            return;
        }

        if (isBlank(openAiProperties.apiKey())) {
            throw new BriefingException(ErrorCode.SYSTEM_CONFIG_ERROR, "system",
                    "OPENAI_API_KEY is required when dry-run is disabled");
        }

        // The admin API can trigger pipeline runs and expose run history. Starting without
        // a token would leave it open, so refuse to start rather than silently allow all.
        if (isBlank(adminProperties.token())) {
            throw new BriefingException(ErrorCode.SYSTEM_CONFIG_ERROR, "system",
                    "ADMIN_TOKEN is required when dry-run is disabled");
        }

        log.info("Configuration validated: OpenAI API key and admin token present");
    }

    /** Stated on every boot: whether an unattended hourly run is armed is not something an
     *  operator should have to infer from the absence of a log line. */
    private void logSchedulerState() {
        AppProperties.SchedulerProperties scheduler = appProperties.scheduler();
        if (scheduler == null || !scheduler.enabled()) {
            log.info("[Scheduler] DISABLED (briefing.scheduler.enabled=false)");
        } else if (scheduler.cronValid()) {
            log.info("[Scheduler] ENABLED cron='{}' zone=Asia/Seoul", scheduler.cron());
        } else {
            log.error("[Scheduler] MISCONFIGURED cron='{}' - hourly briefings will NOT run",
                    scheduler.cron());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

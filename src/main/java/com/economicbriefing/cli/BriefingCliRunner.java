package com.economicbriefing.cli;

import java.time.LocalDate;

import com.economicbriefing.domain.execution.ExecutionLog;
import com.economicbriefing.pipeline.BriefingPipeline;
import com.economicbriefing.pipeline.PipelineOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "briefing.cli.enabled", havingValue = "true")
public class BriefingCliRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BriefingCliRunner.class);

    private final BriefingPipeline pipeline;
    private final ObjectMapper objectMapper;

    public BriefingCliRunner(BriefingPipeline pipeline, ObjectMapper objectMapper) {
        this.pipeline = pipeline;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        String targetDateStr = null;
        String mode = "automatic";

        // Parse command-line arguments
        for (String arg : args) {
            if (arg.startsWith("--target-date=")) {
                targetDateStr = arg.substring("--target-date=".length());
            }
        }

        // Also check environment variable
        if (targetDateStr == null) {
            targetDateStr = System.getenv("TARGET_DATE");
        }

        // Determine mode from GitHub Actions event
        String githubEvent = System.getenv("GITHUB_EVENT_NAME");
        if ("workflow_dispatch".equals(githubEvent)) {
            mode = "manual";
        }

        log.info("CLI runner started: targetDate={}, mode={}", targetDateStr, mode);

        PipelineOptions options;
        if (targetDateStr != null && !targetDateStr.isBlank()) {
            try {
                LocalDate targetDate = LocalDate.parse(targetDateStr);
                options = PipelineOptions.manual(targetDate);
            } catch (Exception e) {
                log.error("Invalid target date format: {}", targetDateStr);
                System.err.println("Error: Invalid target date format: " + targetDateStr);
                System.exit(1);
                return;
            }
        } else {
            options = PipelineOptions.hourly();
        }

        ExecutionLog result = pipeline.run(options);

        // Output JSON result
        CliResult cliResult = new CliResult(
                mode,
                options.triggerType(),
                result.getTargetDate().toString(),
                result.getStatus().name(),
                result.getCollectedArticleCount(),
                result.getSelectedNewsCount(),
                result.getErrors().size()
        );

        ObjectMapper printer = objectMapper.copy();
        printer.registerModule(new JavaTimeModule());
        printer.enable(SerializationFeature.INDENT_OUTPUT);
        printer.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        System.out.println("=== Briefing Result ===");
        System.out.println(printer.writeValueAsString(cliResult));

        boolean success = switch (result.getStatus()) {
            case SUCCESS, PARTIAL_SUCCESS -> true;
            case FAILED, RUNNING -> false;
        };

        if (!success) {
            log.error("Pipeline finished with failure status: {}", result.getStatus());
            System.exit(1);
        }

        log.info("CLI runner completed successfully");
        System.exit(0);
    }

    record CliResult(
        String mode,
        String triggerType,
        String targetDate,
        String status,
        int collectedArticleCount,
        int selectedNewsCount,
        int errorCount
    ) {}
}

package com.economicbriefing.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the CLI output JSON format matches the expected structure
 * used by Node.js version for compatibility.
 */
class CliOutputFormatTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void shouldSerializeCliResultWithExpectedFields() throws Exception {
        BriefingCliRunner.CliResult result = new BriefingCliRunner.CliResult(
                "automatic",
                "SCHEDULER",
                "2025-01-15",
                "SUCCESS",
                25,
                5,
                0
        );

        String json = objectMapper.writeValueAsString(result);
        JsonNode node = objectMapper.readTree(json);

        assertTrue(node.has("mode"), "Should have 'mode' field");
        assertTrue(node.has("triggerType"), "Should have 'triggerType' field");
        assertTrue(node.has("targetDate"), "Should have 'targetDate' field");
        assertTrue(node.has("status"), "Should have 'status' field");
        assertTrue(node.has("collectedArticleCount"), "Should have 'collectedArticleCount' field");
        assertTrue(node.has("selectedNewsCount"), "Should have 'selectedNewsCount' field");
        assertTrue(node.has("errorCount"), "Should have 'errorCount' field");

        assertEquals("automatic", node.get("mode").asText());
        assertEquals("SCHEDULER", node.get("triggerType").asText());
        assertEquals("2025-01-15", node.get("targetDate").asText());
        assertEquals("SUCCESS", node.get("status").asText());
        assertEquals(25, node.get("collectedArticleCount").asInt());
        assertEquals(5, node.get("selectedNewsCount").asInt());
        assertEquals(0, node.get("errorCount").asInt());
    }

    @Test
    void shouldHandleManualMode() throws Exception {
        BriefingCliRunner.CliResult result = new BriefingCliRunner.CliResult(
                "manual",
                "MANUAL",
                "2025-06-01",
                "PARTIAL_SUCCESS",
                10,
                3,
                1
        );

        String json = objectMapper.writeValueAsString(result);
        JsonNode node = objectMapper.readTree(json);

        assertEquals("manual", node.get("mode").asText());
        assertEquals("MANUAL", node.get("triggerType").asText());
        assertEquals(1, node.get("errorCount").asInt());
    }

    @Test
    void shouldHandleFailedStatus() throws Exception {
        BriefingCliRunner.CliResult result = new BriefingCliRunner.CliResult(
                "automatic",
                "SCHEDULER",
                "2025-01-20",
                "FAILED",
                0,
                0,
                2
        );

        String json = objectMapper.writeValueAsString(result);
        JsonNode node = objectMapper.readTree(json);

        assertEquals("FAILED", node.get("status").asText());
        assertEquals(0, node.get("collectedArticleCount").asInt());
        assertEquals(2, node.get("errorCount").asInt());
    }
}

package com.economicbriefing.classifier;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TeacherLabelResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeFromJson() throws Exception {
        String json = """
                {
                  "label": "RELEVANT",
                  "reason": "금리 인상은 가계 대출에 직접 영향",
                  "affected_areas": ["금리", "대출"],
                  "severity": "HIGH",
                  "needs_follow_up": false,
                  "confidence": 0.95,
                  "usable_for_training": true
                }
                """;

        TeacherLabelResponse response = objectMapper.readValue(json, TeacherLabelResponse.class);

        assertEquals("RELEVANT", response.label());
        assertEquals("HIGH", response.severity());
        assertEquals(0.95, response.confidence(), 0.001);
        assertEquals(List.of("금리", "대출"), response.affectedAreas());
        assertFalse(response.needsFollowUp());
        assertTrue(response.usableForTraining());
    }

    @Test
    void shouldHandleUnknownFields() throws Exception {
        String json = """
                {
                  "label": "IRRELEVANT",
                  "reason": "스포츠 기사",
                  "affected_areas": [],
                  "severity": "UNKNOWN",
                  "needs_follow_up": false,
                  "confidence": 0.8,
                  "usable_for_training": true,
                  "extra_field": "should be ignored"
                }
                """;

        TeacherLabelResponse response = objectMapper.readValue(json, TeacherLabelResponse.class);

        assertEquals("IRRELEVANT", response.label());
    }
}

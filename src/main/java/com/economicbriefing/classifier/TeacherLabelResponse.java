package com.economicbriefing.classifier;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TeacherLabelResponse(
    String label,
    String reason,
    @JsonProperty("affected_areas") List<String> affectedAreas,
    String severity,
    @JsonProperty("needs_follow_up") boolean needsFollowUp,
    double confidence,
    @JsonProperty("usable_for_training") boolean usableForTraining
) {}

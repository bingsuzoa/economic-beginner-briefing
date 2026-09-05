package com.economicbriefing.analyzer.openai.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SelectionResponse(
        List<Integer> selectedArticleIndexes
) {}

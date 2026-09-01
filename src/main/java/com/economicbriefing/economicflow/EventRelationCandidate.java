package com.economicbriefing.economicflow;

import com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse;

public record EventRelationCandidate(
        String articleId,
        String fromCandidateKey,
        String toCandidateKey,
        EventRelationType relationType,
        String evidenceText,
        ArticleAnalysisResponse.StatementType evidenceType,
        String speaker) {}

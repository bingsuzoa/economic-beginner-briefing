package com.economicbriefing.domain.analysis;

import java.util.List;

import com.economicbriefing.domain.article.NewsCategory;

public record AudienceProfile(
    String economicKnowledgeLevel,
    List<NewsCategory> interests,
    List<String> contextNotes
) {}

package com.economicbriefing.economicflow;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.economicbriefing.economicflow.entity.TopicCandidateEntity;
import com.economicbriefing.economicflow.entity.TopicEntity;
import com.economicbriefing.economicflow.repository.TopicCandidateRepository;
import com.economicbriefing.economicflow.repository.TopicRepository;
import org.springframework.stereotype.Component;

@Component
public class TopicResolver {
    private final TopicRepository topics;
    private final TopicCandidateRepository candidates;

    public TopicResolver(TopicRepository topics, TopicCandidateRepository candidates) {
        this.topics = topics;
        this.candidates = candidates;
    }

    public Set<TopicEntity> resolve(EventCandidate candidate) {
        Set<TopicEntity> resolved = new LinkedHashSet<>();
        Set<String> proposed = new LinkedHashSet<>();
        String searchable = String.join(" ", candidate.subject(), candidate.subjectKey(), candidate.title(),
                candidate.evidenceText(), java.util.Objects.toString(candidate.previousState(), ""),
                java.util.Objects.toString(candidate.newState(), "")).toLowerCase();
        candidate.topicKeys().forEach(key -> topics.findByTopicKeyAndActiveTrue(key)
                .ifPresentOrElse(topic -> { if (supports(topic, searchable)) resolved.add(topic); },
                        () -> proposed.add(key)));

        for (TopicEntity topic : topics.findByActiveTrue()) {
            if (supports(topic, searchable)) resolved.add(topic);
        }
        candidate.newTopicCandidates().stream().filter(name -> name != null && !name.isBlank())
                .map(String::trim).forEach(proposed::add);
        if (resolved.isEmpty() && proposed.isEmpty() && candidate.eventType() == EventType.INDUSTRY_CHANGE) {
            proposed.add(candidate.subject());
        }
        proposed.forEach(name -> saveCandidate(name, candidate.articleId()));
        return resolved;
    }

    private static boolean supports(TopicEntity topic, String searchable) {
        return searchable.contains(topic.getTopicKey().toLowerCase())
                || searchable.contains(topic.getName().toLowerCase())
                || aliases(topic).stream().anyMatch(searchable::contains);
    }

    private void saveCandidate(String name, String articleId) {
        String normalized = name.trim();
        if (candidates.existsByNameAndArticleId(normalized, articleId)) return;
        TopicCandidateEntity entity = new TopicCandidateEntity();
        entity.setName(normalized); entity.setArticleId(articleId); candidates.save(entity);
    }

    private static List<String> aliases(TopicEntity topic) {
        return topic.getAliases() == null ? List.of()
                : java.util.Arrays.stream(topic.getAliases().split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).map(String::toLowerCase).toList();
    }
}

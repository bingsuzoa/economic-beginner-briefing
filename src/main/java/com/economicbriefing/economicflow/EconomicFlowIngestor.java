package com.economicbriefing.economicflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.economicbriefing.economicflow.entity.*;
import com.economicbriefing.economicflow.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EconomicFlowIngestor {
    private final EventCandidateValidator validator;
    private final EventNormalizer normalizer;
    private final TopicResolver topicResolver;
    private final EconomicEventRepository events;
    private final EventEvidenceRepository evidence;
    private final EventRelationRepository relations;
    private final EconomicKeyResolver keyResolver;
    private final EconomicMemoryComparator comparator;
    private final FlowNodeResolver flowNodeResolver;
    private final EventRelationEvidenceRepository relationEvidence;

    public EconomicFlowIngestor(EventCandidateValidator validator, EventNormalizer normalizer,
            TopicResolver topicResolver, EconomicEventRepository events,
            EventEvidenceRepository evidence, EventRelationRepository relations,
            EconomicKeyResolver keyResolver, EconomicMemoryComparator comparator,
            EventRelationEvidenceRepository relationEvidence, FlowNodeResolver flowNodeResolver) {
        this.validator = validator; this.normalizer = normalizer; this.topicResolver = topicResolver;
        this.events = events; this.evidence = evidence; this.relations = relations;
        this.keyResolver = keyResolver; this.comparator = comparator; this.relationEvidence = relationEvidence;
        this.flowNodeResolver = flowNodeResolver;
    }

    @Transactional
    public FlowIngestionResult ingestFlow(ArticleContext article, EconomicFlowExtraction extraction) {
        if (article == null || isBlank(article.articleId()) || article.publishedAt() == null || isBlank(article.body())) {
            throw new IllegalArgumentException("Invalid flow article context");
        }
        List<FlowNodeCandidate> nodes = extraction.nodes();
        List<FlowClaimCandidate> claims = extraction.flowClaims();
        if (nodes.isEmpty()) {
            if (!claims.isEmpty()) throw new IllegalArgumentException("Flow claims require nodes");
            return new FlowIngestionResult(List.of(), List.of());
        }
        var byText = new java.util.LinkedHashMap<String, FlowNodeCandidate>();
        for (var node : nodes) {
            if (node == null || isBlank(node.text()) || byText.put(node.text(), node) != null) {
                throw new IllegalArgumentException("Invalid or duplicate flow node");
            }
        }
        for (var claim : claims) {
            if (claim == null || claim.relationType() == null
                    || !Set.of(EventRelationType.CAUSE, EventRelationType.PURPOSE,
                            EventRelationType.RESPONSE, EventRelationType.CONDITION,
                            EventRelationType.MOTIVATION).contains(claim.relationType())
                    || !byText.containsKey(claim.from()) || !byText.containsKey(claim.to())
                    || claim.from().equals(claim.to())) {
                throw new IllegalArgumentException("Invalid flow claim endpoints");
            }
        }

        var exact = new java.util.HashMap<String, EconomicEventEntity>();
        var comparisons = new java.util.ArrayList<FlowNodeResolver.Comparison>();
        for (var node : nodes) {
            String key = flowSubjectKey(node.text());
            var same = events.findBySubjectKey(key).stream().findFirst().orElse(null);
            if (same != null) exact.put(node.text(), same);
            else {
                var candidates = lexicalCandidates(node.text());
                if (!candidates.isEmpty()) comparisons.add(new FlowNodeResolver.Comparison(node.text(), candidates));
            }
        }
        var decisions = comparisons.isEmpty() ? java.util.Map.<String, FlowNodeResolver.Decision>of()
                : flowNodeResolver.resolve(comparisons);
        var ids = new java.util.LinkedHashMap<String, Long>();
        var nodeResults = new java.util.ArrayList<ResolvedFlowNode>();
        for (var node : nodes) {
            EconomicEventEntity event = exact.get(node.text());
            var decision = decisions.get(node.text());
            if (event == null && decision != null && decision.decision() == FlowResolverDecision.SAME) {
                event = events.findById(decision.matchedNodeId()).orElseThrow();
            }
            boolean created = event == null;
            if (created) event = events.save(toFlowEntity(node, article));
            addFlowEvidence(event, article);
            ids.put(node.text(), event.getId());
            nodeResults.add(new ResolvedFlowNode(node.text(),
                    decision == null ? (created ? FlowResolverDecision.NO_MATCH : FlowResolverDecision.SAME)
                            : decision.decision(),
                    created ? null : event.getId(), event.getId(), created));
        }
        var edgeResults = claims.stream().map(claim -> saveFlowClaim(claim, ids, article)).toList();
        return new FlowIngestionResult(List.copyOf(nodeResults), edgeResults);
    }

    private List<FlowNodeResolver.ExistingNode> lexicalCandidates(String text) {
        var found = new java.util.LinkedHashMap<Long, EconomicEventEntity>();
        java.util.Arrays.stream(text.trim().split("\\s+"))
                .filter(token -> token.length() >= 2)
                .forEach(token -> events.findTop20ByTitleContainingIgnoreCaseOrderByIdDesc(token)
                        .forEach(event -> found.putIfAbsent(event.getId(), event)));
        return found.values().stream().limit(10)
                .map(event -> new FlowNodeResolver.ExistingNode(event.getId(), event.getTitle())).toList();
    }

    @Transactional(readOnly = true)
    public List<FlowNodeResolver.ExistingNode> findFlowCandidates(String text) {
        return lexicalCandidates(text);
    }

    private EconomicEventEntity toFlowEntity(FlowNodeCandidate node, ArticleContext article) {
        EconomicEventEntity event = new EconomicEventEntity();
        event.setEventType(EventType.MARKET_EVENT);
        event.setTitle(node.text()); event.setSubject(node.text());
        event.setSubjectKey(flowSubjectKey(node.text()));
        event.setNodeKind(NodeKind.EVENT); event.setScopeKey("FLOW");
        event.setEventDate(article.publishedAt().toLocalDate()); event.setStatus(EventStatus.CONFIRMED);
        return event;
    }

    private void addFlowEvidence(EconomicEventEntity event, ArticleContext article) {
        if (evidence.existsByEvent_IdAndArticleId(event.getId(), article.articleId())) return;
        EventEvidenceEntity item = new EventEvidenceEntity();
        item.setEvent(event); item.setArticleId(article.articleId()); item.setEvidenceText(article.body());
        evidence.save(item);
    }

    private ResolvedFlowEdge saveFlowClaim(
            FlowClaimCandidate claim, java.util.Map<String, Long> ids, ArticleContext article) {
        Long fromId = ids.get(claim.from());
        Long toId = ids.get(claim.to());
        var existing = relations.findByFromEvent_IdAndToEvent_IdAndRelationType(fromId, toId, claim.relationType());
        EventRelationEntity relation = existing.orElseGet(() -> {
                    EventRelationEntity created = new EventRelationEntity();
                    created.setFromEvent(events.getReferenceById(fromId));
                    created.setToEvent(events.getReferenceById(toId));
                    created.setRelationType(claim.relationType());
                    created.setProvenance(RelationProvenance.ARTICLE_EXPLICIT);
                    created.setConfidence(1); created.setEvidenceArticleId(article.articleId());
                    return relations.save(created);
                });
        String evidenceHash = hash(article.body());
        if (relationEvidence.existsByRelation_IdAndArticleIdAndEvidenceHash(
                relation.getId(), article.articleId(), evidenceHash)) {
            return new ResolvedFlowEdge(claim.from(), claim.to(), claim.relationType(), fromId, toId, true, false);
        }
        EventRelationEvidenceEntity item = new EventRelationEvidenceEntity();
        item.setRelation(relation); item.setArticleId(article.articleId());
        item.setEvidenceText(article.body()); item.setEvidenceHash(evidenceHash);
        item.setEvidenceType(com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse.StatementType.FACT);
        relationEvidence.save(item);
        return new ResolvedFlowEdge(claim.from(), claim.to(), claim.relationType(), fromId, toId,
                existing.isPresent(), existing.isEmpty());
    }

    private static String flowSubjectKey(String text) {
        return "FLOW_" + hash(text.replaceAll("[\\p{Punct}\\s]+", "").toLowerCase()).substring(0, 32);
    }

    private static boolean isBlank(String value) { return value == null || value.isBlank(); }

    @Transactional
    public IngestionResult ingest(EventCandidate candidate) {
        return ingestAll(List.of(candidate)).getFirst();
    }

    @Transactional
    public List<IngestionResult> ingestAll(List<EventCandidate> candidates) {
        List<Prepared> prepared = candidates.stream().map(this::prepare).toList();
        var unresolved = prepared.stream().filter(p -> p.exactMatch() == null && p.keys() != null)
                .map(p -> EconomicMemoryComparator.comparison(p.candidate(), p.activeStates())).toList();
        var decisions = comparator.compare(unresolved);
        return prepared.stream().map(p -> ingestPrepared(p, p.keys() == null
                ? null : decisions.get(p.candidate().candidateKey()))).toList();
    }

    @Transactional
    public List<IngestionResult> ingestAll(List<EventCandidate> candidates,
            List<EventRelationCandidate> relationCandidates) {
        List<IngestionResult> results = ingestAll(candidates);
        var ids = new java.util.HashMap<String, Long>();
        for (int i = 0; i < candidates.size(); i++) {
            String key = candidates.get(i).candidateKey();
            if (key != null && ids.put(key, results.get(i).eventId()) != null) {
                throw new IllegalArgumentException("Duplicate candidateKey: " + key);
            }
        }
        relationCandidates.forEach(candidate -> saveArticleRelation(candidate, ids));
        return results;
    }

    private void saveArticleRelation(EventRelationCandidate candidate, java.util.Map<String, Long> ids) {
        Long fromId = ids.get(candidate.fromCandidateKey());
        Long toId = ids.get(candidate.toCandidateKey());
        if (fromId == null || toId == null || fromId.equals(toId) || candidate.relationType() == null
                || candidate.evidenceType() == null || candidate.evidenceText() == null
                || candidate.evidenceText().isBlank()) {
            throw new IllegalArgumentException("Invalid article relation endpoints or evidence");
        }
        EventRelationEntity relation = relations.findByFromEvent_IdAndToEvent_IdAndRelationType(
                fromId, toId, candidate.relationType()).orElseGet(() -> {
                    EventRelationEntity created = new EventRelationEntity();
                    created.setFromEvent(events.getReferenceById(fromId));
                    created.setToEvent(events.getReferenceById(toId));
                    created.setRelationType(candidate.relationType());
                    created.setProvenance(RelationProvenance.ARTICLE_EXPLICIT);
                    created.setConfidence(1); created.setEvidenceArticleId(candidate.articleId());
                    return relations.save(created);
                });
        String evidenceHash = hash(candidate.evidenceText());
        if (relationEvidence.existsByRelation_IdAndArticleIdAndEvidenceHash(
                relation.getId(), candidate.articleId(), evidenceHash)) return;
        EventRelationEvidenceEntity item = new EventRelationEvidenceEntity();
        item.setRelation(relation); item.setArticleId(candidate.articleId());
        item.setEvidenceText(candidate.evidenceText()); item.setEvidenceHash(evidenceHash);
        item.setEvidenceType(candidate.evidenceType()); item.setSpeaker(candidate.speaker());
        relationEvidence.save(item);
    }

    private Prepared prepare(EventCandidate candidate) {
        validator.validate(candidate);
        var keys = keyResolver.resolve(candidate);
        var previous = normalizer.normalizeValue(candidate.subjectKey(), candidate.previousState());
        var next = normalizer.normalizeValue(candidate.subjectKey(), candidate.newState());
        String region = normalizer.normalizeRegion(candidate.region());
        if (keys == null) return new Prepared(candidate, null, previous, next, region, List.of(), null);
        List<EconomicEventEntity> active = candidate.nodeKind() == NodeKind.STATE
                ? events.findActiveState(candidate.scopeKey(), candidate.subjectKey(), keys.slot().getId())
                : events.findRecentNormalizedNodes(candidate.scopeKey(), candidate.subjectKey(), keys.slot().getId(),
                        org.springframework.data.domain.PageRequest.of(0, 10));
        EconomicEventEntity exact = candidate.nodeKind() == NodeKind.STATE ? active.stream()
                .filter(e -> e.getEndedAt() == null && e.getSlotValue() != null
                        && e.getSlotValue().getValueKey().equals(candidate.valueKey()))
                .findFirst().orElse(null) : null;
        return new Prepared(candidate, keys, previous, next, region, active, exact);
    }

    private IngestionResult ingestPrepared(Prepared p, EconomicMemoryComparator.Decision decision) {
        EventCandidate candidate = p.candidate();
        if (p.exactMatch() != null) {
            addEvidence(p.exactMatch(), candidate);
            return new IngestionResult(p.exactMatch().getId(), false, MemoryDecision.REPEATED_STATE,
                    p.exactMatch().getId(), true, "active scope+subject+slot+value exact match");
        }
        if (p.keys() != null) {
            if (decision == null) throw new IllegalArgumentException("Missing memory decision");
            if (decision.decision() == MemoryDecision.REPEATED_STATE) {
                EconomicEventEntity matched = events.findById(decision.matchedNodeId()).orElseThrow();
                addEvidence(matched, candidate);
                return new IngestionResult(matched.getId(), false, decision.decision(),
                        decision.matchedNodeId(), false, decision.reason());
            }
            EconomicEventEntity created = toEntity(candidate, p.previous(), p.next(), p.region(), null, p.keys());
            created.getTopics().addAll(topicResolver.resolve(candidate));
            created = events.save(created);
            addEvidence(created, candidate);
            if (decision.decision() == MemoryDecision.STATE_CHANGED) {
                EconomicEventEntity old = events.findById(decision.matchedNodeId()).orElseThrow();
                old.setEndedAt(candidate.eventDate());
                saveTransition(old, created, candidate.articleId());
            }
            return new IngestionResult(created.getId(), true, decision.decision(),
                    decision.matchedNodeId(), false, decision.reason());
        }

        String dedupKey = dedupKey(candidate, p.previous(), p.next(), p.region());
        Set<TopicEntity> resolvedTopics = topicResolver.resolve(candidate);

        EconomicEventEntity event = dedupKey == null
                ? findUnstructuredDuplicate(candidate, p.region(), resolvedTopics)
                : events.findByDedupKey(dedupKey).orElseGet(
                        () -> findStructuredDuplicate(candidate, p.previous(), p.next(), p.region(), resolvedTopics));
        boolean created = event == null;
        if (created) {
            event = toEntity(candidate, p.previous(), p.next(), p.region(), dedupKey, null);
            event.getTopics().addAll(resolvedTopics);
            event = events.save(event);
        }
        addEvidence(event, candidate);
        if (created) linkPreviousVersion(event, candidate.articleId());
        return new IngestionResult(event.getId(), created,
                created ? MemoryDecision.NEW_EVENT : MemoryDecision.REPEATED_STATE,
                created ? null : event.getId(), false,
                created ? "legacy candidate created" : "legacy dedup match");
    }

    private EconomicEventEntity findUnstructuredDuplicate(
            EventCandidate c, String region, Set<TopicEntity> topics) {
        return events.findByEventTypeAndEventDateBetween(c.eventType(), c.eventDate().minusDays(7), c.eventDate().plusDays(7))
                .stream().filter(e -> e.getSubjectKey().equals(c.subjectKey())
                        && e.getStatus() == c.status() && Objects.equals(e.getRegionCode(), region)
                        && sharesTopic(e, topics) && e.getTitle().equals(c.title())).findFirst().orElse(null);
    }

    private EconomicEventEntity findStructuredDuplicate(EventCandidate c,
            EventNormalizer.NormalizedValue previous, EventNormalizer.NormalizedValue next,
            String region, Set<TopicEntity> topics) {
        return events.findByEventTypeAndEventDateBetween(c.eventType(), c.eventDate().minusDays(7), c.eventDate().plusDays(7))
                .stream().filter(e -> e.getSubjectKey().equals(c.subjectKey()) && e.getStatus() == c.status()
                        && Objects.equals(e.getRegionCode(), region) && sharesTopic(e, topics)
                        && (c.eventType() == EventType.INDICATOR_MILESTONE
                                ? sameMilestone(e, c, next)
                                : Objects.equals(e.getPreviousValueNormalized(), previous.value())
                                        && Objects.equals(e.getNewValueNormalized(), next.value())))
                .findFirst().orElse(null);
    }

    private static boolean sameMilestone(EconomicEventEntity event, EventCandidate candidate,
            EventNormalizer.NormalizedValue value) {
        return event.getEventDate().equals(candidate.eventDate())
                && event.getMilestoneType() == candidate.milestoneType()
                && Objects.equals(event.getNewValueNormalized(), value.value())
                && event.getValueType() == value.valueType()
                && compatible(event.getMilestonePeriodValue(), candidate.milestonePeriodValue())
                && compatible(event.getMilestonePeriodUnit(), candidate.milestonePeriodUnit())
                && compatible(event.getMilestoneReferenceDate(), candidate.milestoneReferenceDate());
    }

    private static boolean compatible(Object saved, Object candidate) {
        return saved == null || candidate == null || saved.equals(candidate);
    }

    private static boolean sharesTopic(EconomicEventEntity event, Set<TopicEntity> topics) {
        return !topics.isEmpty() && event.getTopics().stream().anyMatch(topics::contains);
    }

    private EconomicEventEntity toEntity(EventCandidate c, EventNormalizer.NormalizedValue previous,
            EventNormalizer.NormalizedValue next, String region, String dedupKey,
            EconomicKeyResolver.ResolvedKeys keys) {
        EconomicEventEntity e = new EconomicEventEntity();
        e.setEventType(c.eventType()); e.setTitle(c.title()); e.setSubject(c.subject());
        e.setSubjectKey(c.subjectKey()); e.setEventDate(c.eventDate()); e.setStatus(c.status());
        e.setNodeKind(c.nodeKind()); e.setScopeKey(c.scopeKey());
        if (keys != null) { e.setSlot(keys.slot()); e.setSlotValue(keys.value()); }
        e.setPreviousValue(c.previousState()); e.setPreviousValueNormalized(previous.value());
        e.setNewValue(c.newState()); e.setNewValueNormalized(next.value());
        var value = next.unit() != null ? next : previous;
        e.setValueUnit(value.unit()); e.setValueType(value.valueType());
        e.setBaseCurrency(value.baseCurrency()); e.setQuoteCurrency(value.quoteCurrency());
        e.setBaseAmount(value.baseAmount());
        e.setMilestoneType(c.milestoneType()); e.setMilestonePeriodValue(c.milestonePeriodValue());
        e.setMilestonePeriodUnit(c.milestonePeriodUnit());
        e.setMilestoneReferenceDate(c.milestoneReferenceDate());
        e.setRegionCode(region); e.setDedupKey(dedupKey);
        return e;
    }

    private void saveTransition(EconomicEventEntity from, EconomicEventEntity to, String articleId) {
        if (relations.existsByFromEvent_IdAndToEvent_IdAndRelationType(
                from.getId(), to.getId(), EventRelationType.STATE_CHANGED_TO)) return;
        EventRelationEntity relation = new EventRelationEntity();
        relation.setFromEvent(from); relation.setToEvent(to);
        relation.setRelationType(EventRelationType.STATE_CHANGED_TO);
        relation.setConfidence(1); relation.setEvidenceArticleId(articleId);
        relations.save(relation);
    }

    private void addEvidence(EconomicEventEntity event, EventCandidate candidate) {
        if (evidence.existsByEvent_IdAndArticleId(event.getId(), candidate.articleId())) return;
        EventEvidenceEntity entity = new EventEvidenceEntity();
        entity.setEvent(event); entity.setArticleId(candidate.articleId());
        entity.setEvidenceText(candidate.evidenceText());
        evidence.save(entity);
    }

    private void linkPreviousVersion(EconomicEventEntity current, String articleId) {
        if (current.getPreviousValueNormalized() == null || current.getStatus() != EventStatus.CONFIRMED) return;
        events.findBySubjectKeyAndEventDateBeforeOrderByEventDateDesc(current.getSubjectKey(), current.getEventDate())
                .stream().filter(old -> Objects.equals(old.getNewValueNormalized(), current.getPreviousValueNormalized()))
                .findFirst().ifPresent(old -> {
                    if (relations.existsByFromEvent_IdAndToEvent_IdAndRelationType(
                            old.getId(), current.getId(), EventRelationType.PREVIOUS_VERSION)) return;
                    EventRelationEntity relation = new EventRelationEntity();
                    relation.setFromEvent(old); relation.setToEvent(current);
                    relation.setRelationType(EventRelationType.PREVIOUS_VERSION);
                    relation.setConfidence(1.0); relation.setEvidenceArticleId(articleId);
                    relations.save(relation);
                });
    }

    private static String dedupKey(EventCandidate c, EventNormalizer.NormalizedValue previous,
            EventNormalizer.NormalizedValue next, String region) {
        if (c.eventType() == EventType.INDICATOR_MILESTONE) {
            return hash(String.join("|", c.eventType().name(), c.subjectKey(), c.eventDate().toString(),
                    c.milestoneType().name(), Objects.toString(c.milestonePeriodValue(), ""),
                    Objects.toString(c.milestonePeriodUnit(), ""),
                    Objects.toString(c.milestoneReferenceDate(), ""), Objects.toString(next.value(), ""),
                    Objects.toString(next.valueType(), ""), c.status().name()));
        }
        ValueUnit unit = next.unit() != null ? next.unit() : previous.unit();
        if (unit == null || unit == ValueUnit.TEXT) return null;
        String raw = String.join("|", c.eventType().name(), c.subjectKey(), Objects.toString(region, ""),
                Objects.toString(previous.value(), ""), Objects.toString(next.value(), ""),
                c.eventDate().toString(), c.status().name());
        return hash(raw);
    }

    private static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record IngestionResult(Long eventId, boolean created, MemoryDecision decision,
            Long matchedNodeId, boolean javaExact, String reason) {}
    public record FlowIngestionResult(List<ResolvedFlowNode> resolvedNodes, List<ResolvedFlowEdge> resolvedEdges) {}
    public record ResolvedFlowNode(String text, FlowResolverDecision resolverDecision,
            Long matchedNodeId, Long resolvedNodeId, boolean newNodeCreated) {}
    public record ResolvedFlowEdge(String from, String to, EventRelationType relationType,
            Long fromNodeId, Long toNodeId, boolean existingEdge, boolean newEdgeCreated) {}
    private record Prepared(EventCandidate candidate, EconomicKeyResolver.ResolvedKeys keys,
            EventNormalizer.NormalizedValue previous, EventNormalizer.NormalizedValue next,
            String region, List<EconomicEventEntity> activeStates, EconomicEventEntity exactMatch) {}
}

package com.economicbriefing.economicflow;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.economicbriefing.classifier.entity.ArticleEntity;
import com.economicbriefing.classifier.repository.ArticleRepository;
import com.economicbriefing.economicflow.entity.TopicEntity;
import com.economicbriefing.economicflow.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EconomicFlowIngestorTest {
    @Autowired EconomicFlowIngestor ingestor;
    @Autowired EconomicEventRepository events;
    @Autowired EventEvidenceRepository evidence;
    @Autowired EventRelationRepository relations;
    @Autowired TopicRepository topics;
    @Autowired TopicCandidateRepository topicCandidates;
    @Autowired ArticleRepository articles;
    @Autowired EconomicSlotRepository slots;
    @Autowired EconomicSlotValueRepository slotValues;
    @Autowired EventRelationEvidenceRepository relationEvidence;

    @BeforeEach
    void setUp() {
        saveArticle("a1"); saveArticle("a2"); saveArticle("a3"); saveArticle("a4");
        TopicEntity mortgage = new TopicEntity();
        mortgage.setTopicKey("MORTGAGE"); mortgage.setName("주택담보대출");
        mortgage.setDomain(TopicDomain.REAL_ESTATE); mortgage.setAliases("주담대,mortgage");
        topics.save(mortgage);
        TopicEntity debt = new TopicEntity();
        debt.setTopicKey("HOUSEHOLD_DEBT"); debt.setName("가계부채");
        debt.setDomain(TopicDomain.REAL_ESTATE);
        topics.save(debt);
        TopicEntity usdKrw = new TopicEntity();
        usdKrw.setTopicKey("USD_KRW"); usdKrw.setName("원달러 환율");
        usdKrw.setDomain(TopicDomain.FX); usdKrw.setAliases("원/달러 환율,달러-원 환율");
        topics.save(usdKrw);
        var slot = new com.economicbriefing.economicflow.entity.EconomicSlotEntity();
        slot.setSlotKey("INFLATION_STATUS"); slot.setName("물가 상태"); slots.save(slot);
        for (String key : List.of("ABOVE_TARGET", "NEAR_TARGET")) {
            var value = new com.economicbriefing.economicflow.entity.EconomicSlotValueEntity();
            value.setSlot(slot); value.setValueKey(key); value.setName(key); slotValues.save(value);
        }
        var corporateSlot = new com.economicbriefing.economicflow.entity.EconomicSlotEntity();
        corporateSlot.setSlotKey("CORPORATE_STRUCTURE_STATUS"); corporateSlot.setName("기업 구조개편 상태");
        slots.save(corporateSlot);
        for (String key : List.of("SPLIT_ANNOUNCED", "SPLIT_APPROVED")) {
            var value = new com.economicbriefing.economicflow.entity.EconomicSlotValueEntity();
            value.setSlot(corporateSlot); value.setValueKey(key); value.setName(key); slotValues.save(value);
        }
    }

    @Test
    void deduplicatesNormalizedEventAndAddsEvidenceAndMultipleTopics() {
        var first = candidate("a1", "5억원", "4억 원", EventStatus.CONFIRMED, List.of("MORTGAGE", "HOUSEHOLD_DEBT"));
        var second = candidate("a2", "500,000,000원", "400,000,000원", EventStatus.CONFIRMED, List.of("MORTGAGE"));
        var saved = ingestor.ingest(first);
        var duplicate = ingestor.ingest(second);

        assertTrue(saved.created());
        assertFalse(duplicate.created());
        assertEquals(1, events.count());
        assertEquals(2, evidence.countByEvent_Id(saved.eventId()));
        assertEquals(2, events.findById(saved.eventId()).orElseThrow().getTopics().size());
    }

    @Test
    void keepsDifferentValuesAndAnnouncementSeparateAndLinksOnlyValueChain() {
        var old = ingestor.ingest(candidate("a1", "6억원", "5억원", EventStatus.CONFIRMED, List.of("MORTGAGE")));
        var current = ingestor.ingest(candidate("a2", "5억원", "4억원", EventStatus.CONFIRMED, List.of("MORTGAGE")));
        var announcement = ingestor.ingest(candidate("a3", "5억원", "4억원", EventStatus.ANNOUNCED, List.of("MORTGAGE")));

        assertEquals(3, events.count());
        assertEquals(1, relations.count());
        assertTrue(relations.existsByFromEvent_IdAndToEvent_IdAndRelationType(
                old.eventId(), current.eventId(), EventRelationType.PREVIOUS_VERSION));
        assertNotEquals(current.eventId(), announcement.eventId());
    }

    @Test
    void recordsUnknownReusableTopicAsPendingCandidate() {
        var candidate = candidate("a1", null, "도입", EventStatus.ANNOUNCED, List.of());
        candidate = new EventCandidate(candidate.articleId(), EventType.SYSTEM_CHANGE, "토큰증권 제도 발표",
                "토큰증권", "SECURITY_TOKEN", candidate.eventDate(), null, "도입", candidate.status(), null,
                List.of(), List.of("토큰증권"), "토큰증권 제도를 도입한다고 발표했다.");
        ingestor.ingest(candidate);
        assertTrue(topicCandidates.existsByNameAndArticleId("토큰증권", "a1"));
    }

    @Test
    void keepsNodeAndRecordsUnknownTopicKeyAsPendingCandidate() {
        EventCandidate candidate = new EventCandidate("a1", EventType.INDUSTRY_CHANGE,
                "금융권 AI 전면 도입", "금융권 AI", "AI_FINANCIAL_SECTOR",
                LocalDate.of(2026, 8, 27), "파일럿", "전면 적용", EventStatus.CONFIRMED, "KR",
                List.of("FINTECH"), List.of(), "금융권 AI 도입이 전면 적용 단계로 전환됐다.");

        var result = ingestor.ingest(candidate);

        assertTrue(result.created());
        assertTrue(events.findById(result.eventId()).isPresent());
        assertTrue(topicCandidates.existsByNameAndArticleId("FINTECH", "a1"));
    }

    @Test
    void proposesIndustrySubjectWhenNoExistingTopicFits() {
        EventCandidate candidate = new EventCandidate("a1", EventType.INDUSTRY_CHANGE,
                "금융권 AI 전면 도입", "금융권 AI", "AI_FINANCIAL_SECTOR",
                LocalDate.of(2026, 8, 27), "파일럿", "전면 적용", EventStatus.CONFIRMED, "KR",
                List.of(), List.of(), "금융권 AI 도입이 전면 적용 단계로 전환됐다.");

        ingestor.ingest(candidate);

        assertTrue(topicCandidates.existsByNameAndArticleId("금융권 AI", "a1"));
    }

    @Test
    void deduplicatesMilestoneWithoutTitleAndKeepsDifferentMilestoneType() {
        EventCandidate first = milestone("a1", "원/달러 환율 13개월 만에 최저",
                MilestoneType.PERIOD_LOW, 13, MilestonePeriodUnit.MONTH);
        EventCandidate differentlyTitled = milestone("a2", "달러-원 장중 작년 7월 이후 최저",
                MilestoneType.PERIOD_LOW, null, null);
        EventCandidate threshold = milestone("a3", "원/달러 환율 1,380원선 붕괴",
                MilestoneType.THRESHOLD_BREAK, null, null);

        var saved = ingestor.ingest(first);
        var duplicate = ingestor.ingest(differentlyTitled);
        var other = ingestor.ingest(threshold);

        assertTrue(saved.created());
        assertFalse(duplicate.created());
        assertTrue(other.created());
        assertEquals(2, events.count());
        assertEquals(2, evidence.countByEvent_Id(saved.eventId()));
        var entity = events.findById(saved.eventId()).orElseThrow();
        assertEquals(ValueUnit.FX_RATE, entity.getValueUnit());
        assertEquals(ValueType.RANGE_BAND, entity.getValueType());
        assertEquals("USD", entity.getBaseCurrency());
        assertEquals("KRW", entity.getQuoteCurrency());
        assertEquals(1, entity.getBaseAmount());
        assertEquals(MilestoneType.PERIOD_LOW, entity.getMilestoneType());
        assertEquals(13, entity.getMilestonePeriodValue());
    }

    @Test
    void reusesSameActiveStateAndChangesDifferentValueByScopeSubjectSlot() {
        var first = ingestor.ingest(state("a1", "c1", "ABOVE_TARGET", LocalDate.of(2026, 8, 20)));
        var repeated = ingestor.ingest(state("a2", "c2", "ABOVE_TARGET", LocalDate.of(2026, 8, 21)));
        var changed = ingestor.ingest(state("a3", "c3", "NEAR_TARGET", LocalDate.of(2026, 8, 24)));

        assertFalse(repeated.created());
        assertEquals(first.eventId(), repeated.eventId());
        assertTrue(changed.created());
        assertEquals(LocalDate.of(2026, 8, 24), events.findById(first.eventId()).orElseThrow().getEndedAt());
        assertTrue(relations.existsByFromEvent_IdAndToEvent_IdAndRelationType(
                first.eventId(), changed.eventId(), EventRelationType.STATE_CHANGED_TO));
    }

    @Test
    void linksCorporateStateChangesOnlyWithinSameCompany() {
        var announced = ingestor.ingest(corporateState("a1", "k1", "KAKAO", "SPLIT_ANNOUNCED"));
        var approved = ingestor.ingest(corporateState("a2", "k2", "KAKAO", "SPLIT_APPROVED"));
        var samsung = ingestor.ingest(corporateState("a3", "s1", "SAMSUNG_ELECTRONICS", "SPLIT_APPROVED"));

        assertTrue(relations.existsByFromEvent_IdAndToEvent_IdAndRelationType(
                announced.eventId(), approved.eventId(), EventRelationType.STATE_CHANGED_TO));
        assertFalse(relations.existsByFromEvent_IdAndToEvent_IdAndRelationType(
                approved.eventId(), samsung.eventId(), EventRelationType.STATE_CHANGED_TO));
        assertNull(events.findById(samsung.eventId()).orElseThrow().getEndedAt());
    }

    @Test
    void storesArticleExplicitRelationEvidenceBetweenCandidateKeys() {
        var from = normalizedEvent("from", "INFLATION");
        var to = normalizedEvent("to", "FED_RATE");
        var relation = new EventRelationCandidate("a1", "from", "to", EventRelationType.DIRECT_CAUSE,
                "물가 우려 때문에 연준이 대응했다.",
                com.economicbriefing.analyzer.openai.dto.ArticleAnalysisResponse.StatementType.FACT, null);

        var result = ingestor.ingestAll(List.of(from, to), List.of(relation));

        assertTrue(relations.existsByFromEvent_IdAndToEvent_IdAndRelationType(
                result.get(0).eventId(), result.get(1).eventId(), EventRelationType.DIRECT_CAUSE));
        assertEquals(1, relationEvidence.count());
    }

    @Test
    void storesEventNodeWithoutStateSlot() {
        EventCandidate candidate = new EventCandidate("a1", EventType.POLICY_CHANGE,
                "새마을금고 집단대출 재개", "새마을금고", "SAEMAUL_GEUMGO",
                LocalDate.of(2026, 8, 27), "중단", "재개", EventStatus.CONFIRMED, "KR",
                List.of("HOUSEHOLD_DEBT"), List.of(), "새마을금고가 집단대출을 재개했다.",
                null, null, null, null, "event1", NodeKind.EVENT, "KR", null, null);

        var result = ingestor.ingest(candidate);

        var saved = events.findById(result.eventId()).orElseThrow();
        assertTrue(result.created());
        assertEquals(NodeKind.EVENT, saved.getNodeKind());
        assertEquals("KR", saved.getScopeKey());
        assertNull(saved.getSlot());
    }

    private EventCandidate normalizedEvent(String key, String subjectKey) {
        return new EventCandidate("a1", EventType.MARKET_EVENT, subjectKey, subjectKey, subjectKey,
                LocalDate.of(2026, 8, 25), null, null, EventStatus.CONFIRMED, "US",
                List.of(), List.of(), subjectKey + " 사건이 발생했다.", null, null, null, null,
                key, NodeKind.EVENT, "US", "INFLATION_STATUS", null);
    }

    private EventCandidate state(String articleId, String key, String value, LocalDate date) {
        return new EventCandidate(articleId, EventType.INDICATOR_TREND, "미국 물가 상태", "물가",
                "INFLATION", date, null, value, EventStatus.CONFIRMED, "US",
                List.of(), List.of(), "미국 물가 상태가 변했다.", null, null, null, null,
                key, NodeKind.STATE, "US", "INFLATION_STATUS", value);
    }

    private EventCandidate corporateState(String articleId, String key, String subjectKey, String value) {
        return new EventCandidate(articleId, EventType.CORPORATE_RESTRUCTURING, subjectKey + " 구조개편",
                subjectKey, subjectKey, LocalDate.of(2026, 8, 26), null, value, EventStatus.ANNOUNCED, "KR",
                List.of(), List.of(), subjectKey + "가 구조개편을 발표했다.",
                null, null, null, null, key, NodeKind.STATE, "KR", "CORPORATE_STRUCTURE_STATUS", value);
    }

    private EventCandidate milestone(String articleId, String title, MilestoneType type,
            Integer period, MilestonePeriodUnit unit) {
        return new EventCandidate(articleId, EventType.INDICATOR_MILESTONE, title,
                "원/달러 환율", "USD_KRW", LocalDate.of(2026, 8, 24), null, "1,370원대",
                EventStatus.CONFIRMED, null, List.of("USD_KRW"), List.of(),
                "원/달러 환율이 장중 1,370원대로 내려갔다.", type, period, unit, null);
    }

    private EventCandidate candidate(String articleId, String before, String after,
            EventStatus status, List<String> topicKeys) {
        return new EventCandidate(articleId,
                status == EventStatus.CONFIRMED ? EventType.POLICY_CHANGE : EventType.POLICY_ANNOUNCEMENT,
                "수도권 주담대 한도 축소", "주택담보대출 한도", "MORTGAGE_LIMIT",
                LocalDate.of(2026, 8, articleId.equals("a1") ? 20 : 24), before, after, status,
                "수도권", topicKeys, List.of(), "가계부채 관리를 위해 수도권 주담대 한도를 변경했다.");
    }

    private void saveArticle(String id) {
        ArticleEntity article = new ArticleEntity();
        article.setId(id); article.setSource("test"); article.setTitle(id); article.setUrl("https://example.com/" + id);
        article.setCollectedAt(OffsetDateTime.now());
        articles.save(article);
    }
}

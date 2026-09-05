package com.economicbriefing.exchangerate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.economicbriefing.classifier.entity.ArticleEntity;
import com.economicbriefing.classifier.entity.ArticlePresentationEntity;
import com.economicbriefing.classifier.repository.ArticlePresentationRepository;
import com.economicbriefing.classifier.repository.ArticleRepository;
import com.economicbriefing.economicflow.EventRelationType;
import com.economicbriefing.economicflow.entity.EconomicEventEntity;
import com.economicbriefing.economicflow.entity.EventRelationEntity;
import com.economicbriefing.economicflow.entity.EventRelationEvidenceEntity;
import com.economicbriefing.economicflow.repository.EventRelationEvidenceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExchangeRateBriefingServiceTest {
    private final ArticlePresentationRepository presentations = mock(ArticlePresentationRepository.class);
    private final ArticleRepository articles = mock(ArticleRepository.class);
    private final EventRelationEvidenceRepository evidence = mock(EventRelationEvidenceRepository.class);
    private final ExchangeRateBriefingService service = new ExchangeRateBriefingService(
            presentations, articles, evidence, new ObjectMapper());

    @Test
    void usesOneFreshPresentationForBothCurrencyTopicsAndItsValidatedFlow() {
        String articleId = "AKR20260904124900002";
        ArticleEntity article = article(articleId, "원/달러 환율 하락", "엔화 강세와 외환시장 움직임", OffsetDateTime.now().minusHours(2));
        when(presentations.findByCreatedAtAfterOrderByCreatedAtDesc(any())).thenReturn(List.of(presentation(articleId)));
        when(articles.findById(articleId)).thenReturn(Optional.of(article));
        EventRelationEvidenceEntity relationEvidence = flow("엔화 강세", "원/달러 환율 하락");
        when(evidence.findByArticleId(articleId)).thenReturn(List.of(relationEvidence));

        var usd = service.find(SupportedCurrency.USD);
        var jpy = service.find(SupportedCurrency.JPY);

        assertEquals(articleId, usd.articleId());
        assertEquals(articleId, jpy.articleId());
        assertEquals("엔화 강세", usd.flow().getFirst().from());
        assertEquals("원/달러 환율 하락", usd.flow().getFirst().to());
        verify(presentations, times(2)).findByCreatedAtAfterOrderByCreatedAtDesc(any());
        verify(evidence, times(2)).findByArticleId(articleId);
        verifyNoMoreInteractions(evidence);
    }

    @Test
    void excludesOldOrPresenterlessArticles() {
        ArticleEntity old = article("old", "원/달러 환율", "외환시장", OffsetDateTime.now().minusHours(25));
        ArticleEntity current = article("current", "원/달러 환율", "외환시장", OffsetDateTime.now().minusHours(1));
        when(presentations.findByCreatedAtAfterOrderByCreatedAtDesc(any()))
                .thenReturn(List.of(presentation("old"), presentation("current")));
        when(articles.findById("old")).thenReturn(Optional.of(old));
        when(articles.findById("current")).thenReturn(Optional.of(current));
        when(evidence.findByArticleId("current")).thenReturn(List.of());

        assertEquals("current", service.find(SupportedCurrency.USD).articleId());

        reset(presentations, articles, evidence);
        when(presentations.findByCreatedAtAfterOrderByCreatedAtDesc(any())).thenReturn(List.of());
        assertNull(service.find(SupportedCurrency.USD));
        verifyNoInteractions(articles, evidence);
    }

    private static ArticlePresentationEntity presentation(String articleId) {
        ArticlePresentationEntity item = new ArticlePresentationEntity();
        item.setArticleId(articleId);
        item.setPresentationJson("""
                {"displayTitle":"엔화 강세가 원/달러 환율에 영향을 줬어요",
                 "whatHappened":"환율 움직임의 배경이에요.",
                 "whyExplanations":[{"explanation":"엔화 움직임에 원화가 함께 반응했어요."}]}""");
        return item;
    }

    private static ArticleEntity article(String id, String title, String body, OffsetDateTime publishedAt) {
        ArticleEntity article = new ArticleEntity();
        article.setId(id); article.setTitle(title); article.setBody(body); article.setSource("연합뉴스");
        article.setUrl("https://example.test/" + id); article.setPublishedAt(publishedAt); article.setCollectedAt(publishedAt);
        return article;
    }

    private static EventRelationEvidenceEntity flow(String from, String to) {
        EconomicEventEntity fromEvent = mock(EconomicEventEntity.class);
        when(fromEvent.getTitle()).thenReturn(from);
        EconomicEventEntity toEvent = mock(EconomicEventEntity.class);
        when(toEvent.getTitle()).thenReturn(to);
        EventRelationEntity relation = mock(EventRelationEntity.class);
        when(relation.getFromEvent()).thenReturn(fromEvent);
        when(relation.getToEvent()).thenReturn(toEvent);
        when(relation.getRelationType()).thenReturn(EventRelationType.CAUSE);
        EventRelationEvidenceEntity evidence = mock(EventRelationEvidenceEntity.class);
        when(evidence.getRelation()).thenReturn(relation);
        return evidence;
    }
}

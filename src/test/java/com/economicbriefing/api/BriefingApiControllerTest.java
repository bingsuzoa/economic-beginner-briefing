package com.economicbriefing.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.economicbriefing.admin.dto.ApiResponse;
import com.economicbriefing.classifier.entity.ArticleAnalysisEntity;
import com.economicbriefing.classifier.repository.ArticleAnalysisRepository;
import com.economicbriefing.classifier.repository.ArticleRepository;
import com.economicbriefing.classifier.repository.TeacherLabelRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Covers the two things the public list endpoint has to get right, both of which it once got
 * wrong: newest analyses first, and one card per article.
 */
class BriefingApiControllerTest {

    private static ArticleAnalysisEntity analysis(String articleId, String easyTitle, OffsetDateTime createdAt) {
        ArticleAnalysisEntity e = new ArticleAnalysisEntity();
        e.setArticleId(articleId);
        e.setAnalysisJson("{\"easyTitle\":\"" + easyTitle + "\"}");
        e.setModelName("gpt-4o");
        e.setPromptVersion("v1");
        // createdAt is written by @PrePersist and has no setter, so it has to go in directly.
        ReflectionTestUtils.setField(e, "createdAt", createdAt);
        return e;
    }

    private static BriefingApiController controller(ArticleAnalysisRepository analysisRepo) {
        ArticleRepository articleRepo = mock(ArticleRepository.class);
        TeacherLabelRepository labelRepo = mock(TeacherLabelRepository.class);
        when(articleRepo.findById(anyString())).thenReturn(Optional.empty());
        when(labelRepo.findAllByArticleId(anyString())).thenReturn(List.of());
        return new BriefingApiController(analysisRepo, articleRepo, labelRepo, new ObjectMapper());
    }

    @Test
    void shouldReturnNewestAnalysesFirst() {
        OffsetDateTime now = OffsetDateTime.now();
        ArticleAnalysisRepository repo = mock(ArticleAnalysisRepository.class);
        when(repo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(
                analysis("new", "오늘 16시 분석", now),
                analysis("old", "어제 분석", now.minusDays(1))));

        ApiResponse<List<JsonNode>> response = controller(repo).listAnalyzedArticles();

        assertEquals(2, response.data().size());
        assertEquals("오늘 16시 분석", response.data().get(0).get("easyTitle").asText());
        assertEquals("어제 분석", response.data().get(1).get("easyTitle").asText());
    }

    @Test
    void shouldKeepOnlyTheNewestAnalysisPerArticle() {
        OffsetDateTime now = OffsetDateTime.now();
        ArticleAnalysisRepository repo = mock(ArticleAnalysisRepository.class);
        // Same article analysed twice; the repository hands them over newest first.
        when(repo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(
                analysis("dup", "다시 분석한 것", now),
                analysis("dup", "처음 분석한 것", now.minusHours(2)),
                analysis("other", "다른 기사", now.minusHours(3))));

        ApiResponse<List<JsonNode>> response = controller(repo).listAnalyzedArticles();

        assertEquals(2, response.data().size(), "duplicate articleId must render once");
        assertEquals("다시 분석한 것", response.data().get(0).get("easyTitle").asText());
        assertEquals("다른 기사", response.data().get(1).get("easyTitle").asText());
    }

    @Test
    void shouldNotUseUnorderedFindAll() {
        ArticleAnalysisRepository repo = mock(ArticleAnalysisRepository.class);
        when(repo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        controller(repo).listAnalyzedArticles();

        verify(repo, never()).findAll();
    }
}

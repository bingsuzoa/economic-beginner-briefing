package com.economicbriefing.api;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.economicbriefing.admin.dto.ApiResponse;
import com.economicbriefing.classifier.entity.ArticleAnalysisEntity;
import com.economicbriefing.classifier.entity.ArticleEntity;
import com.economicbriefing.classifier.entity.TeacherLabelEntity;
import com.economicbriefing.classifier.repository.ArticleAnalysisRepository;
import com.economicbriefing.classifier.repository.ArticlePresentationRepository;
import com.economicbriefing.classifier.repository.ArticleRepository;
import com.economicbriefing.classifier.repository.TeacherLabelRepository;
import com.economicbriefing.reading.entity.ArticleReadingHistoryEntity;
import com.economicbriefing.reading.repository.ArticleReadingHistoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/briefing")
public class BriefingApiController {

    private static final String SESSION_USER_ID = "USER_ID";

    private final ArticleAnalysisRepository analysisRepo;
    private final ArticlePresentationRepository presentationRepo;
    private final ArticleRepository articleRepo;
    private final TeacherLabelRepository labelRepo;
    private final ArticleReadingHistoryRepository readingHistoryRepo;
    private final ObjectMapper objectMapper;

    public BriefingApiController(
            ArticleAnalysisRepository analysisRepo,
            ArticlePresentationRepository presentationRepo,
            ArticleRepository articleRepo,
            TeacherLabelRepository labelRepo,
            ArticleReadingHistoryRepository readingHistoryRepo,
            ObjectMapper objectMapper) {
        this.analysisRepo = analysisRepo;
        this.presentationRepo = presentationRepo;
        this.articleRepo = articleRepo;
        this.labelRepo = labelRepo;
        this.readingHistoryRepo = readingHistoryRepo;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/articles")
    public ApiResponse<List<JsonNode>> listAnalyzedArticles(HttpServletRequest request) {
        // Newest first, one card per article. The frontend renders this list in order and does
        // no sorting of its own, so the ordering has to come from here. Re-analysing an article
        // inserts another row instead of replacing one, so the same article could appear twice;
        // keeping the first occurrence of each id keeps the newest analysis and drops the stale
        // duplicates rather than deleting anything.
        // Only show today's articles (from 00:00:00 of current day)
        OffsetDateTime startOfToday = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
        List<ArticleAnalysisEntity> analyses = analysisRepo.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(startOfToday);
        List<JsonNode> result = new ArrayList<>();
        Set<String> seenArticleIds = new HashSet<>();

        // Get user ID from session (optional - may be null if not logged in)
        HttpSession session = request.getSession(false);
        String userId = (session != null) ? (String) session.getAttribute(SESSION_USER_ID) : null;

        // Fetch reading history for this user (if logged in)
        java.util.Map<String, ArticleReadingHistoryEntity> readingHistory = new java.util.HashMap<>();
        if (userId != null) {
            List<String> articleIds = analyses.stream()
                    .map(ArticleAnalysisEntity::getArticleId)
                    .distinct()
                    .toList();
            List<ArticleReadingHistoryEntity> historyList = readingHistoryRepo.findByUserIdAndArticleIdIn(userId, articleIds);
            for (ArticleReadingHistoryEntity h : historyList) {
                readingHistory.put(h.getArticleId(), h);
            }
        }

        for (ArticleAnalysisEntity analysis : analyses) {
            if (!seenArticleIds.add(analysis.getArticleId())) {
                continue;
            }
            try {
                ObjectNode node = (ObjectNode) objectMapper.readTree(analysis.getAnalysisJson());
                // Enrich with article metadata
                ArticleEntity article = articleRepo.findById(analysis.getArticleId()).orElse(null);
                if (article != null) {
                    node.put("originalTitle", article.getTitle());
                    node.put("sourceName", article.getSource());
                    node.put("originalUrl", article.getUrl());
                    node.put("author", article.getAuthor());
                    node.put("publishedAt", article.getPublishedAt() != null
                            ? article.getPublishedAt().toString() : null);
                }
                node.put("articleId", analysis.getArticleId());
                node.put("analyzedAt", analysis.getCreatedAt().toString());
                node.put("modelName", analysis.getModelName());
                node.put("promptVersion", analysis.getPromptVersion());

                // Add teacher label info
                List<TeacherLabelEntity> labels = labelRepo.findAllByArticleId(analysis.getArticleId());
                if (!labels.isEmpty()) {
                    TeacherLabelEntity label = labels.get(0);
                    node.put("teacherLabel", label.getLabel());
                    node.put("teacherConfidence", label.getConfidence());
                }

                // Add reading history info
                ArticleReadingHistoryEntity history = readingHistory.get(analysis.getArticleId());
                if (history != null) {
                    node.put("readAt", history.getReadAt().toString());
                }

                result.add(node);
            } catch (Exception e) {
                // Skip malformed analysis
            }
        }

        return ApiResponse.ok(result);
    }

    @PostMapping("/articles/{articleId}/mark-read")
    public ResponseEntity<ApiResponse<?>> markArticleAsRead(@PathVariable String articleId,
                                                             HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("UNAUTHORIZED", "로그인이 필요합니다."));
        }

        String userId = (String) session.getAttribute(SESSION_USER_ID);
        if (userId == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("UNAUTHORIZED", "로그인이 필요합니다."));
        }

        // Check if already read
        var existing = readingHistoryRepo.findByUserIdAndArticleId(userId, articleId);
        if (existing.isPresent()) {
            // Already marked as read - return existing
            return ResponseEntity.ok(ApiResponse.ok(java.util.Map.of(
                    "readAt", existing.get().getReadAt().toString()
            )));
        }

        // Create new reading history entry
        ArticleReadingHistoryEntity history = new ArticleReadingHistoryEntity();
        history.setUserId(userId);
        history.setArticleId(articleId);
        readingHistoryRepo.save(history);

        return ResponseEntity.ok(ApiResponse.ok(java.util.Map.of(
                "readAt", history.getReadAt().toString()
        )));
    }

    @GetMapping("/articles/{articleId}")
    public ResponseEntity<ApiResponse<?>> getArticleDetail(@PathVariable String articleId) {
        List<ArticleAnalysisEntity> analyses = analysisRepo.findByArticleId(articleId);
        if (analyses.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("NOT_FOUND", "분석 결과를 찾을 수 없습니다."));
        }

        ArticleAnalysisEntity analysis = analyses.get(0);
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(analysis.getAnalysisJson());
            ArticleEntity article = articleRepo.findById(articleId).orElse(null);
            if (article != null) {
                node.put("originalTitle", article.getTitle());
                node.put("sourceName", article.getSource());
                node.put("originalUrl", article.getUrl());
                node.put("author", article.getAuthor());
                node.put("publishedAt", article.getPublishedAt() != null
                        ? article.getPublishedAt().toString() : null);
            }
            node.put("articleId", articleId);
            node.put("analyzedAt", analysis.getCreatedAt().toString());
            node.put("modelName", analysis.getModelName());
            node.put("promptVersion", analysis.getPromptVersion());

            List<TeacherLabelEntity> labels = labelRepo.findAllByArticleId(articleId);
            if (!labels.isEmpty()) {
                TeacherLabelEntity label = labels.get(0);
                node.put("teacherLabel", label.getLabel());
                node.put("teacherConfidence", label.getConfidence());
            }

            return ResponseEntity.ok(ApiResponse.ok(node));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("PARSE_ERROR", "분석 데이터 파싱 실패"));
        }
    }

    @GetMapping("/articles/{articleId}/presentation")
    public ResponseEntity<ApiResponse<?>> getArticlePresentation(@PathVariable String articleId) {
        var presentations = presentationRepo.findByArticleIdOrderByCreatedAtDesc(articleId);
        if (presentations.isEmpty()) return ResponseEntity.status(404)
                .body(ApiResponse.error("NOT_FOUND", "사용자용 기사 설명을 찾을 수 없습니다."));
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(presentations.getFirst().getPresentationJson());
            node.put("articleId", articleId);
            node.put("presentedAt", presentations.getFirst().getCreatedAt().toString());
            return ResponseEntity.ok(ApiResponse.ok(node));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error("PARSE_ERROR", "기사 설명 데이터 파싱 실패"));
        }
    }
}

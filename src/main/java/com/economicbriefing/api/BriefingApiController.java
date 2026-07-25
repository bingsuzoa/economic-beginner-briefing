package com.economicbriefing.api;

import java.util.ArrayList;
import java.util.List;

import com.economicbriefing.admin.dto.ApiResponse;
import com.economicbriefing.classifier.entity.ArticleAnalysisEntity;
import com.economicbriefing.classifier.entity.ArticleEntity;
import com.economicbriefing.classifier.entity.TeacherLabelEntity;
import com.economicbriefing.classifier.repository.ArticleAnalysisRepository;
import com.economicbriefing.classifier.repository.ArticleRepository;
import com.economicbriefing.classifier.repository.TeacherLabelRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/briefing")
@CrossOrigin(origins = "*")
public class BriefingApiController {

    private final ArticleAnalysisRepository analysisRepo;
    private final ArticleRepository articleRepo;
    private final TeacherLabelRepository labelRepo;
    private final ObjectMapper objectMapper;

    public BriefingApiController(
            ArticleAnalysisRepository analysisRepo,
            ArticleRepository articleRepo,
            TeacherLabelRepository labelRepo,
            ObjectMapper objectMapper) {
        this.analysisRepo = analysisRepo;
        this.articleRepo = articleRepo;
        this.labelRepo = labelRepo;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/articles")
    public ApiResponse<List<JsonNode>> listAnalyzedArticles() {
        List<ArticleAnalysisEntity> analyses = analysisRepo.findAll();
        List<JsonNode> result = new ArrayList<>();

        for (ArticleAnalysisEntity analysis : analyses) {
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

                result.add(node);
            } catch (Exception e) {
                // Skip malformed analysis
            }
        }

        return ApiResponse.ok(result);
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
}

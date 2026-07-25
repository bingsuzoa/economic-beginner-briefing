package com.economicbriefing.classifier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import com.economicbriefing.classifier.entity.ArticleEntity;
import com.economicbriefing.classifier.repository.ArticleRepository;
import com.economicbriefing.domain.article.Article;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ArticlePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ArticlePersistenceService.class);

    private final ArticleRepository articleRepository;

    public ArticlePersistenceService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    /**
     * Saves articles to DB, skipping duplicates by contentHash.
     * Returns the number of newly saved articles.
     */
    public int saveAll(List<Article> articles) {
        int saved = 0;
        for (Article article : articles) {
            if (save(article)) {
                saved++;
            }
        }
        log.info("Article persistence: total={}, saved={}, skipped={}",
                articles.size(), saved, articles.size() - saved);
        return saved;
    }

    /**
     * Saves a single article. Returns true if newly inserted, false if duplicate.
     */
    public boolean save(Article article) {
        String hash = contentHash(article.title(), article.content());

        if (articleRepository.findByContentHash(hash).isPresent()) {
            log.debug("Duplicate article skipped: id={}, hash={}", article.id(), hash);
            return false;
        }

        ArticleEntity entity = toEntity(article, hash);
        articleRepository.save(entity);
        return true;
    }

    private ArticleEntity toEntity(Article article, String hash) {
        ArticleEntity e = new ArticleEntity();
        e.setId(article.id());
        e.setSource(article.sourceName());
        e.setTitle(article.title());
        e.setBody(article.content());
        e.setSummary(article.summary());
        e.setUrl(article.url());
        e.setCategory(article.categories() != null && !article.categories().isEmpty()
                ? article.categories().get(0).name() : null);
        e.setPublishedAt(article.publishedAt());
        e.setCollectedAt(article.collectedAt());
        e.setContentHash(hash);
        e.setLanguage(article.language() != null ? article.language() : "ko");
        return e;
    }

    static String contentHash(String title, String body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = (title != null ? title : "") + "|" + (body != null ? body : "");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}

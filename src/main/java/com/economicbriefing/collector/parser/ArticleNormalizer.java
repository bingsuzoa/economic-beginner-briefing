package com.economicbriefing.collector.parser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.List;

import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;
import com.economicbriefing.util.KstDateTimeUtil;
import org.springframework.stereotype.Component;

@Component
public class ArticleNormalizer {

    public Article normalize(
            String title,
            String url,
            OffsetDateTime publishedAt,
            String summary,
            String sourceName,
            ArticleSourceType sourceType,
            List<NewsCategory> categories,
            String content,
            String guid
    ) {
        String id = generateArticleId(url, guid);

        return new Article(
                id,
                title.trim(),
                summary != null ? summary.trim() : "",
                sourceName,
                sourceType,
                publishedAt,
                KstDateTimeUtil.now(),
                url,
                categories,
                "ko",
                content != null ? content.trim() : null
        );
    }

    private String generateArticleId(String url, String guid) {
        String source = (guid != null && !guid.isBlank()) ? guid : url;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(Math.abs(source.hashCode()));
        }
    }
}

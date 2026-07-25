package com.economicbriefing.collector.parser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.economicbriefing.domain.article.Article;
import com.economicbriefing.domain.article.ArticleSourceType;
import com.economicbriefing.domain.article.NewsCategory;
import com.economicbriefing.util.KstDateTimeUtil;
import org.springframework.stereotype.Component;

@Component
public class ArticleNormalizer {

    private static final Pattern ENTITY = Pattern.compile("&(?:#[xX]?[0-9a-fA-F]+|[a-zA-Z]+);");
    private static final Map<String, String> NAMED = Map.of(
            "&amp;", "&",
            "&lt;", "<",
            "&gt;", ">",
            "&quot;", "\"",
            "&apos;", "'",
            "&nbsp;", " ");

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
                decodeEntities(title).trim(),
                summary != null ? decodeEntities(summary).trim() : "",
                sourceName,
                sourceType,
                publishedAt,
                KstDateTimeUtil.now(),
                url,
                categories,
                "ko",
                content != null ? decodeEntities(content).trim() : null
        );
    }

    /**
     * RSS feeds deliver titles double-escaped (&amp;#34; for a quote), which otherwise
     * reaches the browser verbatim. Decoding here covers every source adapter at once.
     */
    static String decodeEntities(String text) {
        if (text == null || text.indexOf('&') < 0) {
            return text;
        }

        String previous;
        String decoded = text;
        // feeds escape twice (&amp;#34; -> &#34; -> "), so decode until it settles
        int rounds = 0;
        do {
            previous = decoded;
            decoded = decodeOnce(previous);
        } while (!decoded.equals(previous) && ++rounds < 3);

        return decoded;
    }

    private static String decodeOnce(String text) {
        Matcher matcher = ENTITY.matcher(text);
        StringBuilder out = new StringBuilder();

        while (matcher.find()) {
            String replacement = resolve(matcher.group());
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);

        return out.toString();
    }

    private static String resolve(String entity) {
        String named = NAMED.get(entity);
        if (named != null) {
            return named;
        }
        try {
            String digits = entity.substring(2, entity.length() - 1);
            int codePoint = digits.startsWith("x") || digits.startsWith("X")
                    ? Integer.parseInt(digits.substring(1), 16)
                    : Integer.parseInt(digits);
            return Character.isValidCodePoint(codePoint)
                    ? new String(Character.toChars(codePoint))
                    : entity;
        } catch (RuntimeException e) {
            return entity; // leave anything unrecognised untouched
        }
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

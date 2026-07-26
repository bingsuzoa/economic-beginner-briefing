package com.economicbriefing.config;

import java.time.Duration;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets the browser keep the frontend's static files.
 *
 * <p>Spring Security stamps {@code no-cache, no-store, must-revalidate} on every response by
 * default, which is right for the API and for {@code index.html} but wrong for the assets:
 * the images under {@code /images/} total about 7.7 MB and were being re-downloaded on every
 * single page load, over a home uplink.
 *
 * <p>Only two prefixes are opened up, and they are treated differently because their names mean
 * different things:
 *
 * <ul>
 *   <li>{@code /assets/**} — Vite writes a content hash into these filenames, so a changed file
 *       is a different URL. Safe to mark immutable for a year.
 *   <li>{@code /images/**} — copied verbatim out of {@code frontend/public}, so the name stays
 *       the same when the picture changes. One day only. To publish a new image sooner, rename
 *       it; that is the only cache-busting handle these files have.
 * </ul>
 *
 * <p>Everything else keeps Security's no-store, and that matters most for {@code index.html}:
 * it names the hashed bundles, so a cached copy would outlive the files it points at and the
 * page would go blank after a deploy.
 */
@Configuration
public class StaticResourceCacheConfig implements WebMvcConfigurer {

    private final String[] staticLocations;

    public StaticResourceCacheConfig(
            @Value("${spring.web.resources.static-locations}") String staticLocations) {
        this.staticLocations = Arrays.stream(staticLocations.split(","))
                .map(String::trim)
                .filter(location -> !location.isEmpty())
                .map(location -> location.endsWith("/") ? location : location + "/")
                .toArray(String[]::new);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // More specific patterns win over the auto-configured '/**' handler, so these two take
        // precedence and everything else still falls through to the default.
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(under("assets/"))
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

        registry.addResourceHandler("/images/**")
                .addResourceLocations(under("images/"))
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic());
    }

    /** Same roots as spring.web.resources.static-locations, narrowed to one subdirectory. */
    private String[] under(String subdirectory) {
        return Arrays.stream(staticLocations)
                .map(location -> location + subdirectory)
                .toArray(String[]::new);
    }
}

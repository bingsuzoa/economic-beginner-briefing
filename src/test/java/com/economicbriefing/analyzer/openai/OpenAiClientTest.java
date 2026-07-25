package com.economicbriefing.analyzer.openai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;

import javax.net.ssl.SSLSession;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiClientTest {

    @Test
    void shouldParseRetryAfterFromRateLimitMessage() {
        String body = """
                {"error":{"message":"Rate limit reached for gpt-4o on tokens per min (TPM): \
                Limit 30000, Used 22848, Requested 11099. Please try again in 7.894s.",\
                "code":"rate_limit_exceeded"}}""";

        Duration retryAfter = OpenAiClient.parseRetryAfter(new FakeResponse(body, Map.of()));

        assertNotNull(retryAfter);
        assertTrue(retryAfter.toMillis() >= 7894,
                "must wait at least the advised 7.894s, got " + retryAfter.toMillis() + "ms");
    }

    @Test
    void shouldPreferRetryAfterMsHeader() {
        Duration retryAfter = OpenAiClient.parseRetryAfter(
                new FakeResponse("{}", Map.of("retry-after-ms", List.of("2500"))));

        assertNotNull(retryAfter);
        assertTrue(retryAfter.toMillis() >= 2500);
    }

    @Test
    void shouldParseMillisecondHint() {
        Duration retryAfter = OpenAiClient.parseRetryAfter(
                new FakeResponse("Please try again in 20ms.", Map.of()));

        assertNotNull(retryAfter);
        assertTrue(retryAfter.toMillis() < 1000);
    }

    @Test
    void shouldReturnNullWhenNoHintPresent() {
        assertNull(OpenAiClient.parseRetryAfter(new FakeResponse("{\"error\":{}}", Map.of())));
    }

    /** Minimal HttpResponse stub: only body() and headers() are read by parseRetryAfter. */
    private record FakeResponse(String bodyText, Map<String, List<String>> headerMap)
            implements HttpResponse<String> {

        @Override public int statusCode() { return 429; }
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() {
            BiPredicate<String, String> all = (a, b) -> true;
            return HttpHeaders.of(headerMap, all);
        }
        @Override public String body() { return bodyText; }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return URI.create("https://api.openai.com"); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}

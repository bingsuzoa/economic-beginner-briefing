package com.economicbriefing.auth.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "sms.provider", havingValue = "prod")
public class ProdSmsService implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(ProdSmsService.class);
    private static final String API_URL = "https://api.solapi.com/messages/v4/send";

    private final String apiKey;
    private final String apiSecret;
    private final String senderNumber;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ProdSmsService(@Value("${sms.api-key}") String apiKey,
                          @Value("${sms.api-secret}") String apiSecret,
                          @Value("${sms.sender-number}") String senderNumber,
                          ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.senderNumber = senderNumber;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public void sendCode(String phone, String code) {
        String text = "[병아리경제] 인증번호 " + code + " 를 입력해주세요.";
        try {
            String body = objectMapper.writeValueAsString(
                    new SendRequest(new Message(phone, senderNumber, text)));

            String auth = buildAuthHeader();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", auth)
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                log.error("SOLAPI 발송 실패: status={}, body={}", response.statusCode(), response.body());
                throw new SmsService.SmsSendFailedException();
            }

            // Check response for per-message error
            JsonNode root = objectMapper.readTree(response.body());
            String statusCode = root.path("statusCode").asText("");
            if (statusCode.startsWith("4") || statusCode.startsWith("5")) {
                log.error("SOLAPI 메시지 오류: {}", response.body());
                throw new SmsService.SmsSendFailedException();
            }

            log.info("SMS 발송 성공: phone={}", phone);

        } catch (SmsService.SmsSendFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error("SMS 발송 중 예외: {}", e.getMessage(), e);
            throw new SmsService.SmsSendFailedException();
        }
    }

    private String buildAuthHeader() {
        String date = Instant.now().toString();
        String salt = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String signature = hmacSha256(apiSecret, date + salt);
        return "HMAC-SHA256 apiKey=" + apiKey + ", date=" + date + ", salt=" + salt + ", signature=" + signature;
    }

    private static String hmacSha256(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 서명 생성 실패", e);
        }
    }

    record Message(String to, String from, String text) {}
    record SendRequest(Message message) {}
}

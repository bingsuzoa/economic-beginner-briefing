package com.economicbriefing.auth;

import com.economicbriefing.auth.service.ProdSmsService;
import com.economicbriefing.auth.service.SmsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdSmsServiceTest {

    /**
     * Bad credentials → SOLAPI returns 4xx → SmsSendFailedException.
     * Verifies HMAC header construction, HTTP call, and error wrapping in one shot.
     */
    @Test
    void badCredentialsThrowSmsSendFailed() {
        ProdSmsService service = new ProdSmsService(
                "invalid-key", "invalid-secret", "029302266", new ObjectMapper());

        assertThatThrownBy(() -> service.sendCode("01012345678", "123456"))
                .isInstanceOf(SmsService.SmsSendFailedException.class)
                .hasMessage("인증번호 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");
    }

    /**
     * Real send test — only runs when SMS_API_KEY env var is set.
     * Set SMS_API_KEY, SMS_API_SECRET, SMS_SENDER_NUMBER, SMS_TEST_PHONE to run.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "SMS_API_KEY", matches = ".+")
    void realSendTest() {
        ProdSmsService service = new ProdSmsService(
                System.getenv("SMS_API_KEY"),
                System.getenv("SMS_API_SECRET"),
                System.getenv("SMS_SENDER_NUMBER"),
                new ObjectMapper());

        // This actually sends an SMS — use a test phone you own
        service.sendCode(System.getenv("SMS_TEST_PHONE"), "999999");
    }
}

package com.economicbriefing.auth;

import com.economicbriefing.auth.service.SmsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SmsAuthIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @MockitoBean SmsService smsService;

    private final String PHONE = "01012345678";

    // ── helpers ──

    private String captureCode() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(smsService, atLeastOnce()).sendCode(eq(PHONE), captor.capture());
        return captor.getValue();
    }

    /** Send SMS + verify code. Returns session with VERIFIED_PHONE. */
    private MockHttpSession sendAndVerify() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/sms/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("phone", PHONE)))
                .session(session))
                .andExpect(status().isOk());

        String code = captureCode();

        mvc.perform(post("/api/auth/sms/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("phone", PHONE, "code", code)))
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        return session;
    }

    private MockHttpSession fullSignup() throws Exception {
        MockHttpSession session = sendAndVerify();

        mvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                        "name", "테스트", "nickname", "tester", "gender", "남성")))
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.user.name").value("테스트"));

        return session;
    }

    // ── 1. Full flow: send → verify → signup ──

    @Test
    void fullSignupFlow() throws Exception {
        MockHttpSession session = fullSignup();

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("테스트"))
                .andExpect(jsonPath("$.nickname").value("tester"));
    }

    // ── 2. Existing user login via /login endpoint ──

    @Test
    void existingUserLogin() throws Exception {
        fullSignup();
        reset(smsService);

        MockHttpSession session2 = new MockHttpSession();
        mvc.perform(post("/api/auth/sms/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("phone", PHONE)))
                .session(session2))
                .andExpect(status().isOk());

        String code = captureCode();

        mvc.perform(post("/api/auth/sms/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("phone", PHONE, "code", code)))
                .session(session2))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/login").session(session2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.user.name").value("테스트"));
    }

    // ── 3. Login with non-existing phone ──

    @Test
    void loginNonExistingPhone() throws Exception {
        MockHttpSession session = sendAndVerify();

        mvc.perform(post("/api/auth/login").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("가입되지 않은 번호입니다."));
    }

    // ── 4. Check-member existing ──

    @Test
    void checkMemberExists() throws Exception {
        fullSignup();
        reset(smsService);

        MockHttpSession session2 = sendAndVerify();

        mvc.perform(post("/api/auth/check-member").session(session2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true));
    }

    // ── 5. Check-member new ──

    @Test
    void checkMemberNew() throws Exception {
        MockHttpSession session = sendAndVerify();

        mvc.perform(post("/api/auth/check-member").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false));
    }

    // ── 6. Wrong code ──

    @Test
    void wrongCodeReturnsError() throws Exception {
        mvc.perform(post("/api/auth/sms/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("phone", PHONE))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/sms/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("phone", PHONE, "code", "000000"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("인증번호가 일치하지 않습니다."));
    }

    // ── 7. Verify without sending ──

    @Test
    void verifyWithoutSendingFails() throws Exception {
        mvc.perform(post("/api/auth/sms/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("phone", "01099999999", "code", "123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("인증 요청을 찾을 수 없습니다."));
    }

    // ── 8. Resend throttle (429) ──

    @Test
    void resendThrottle() throws Exception {
        mvc.perform(post("/api/auth/sms/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("phone", PHONE))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/sms/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("phone", PHONE))))
                .andExpect(status().is(429));
    }

    // ── 9. Nickname duplicate check ──

    @Test
    void nicknameDuplicateCheck() throws Exception {
        fullSignup();

        mvc.perform(get("/api/auth/check-nickname").param("nickname", "tester"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taken").value(true));

        mvc.perform(get("/api/auth/check-nickname").param("nickname", "unique123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taken").value(false));
    }

    // ── 10. /me without auth → 401 ──

    @Test
    void meWithoutAuth() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // ── 11. Signup without verification → 403 ──

    @Test
    void signupWithoutVerification() throws Exception {
        mvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                        "name", "해커", "nickname", "hacker"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("전화번호 인증이 필요합니다."));
    }

    // ── 12. SMS send failure → 500 ──

    @Test
    void smsSendFailureReturns500() throws Exception {
        doThrow(new SmsService.SmsSendFailedException())
                .when(smsService).sendCode(anyString(), anyString());

        mvc.perform(post("/api/auth/sms/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("phone", "01099998888"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("인증번호 발송에 실패했습니다. 잠시 후 다시 시도해주세요."));
    }

    // ── 13. Logout invalidates session ──

    @Test
    void logoutInvalidatesSession() throws Exception {
        MockHttpSession session = fullSignup();

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk());

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    // ── 14. Login without verification → 403 ──

    @Test
    void loginWithoutVerification() throws Exception {
        mvc.perform(post("/api/auth/login"))
                .andExpect(status().isForbidden());
    }
}

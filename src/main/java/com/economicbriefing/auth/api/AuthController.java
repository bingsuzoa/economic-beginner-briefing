package com.economicbriefing.auth.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.economicbriefing.auth.entity.UserEntity;
import com.economicbriefing.auth.repository.UserRepository;
import com.economicbriefing.auth.service.SmsAuthService;
import com.economicbriefing.auth.service.SmsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String SESSION_USER_ID = "USER_ID";
    static final String SESSION_VERIFIED_PHONE = "VERIFIED_PHONE";

    private final UserRepository userRepository;
    private final SmsAuthService smsAuthService;
    private final boolean devMode;

    public AuthController(UserRepository userRepository, SmsAuthService smsAuthService,
                          @org.springframework.beans.factory.annotation.Value("${sms.provider:dev}") String smsProvider) {
        this.userRepository = userRepository;
        this.smsAuthService = smsAuthService;
        this.devMode = "dev".equals(smsProvider);
    }

    @PostMapping("/sms/send")
    public ResponseEntity<Map<String, Object>> sendSms(@RequestBody Map<String, String> body,
                                                        HttpServletRequest request) {
        String phone = body.get("phone");
        if (phone == null || phone.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "전화번호를 입력해주세요."));
        }
        try {
            String code = smsAuthService.sendCode(phone, request.getRemoteAddr());
            Map<String, Object> resp = new LinkedHashMap<>(Map.of("success", true, "message", "인증번호를 발송했습니다."));
            if (devMode) resp.put("devCode", code);
            return ResponseEntity.ok(resp);
        } catch (SmsAuthService.TooManyRequestsException e) {
            return ResponseEntity.status(429).body(Map.of("success", false, "message", e.getMessage()));
        } catch (SmsService.SmsSendFailedException e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /** Verify SMS code. Stores VERIFIED_PHONE in session. Does NOT auto-login. */
    @PostMapping("/sms/verify")
    public ResponseEntity<Map<String, Object>> verifySms(@RequestBody Map<String, String> body,
                                                          HttpServletRequest request) {
        String phone = body.get("phone");
        String code = body.get("code");
        if (phone == null || code == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "전화번호와 인증번호를 입력해주세요."));
        }
        try {
            smsAuthService.verifyCode(phone, code);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }

        String normalized = smsAuthService.normalizePhone(phone);
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_VERIFIED_PHONE, normalized);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** Login: requires VERIFIED_PHONE in session, finds existing user by phone. */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SESSION_VERIFIED_PHONE) == null) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "전화번호 인증이 필요합니다."));
        }
        String phone = (String) session.getAttribute(SESSION_VERIFIED_PHONE);
        var user = userRepository.findByPhone(phone).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "가입되지 않은 번호입니다."));
        }
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);
        session.removeAttribute(SESSION_VERIFIED_PHONE);
        session.setAttribute(SESSION_USER_ID, user.getId());
        return ResponseEntity.ok(Map.of("success", true, "user", toUserMap(user)));
    }

    /** Check if a verified phone already has an account. */
    @PostMapping("/check-member")
    public ResponseEntity<Map<String, Object>> checkMember(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SESSION_VERIFIED_PHONE) == null) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "전화번호 인증이 필요합니다."));
        }
        String phone = (String) session.getAttribute(SESSION_VERIFIED_PHONE);
        return ResponseEntity.ok(Map.of("exists", userRepository.existsByPhone(phone)));
    }

    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> signup(@RequestBody SignupRequest body,
                                                       HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SESSION_VERIFIED_PHONE) == null) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "전화번호 인증이 필요합니다."));
        }

        String phone = (String) session.getAttribute(SESSION_VERIFIED_PHONE);

        if (userRepository.existsByPhone(phone)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "이미 가입된 전화번호입니다."));
        }
        if (body.nickname() != null && userRepository.existsByNickname(body.nickname())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "이미 사용 중인 닉네임입니다."));
        }

        UserEntity user = new UserEntity(
                UUID.randomUUID().toString(), phone,
                body.name(), body.nickname(), body.birthDate(), body.gender());
        userRepository.save(user);

        session.removeAttribute(SESSION_VERIFIED_PHONE);
        session.setAttribute(SESSION_USER_ID, user.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("user", toUserMap(user));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return ResponseEntity.status(401).build();

        String userId = (String) session.getAttribute(SESSION_USER_ID);
        if (userId == null) return ResponseEntity.status(401).build();

        return userRepository.findById(userId)
                .map(u -> ResponseEntity.ok(toUserMap(u)))
                .orElse(ResponseEntity.status(401).build());
    }

    @GetMapping("/check-nickname")
    public ResponseEntity<Map<String, Object>> checkNickname(@RequestParam String nickname) {
        return ResponseEntity.ok(Map.of("taken", userRepository.existsByNickname(nickname)));
    }

    private Map<String, Object> toUserMap(UserEntity user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", user.getId());
        body.put("name", user.getName());
        body.put("nickname", user.getNickname());
        body.put("phone", user.getPhone());
        body.put("profileImageUrl", user.getProfileImageUrl());
        return body;
    }

    record SignupRequest(String name, String nickname, LocalDate birthDate, String gender) {}
}

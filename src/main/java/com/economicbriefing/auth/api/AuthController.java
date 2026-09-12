package com.economicbriefing.auth.api;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.economicbriefing.auth.entity.UserEntity;
import com.economicbriefing.auth.repository.UserRepository;
import com.economicbriefing.auth.service.AccountDeletionService;
import com.economicbriefing.auth.service.EmailCryptoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String SESSION_USER_ID = "USER_ID";
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{4,30}$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailCryptoService emailCrypto;
    private final AccountDeletionService accountDeletionService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          EmailCryptoService emailCrypto, AccountDeletionService accountDeletionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailCrypto = emailCrypto;
        this.accountDeletionService = accountDeletionService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest body,
                                                       HttpServletRequest request) {
        String username = normalizeUsername(body.username());
        UserEntity user = username == null ? null : userRepository.findByUsername(username).orElse(null);
        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(value(body.password()), user.getPasswordHash())) {
            return bad("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(SESSION_USER_ID, user.getId());
        return ResponseEntity.ok(Map.of("success", true, "user", toUserMap(user)));
    }

    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> signup(@RequestBody SignupRequest body,
                                                       HttpServletRequest request) {
        String username = normalizeUsername(body.username());
        String email = emailCrypto.normalize(body.email());
        String nickname = value(body.nickname()).trim();
        String password = value(body.password());

        if (username == null || !USERNAME.matcher(username).matches()) return bad("아이디는 영문, 숫자, 밑줄 4~30자로 입력해주세요.");
        if (!EMAIL.matcher(email).matches()) return bad("올바른 이메일 형식을 입력해주세요.");
        if (password.length() < 8) return bad("비밀번호는 8자 이상 입력해주세요.");
        if (!password.equals(value(body.passwordConfirm()))) return bad("비밀번호가 일치하지 않습니다.");
        if (nickname.length() < 2 || nickname.length() > 20) return bad("닉네임은 2~20자로 입력해주세요.");
        if (userRepository.existsByUsername(username)) return bad("이미 사용 중인 아이디입니다.");
        if (userRepository.existsByEmailHash(emailCrypto.hash(email))) return bad("이미 사용 중인 이메일입니다.");
        if (userRepository.existsByNickname(nickname)) return bad("이미 사용 중인 닉네임입니다.");

        UserEntity user = new UserEntity(UUID.randomUUID().toString(), username,
                emailCrypto.encrypt(email), emailCrypto.hash(email), passwordEncoder.encode(password), nickname);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            return bad("이미 사용 중인 회원 정보입니다.");
        }

        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(SESSION_USER_ID, user.getId());
        return ResponseEntity.ok(Map.of("success", true, "user", toUserMap(user)));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return ResponseEntity.status(401).build();
        String userId = (String) session.getAttribute(SESSION_USER_ID);
        if (userId == null) return ResponseEntity.status(401).build();
        return userRepository.findById(userId).map(u -> ResponseEntity.ok(toUserMap(u)))
                .orElse(ResponseEntity.status(401).build());
    }

    @DeleteMapping("/me")
    public ResponseEntity<Map<String, Object>> deleteMe(@RequestBody DeleteRequest body,
                                                         HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String userId = session == null ? null : (String) session.getAttribute(SESSION_USER_ID);
        if (userId == null) return ResponseEntity.status(401).build();
        if (!accountDeletionService.deleteAuthenticated(userId, value(body.password()))) {
            return bad("비밀번호가 올바르지 않습니다.");
        }
        session.invalidate();
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/delete-account")
    public ResponseEntity<Map<String, Object>> deleteAccount(@RequestBody ExternalDeleteRequest body,
                                                              HttpServletRequest request) {
        String username = normalizeUsername(body.username());
        if (username == null || !accountDeletionService.deleteWithCredentials(username, value(body.password()))) {
            return bad("아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/check-username")
    public Map<String, Boolean> checkUsername(@RequestParam String username) {
        String normalized = normalizeUsername(username);
        return Map.of("taken", normalized != null && userRepository.existsByUsername(normalized));
    }

    @GetMapping("/check-email")
    public Map<String, Boolean> checkEmail(@RequestParam String email) {
        String normalized = emailCrypto.normalize(email);
        return Map.of("taken", !normalized.isBlank() && userRepository.existsByEmailHash(emailCrypto.hash(normalized)));
    }

    @GetMapping("/check-nickname")
    public Map<String, Boolean> checkNickname(@RequestParam String nickname) {
        return Map.of("taken", userRepository.existsByNickname(nickname.trim()));
    }

    private ResponseEntity<Map<String, Object>> bad(String message) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", message));
    }

    private Map<String, Object> toUserMap(UserEntity user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", user.getId());
        body.put("username", user.getUsername());
        body.put("nickname", user.getNickname());
        body.put("email", maskEmail(user.getEmailEncrypted()));
        body.put("profileImageUrl", user.getProfileImageUrl());
        return body;
    }

    private static String normalizeUsername(String username) {
        String normalized = value(username).trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private static String value(String value) { return value == null ? "" : value; }

    private String maskEmail(String encryptedEmail) {
        if (encryptedEmail == null) return null;
        String email = emailCrypto.decrypt(encryptedEmail);
        int at = email.indexOf('@');
        return at < 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }

    record LoginRequest(String username, String password) {}
    record SignupRequest(String username, String email, String password, String passwordConfirm, String nickname) {}
    record DeleteRequest(String password) {}
    record ExternalDeleteRequest(String username, String password) {}
}

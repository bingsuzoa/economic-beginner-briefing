package com.economicbriefing.auth.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.economicbriefing.auth.entity.SmsVerificationCode;
import com.economicbriefing.auth.repository.SmsVerificationCodeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class SmsAuthService {

    private static final int CODE_EXPIRY_SECONDS = 180;
    private static final int MAX_ATTEMPTS = 5;
    private static final int RESEND_THROTTLE_SECONDS = 60;
    private static final int DAILY_PHONE_LIMIT = 10;
    private static final int DAILY_IP_LIMIT = 20;

    private final SmsVerificationCodeRepository codeRepository;
    private final SmsService smsService;
    private final PasswordEncoder passwordEncoder;
    private final Random random = new Random();

    // ponytail: in-memory IP rate limit, resets daily on first access. Use Redis if multi-instance.
    private final ConcurrentHashMap<String, AtomicInteger> ipDailyCounts = new ConcurrentHashMap<>();
    private volatile LocalDate ipCountDate = LocalDate.now();

    public SmsAuthService(SmsVerificationCodeRepository codeRepository,
                          SmsService smsService,
                          PasswordEncoder passwordEncoder) {
        this.codeRepository = codeRepository;
        this.smsService = smsService;
        this.passwordEncoder = passwordEncoder;
    }

    public String sendCode(String phone, String clientIp) {
        phone = normalizePhone(phone);

        // 1. Resend throttle (60s)
        codeRepository.findFirstByPhoneAndVerifiedFalseOrderByCreatedAtDesc(phone)
                .ifPresent(existing -> {
                    if (existing.getCreatedAt().plusSeconds(RESEND_THROTTLE_SECONDS).isAfter(OffsetDateTime.now())) {
                        throw new TooManyRequestsException("재전송은 " + RESEND_THROTTLE_SECONDS + "초 후에 가능합니다.");
                    }
                });

        // 2. Daily per-phone limit
        OffsetDateTime startOfDay = LocalDate.now().atStartOfDay(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
        long dailyPhoneCount = codeRepository.countByPhoneAndCreatedAtAfter(phone, startOfDay);
        if (dailyPhoneCount >= DAILY_PHONE_LIMIT) {
            throw new TooManyRequestsException("일일 인증 요청 한도를 초과했습니다. 내일 다시 시도해주세요.");
        }

        // 3. Daily per-IP limit
        if (clientIp != null) {
            resetIpCountsIfNewDay();
            int ipCount = ipDailyCounts.computeIfAbsent(clientIp, k -> new AtomicInteger(0)).incrementAndGet();
            if (ipCount > DAILY_IP_LIMIT) {
                throw new TooManyRequestsException("일일 인증 요청 한도를 초과했습니다. 내일 다시 시도해주세요.");
            }
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        String hash = passwordEncoder.encode(code);

        codeRepository.save(new SmsVerificationCode(
                phone, hash, OffsetDateTime.now().plusSeconds(CODE_EXPIRY_SECONDS)));

        smsService.sendCode(phone, code);
        return code;
    }

    public boolean verifyCode(String phone, String code) {
        phone = normalizePhone(phone);

        SmsVerificationCode record = codeRepository
                .findFirstByPhoneAndVerifiedFalseOrderByCreatedAtDesc(phone)
                .orElseThrow(() -> new IllegalArgumentException("인증 요청을 찾을 수 없습니다."));

        if (record.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("인증번호가 만료되었습니다. 다시 요청해주세요.");
        }

        if (record.getAttempts() >= MAX_ATTEMPTS) {
            throw new IllegalArgumentException("시도 횟수를 초과했습니다. 다시 요청해주세요.");
        }

        record.incrementAttempts();

        if (!passwordEncoder.matches(code, record.getCodeHash())) {
            codeRepository.save(record);
            throw new IllegalArgumentException("인증번호가 일치하지 않습니다.");
        }

        record.markVerified();
        codeRepository.save(record);
        return true;
    }

    public String normalizePhone(String phone) {
        return phone.replaceAll("[\\s-]", "");
    }

    private void resetIpCountsIfNewDay() {
        LocalDate today = LocalDate.now();
        if (!today.equals(ipCountDate)) {
            ipDailyCounts.clear();
            ipCountDate = today;
        }
    }

    public static class TooManyRequestsException extends RuntimeException {
        public TooManyRequestsException(String message) { super(message); }
    }
}

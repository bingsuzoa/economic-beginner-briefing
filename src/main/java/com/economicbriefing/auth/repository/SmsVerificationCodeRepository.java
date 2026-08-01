package com.economicbriefing.auth.repository;

import java.time.OffsetDateTime;
import java.util.Optional;

import com.economicbriefing.auth.entity.SmsVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmsVerificationCodeRepository extends JpaRepository<SmsVerificationCode, Long> {
    Optional<SmsVerificationCode> findFirstByPhoneAndVerifiedFalseOrderByCreatedAtDesc(String phone);
    long countByPhoneAndCreatedAtAfter(String phone, OffsetDateTime after);
}

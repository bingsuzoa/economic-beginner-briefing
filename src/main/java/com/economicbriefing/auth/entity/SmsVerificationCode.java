package com.economicbriefing.auth.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sms_verification_codes")
public class SmsVerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "code_hash", nullable = false, length = 256)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SmsVerificationCode() {}

    public SmsVerificationCode(String phone, String codeHash, OffsetDateTime expiresAt) {
        this.phone = phone;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.attempts = 0;
        this.verified = false;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getPhone() { return phone; }
    public String getCodeHash() { return codeHash; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public int getAttempts() { return attempts; }
    public boolean isVerified() { return verified; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void incrementAttempts() { this.attempts++; }
    public void markVerified() { this.verified = true; }
}

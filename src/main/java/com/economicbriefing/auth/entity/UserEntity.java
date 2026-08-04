package com.economicbriefing.auth.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id private String id;
    @Column(length = 30) private String username;
    @Column(name = "email_encrypted", columnDefinition = "TEXT") private String emailEncrypted;
    @Column(name = "email_hash", length = 64) private String emailHash;
    @Column(name = "password_hash", length = 256) private String passwordHash;
    @Column(length = 50) private String nickname;
    @Column(name = "profile_image_url", columnDefinition = "TEXT") private String profileImageUrl;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "last_login_at") private OffsetDateTime lastLoginAt;

    protected UserEntity() {}

    public UserEntity(String id, String username, String emailEncrypted, String emailHash,
                      String passwordHash, String nickname) {
        this.id = id;
        this.username = username;
        this.emailEncrypted = emailEncrypted;
        this.emailHash = emailHash;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.createdAt = OffsetDateTime.now();
        this.lastLoginAt = this.createdAt;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getEmailEncrypted() { return emailEncrypted; }
    public String getEmailHash() { return emailHash; }
    public String getPasswordHash() { return passwordHash; }
    public String getNickname() { return nickname; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getLastLoginAt() { return lastLoginAt; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
    public void setLastLoginAt(OffsetDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}

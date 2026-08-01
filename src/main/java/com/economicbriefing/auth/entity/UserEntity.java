package com.economicbriefing.auth.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private String id;

    @Column(nullable = false, length = 20, unique = true)
    private String phone;

    @Column(length = 256)
    private String name;

    @Column(length = 50)
    private String nickname;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(length = 10)
    private String gender;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified;

    @Column(name = "profile_image_url", columnDefinition = "TEXT")
    private String profileImageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    protected UserEntity() {}

    public UserEntity(String id, String phone, String name, String nickname,
                      LocalDate birthDate, String gender) {
        this.id = id;
        this.phone = phone;
        this.name = name;
        this.nickname = nickname;
        this.birthDate = birthDate;
        this.gender = gender;
        this.phoneVerified = true;
        this.createdAt = OffsetDateTime.now();
        this.lastLoginAt = this.createdAt;
    }

    public String getId() { return id; }
    public String getPhone() { return phone; }
    public String getName() { return name; }
    public String getNickname() { return nickname; }
    public LocalDate getBirthDate() { return birthDate; }
    public String getGender() { return gender; }
    public boolean isPhoneVerified() { return phoneVerified; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getLastLoginAt() { return lastLoginAt; }

    public void setName(String name) { this.name = name; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
    public void setLastLoginAt(OffsetDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}

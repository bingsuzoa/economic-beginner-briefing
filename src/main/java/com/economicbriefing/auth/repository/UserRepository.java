package com.economicbriefing.auth.repository;

import java.util.Optional;

import com.economicbriefing.auth.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, String> {
    Optional<UserEntity> findByPhone(String phone);
    boolean existsByPhone(String phone);
    boolean existsByNickname(String nickname);
}

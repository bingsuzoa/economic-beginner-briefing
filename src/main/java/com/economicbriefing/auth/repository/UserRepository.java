package com.economicbriefing.auth.repository;

import java.util.Optional;

import com.economicbriefing.auth.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, String> {
    Optional<UserEntity> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmailHash(String emailHash);
    boolean existsByNickname(String nickname);
}

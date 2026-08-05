package com.economicbriefing.auth.service;

import com.economicbriefing.auth.entity.UserEntity;
import com.economicbriefing.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDeletionService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountDeletionService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public boolean deleteAuthenticated(String userId, String password) {
        return userRepository.findById(userId).map(user -> delete(user, password)).orElse(false);
    }

    @Transactional
    public boolean deleteWithCredentials(String username, String password) {
        return userRepository.findByUsername(username).map(user -> delete(user, password)).orElse(false);
    }

    private boolean delete(UserEntity user, String password) {
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) return false;
        userRepository.delete(user);
        userRepository.flush();
        return true;
    }
}

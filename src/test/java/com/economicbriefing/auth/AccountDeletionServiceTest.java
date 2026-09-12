package com.economicbriefing.auth;

import java.util.Optional;

import com.economicbriefing.auth.entity.UserEntity;
import com.economicbriefing.auth.repository.UserRepository;
import com.economicbriefing.auth.service.AccountDeletionService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountDeletionServiceTest {
    @Test
    void deletesOnlyAfterPasswordVerification() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        UserEntity user = new UserEntity("user-pk", "tester", "encrypted", "hash", "password-hash", "토리");
        when(users.findById("user-pk")).thenReturn(Optional.of(user));
        when(passwords.matches("correct-password", "password-hash")).thenReturn(true);
        AccountDeletionService service = new AccountDeletionService(users, passwords);

        assertTrue(service.deleteAuthenticated("user-pk", "correct-password"));
        verify(users).delete(user);
        verify(users).flush();
    }

    @Test
    void keepsAccountWhenPasswordIsWrong() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        UserEntity user = new UserEntity("user-pk", "tester", "encrypted", "hash", "password-hash", "토리");
        when(users.findByUsername("tester")).thenReturn(Optional.of(user));
        AccountDeletionService service = new AccountDeletionService(users, passwords);

        assertFalse(service.deleteWithCredentials("tester", "wrong-password"));
        verify(users, never()).delete(any());
    }
}

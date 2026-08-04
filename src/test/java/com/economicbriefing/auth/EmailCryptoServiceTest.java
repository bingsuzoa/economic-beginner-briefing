package com.economicbriefing.auth;

import com.economicbriefing.auth.service.EmailCryptoService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailCryptoServiceTest {
    @Test
    void encryptsReversiblyAndHashesNormalizedEmailDeterministically() {
        EmailCryptoService crypto = new EmailCryptoService(
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");
        String email = crypto.normalize(" User@Example.COM ");
        String encrypted = crypto.encrypt(email);

        assertEquals("user@example.com", email);
        assertNotEquals(email, encrypted);
        assertEquals(email, crypto.decrypt(encrypted));
        assertEquals(crypto.hash(email), crypto.hash("user@example.com"));
    }
}

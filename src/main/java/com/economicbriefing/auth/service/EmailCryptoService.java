package com.economicbriefing.auth.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailCryptoService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] encryptionKey;
    private final byte[] hashKey;

    public EmailCryptoService(@Value("${auth.email-encryption-key}") String encryptionKey,
                              @Value("${auth.email-hash-key}") String hashKey) {
        this.encryptionKey = decodeKey(encryptionKey, "AUTH_EMAIL_ENCRYPTION_KEY");
        this.hashKey = decodeKey(hashKey, "AUTH_EMAIL_HASH_KEY");
    }

    public String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public String encrypt(String email) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(email.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("이메일 암호화에 실패했습니다.", e);
        }
    }

    public String decrypt(String encryptedEmail) {
        try {
            ByteBuffer value = ByteBuffer.wrap(Base64.getDecoder().decode(encryptedEmail));
            byte[] iv = new byte[12];
            value.get(iv);
            byte[] encrypted = new byte[value.remaining()];
            value.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("이메일 복호화에 실패했습니다.", e);
        }
    }

    public String hash(String normalizedEmail) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalizedEmail.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("이메일 해시에 실패했습니다.", e);
        }
    }

    private static byte[] decodeKey(String value, String name) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (decoded.length != 32) throw new IllegalArgumentException();
            return decoded;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + "는 Base64로 인코딩된 32바이트 키여야 합니다.");
        }
    }
}

package com.economicbriefing.classifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArticlePersistenceServiceTest {

    @Test
    void contentHashShouldBeDeterministic() {
        String hash1 = ArticlePersistenceService.contentHash("제목", "본문");
        String hash2 = ArticlePersistenceService.contentHash("제목", "본문");

        assertEquals(hash1, hash2);
    }

    @Test
    void contentHashShouldDifferForDifferentInput() {
        String hash1 = ArticlePersistenceService.contentHash("제목A", "본문");
        String hash2 = ArticlePersistenceService.contentHash("제목B", "본문");

        assertNotEquals(hash1, hash2);
    }

    @Test
    void contentHashShouldHandleNulls() {
        assertDoesNotThrow(() -> ArticlePersistenceService.contentHash(null, null));
        assertDoesNotThrow(() -> ArticlePersistenceService.contentHash("제목", null));
        assertDoesNotThrow(() -> ArticlePersistenceService.contentHash(null, "본문"));
    }

    @Test
    void contentHashShouldBeSha256Hex() {
        String hash = ArticlePersistenceService.contentHash("test", "body");

        // SHA-256 produces 64 hex chars
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]+"));
    }
}

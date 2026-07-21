package com.economicbriefing.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryPipelineLockTest {

    private InMemoryPipelineLock lock;

    @BeforeEach
    void setUp() {
        lock = new InMemoryPipelineLock();
    }

    @Test
    void shouldAcquireLockWhenFree() {
        assertTrue(lock.acquire("run-1", "MANUAL"));
        assertTrue(lock.isLocked());
        assertEquals("run-1", lock.getCurrentRunId());
    }

    @Test
    void shouldDenySecondAcquisition() {
        assertTrue(lock.acquire("run-1", "MANUAL"));
        assertFalse(lock.acquire("run-2", "SCHEDULER"));
        assertEquals("run-1", lock.getCurrentRunId());
    }

    @Test
    void shouldReleaseLock() {
        lock.acquire("run-1", "MANUAL");
        lock.release("run-1");

        assertFalse(lock.isLocked());
        assertNull(lock.getCurrentRunId());
    }

    @Test
    void shouldAllowReacquisitionAfterRelease() {
        lock.acquire("run-1", "MANUAL");
        lock.release("run-1");

        assertTrue(lock.acquire("run-2", "SCHEDULER"));
        assertEquals("run-2", lock.getCurrentRunId());
    }

    @Test
    void shouldNotReleaseWithWrongRunId() {
        lock.acquire("run-1", "MANUAL");
        lock.release("run-2");

        assertTrue(lock.isLocked());
        assertEquals("run-1", lock.getCurrentRunId());
    }

    @Test
    void shouldReturnNullWhenNotLocked() {
        assertFalse(lock.isLocked());
        assertNull(lock.getCurrentRunId());
    }
}
